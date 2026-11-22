package app.virgil.engine.analysis

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.virgil.core.database.VirgilDatabase
import app.virgil.core.database.entity.ChapterDifficultyEntity
import app.virgil.core.database.entity.DifficultyAnalysisEntity
import app.virgil.core.model.AnalysisState
import app.virgil.core.model.Language
import app.virgil.engine.books.BookFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Analisa a dificuldade de um livro em segundo plano, capítulo por capítulo (ADR-0004).
 * Os resultados de cada capítulo são gravados assim que ficam prontos; o livro pode ser lido
 * durante a análise.
 */
class AnalysisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val bookId = inputData.getLong(KEY_BOOK, -1)
        val db = VirgilDatabase.getInstance(applicationContext)
        val files = BookFiles(applicationContext)
        runCatching { analyze(db, files, bookId) }
            .fold({ Result.success() }, {
                db.books().setAnalysisState(bookId, AnalysisState.Failed.name)
                Result.failure()
            })
    }

    companion object {
        private const val KEY_BOOK = "book"
        private val json = Json { encodeDefaults = false }

        suspend fun analyze(db: VirgilDatabase, files: BookFiles, bookId: Long) {
            val book = db.books().get(bookId) ?: return
            val language = Language.fromTag(book.language)
            if (language == null) {
                db.books().setAnalysisState(bookId, AnalysisState.Failed.name)
                return
            }
            db.books().setAnalysisState(bookId, AnalysisState.Running.name)
            val analyzer = DifficultyAnalyzer(language)
            val chapters = db.books().chapters(bookId)
            var total = TextMetrics()
            val chapterScores = ArrayList<Double>()
            val sample = StringBuilder()
            for (ch in chapters) {
                val doc = files.readChapter(book.storageDir, ch.contentPath)
                val texts = doc.blocks.map { it.text }.filter { it.isNotBlank() }
                if (sample.length < 60_000) texts.forEach { if (sample.length < 60_000) sample.append(it).append('\n') }
                val m = analyzer.measure(texts)
                total += m
                val scores = analyzer.score(m) ?: continue
                chapterScores += scores.combined
                val range = analyzer.rangeFor(scores, m.words)
                db.analysis().upsertChapter(
                    ChapterDifficultyEntity(bookId, ch.chapterIndex, range.low.label, range.high.label, scores.combined.toFloat(), json.encodeToString(DimensionScores.serializer(), scores)),
                )
            }
            val scores = analyzer.score(total)
            if (scores == null) {
                db.books().setAnalysisState(bookId, AnalysisState.Failed.name)
                return
            }
            val spread = if (chapterScores.size < 2) 0.0 else chapterScores.average().let { mu -> kotlin.math.sqrt(chapterScores.sumOf { (it - mu) * (it - mu) } / chapterScores.size) }
            val range = analyzer.rangeFor(scores, total.words, spread)
            val findings = analyzer.findings(total, scores)
            val variant = VariantDetector.detect(language, sample.toString())
            db.books().setLanguage(bookId, book.language, variant)
            db.analysis().upsert(
                DifficultyAnalysisEntity(
                    bookId = bookId, levelLow = range.low.label, levelHigh = range.high.label, confidence = range.confidence,
                    metricsJson = json.encodeToString(StoredMetrics.serializer(), StoredMetrics.of(total, scores)),
                    findingsJson = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(String.serializer()), findings),
                    analyzerVersion = DifficultyAnalyzer.VERSION, analyzedAt = System.currentTimeMillis(),
                ),
            )
            db.books().setAnalysisState(bookId, AnalysisState.Done.name)
        }

        fun enqueue(context: Context, bookId: Long) {
            val req = OneTimeWorkRequestBuilder<AnalysisWorker>().setInputData(workDataOf(KEY_BOOK to bookId)).addTag("virgil-analysis").build()
            WorkManager.getInstance(context).enqueueUniqueWork("analysis-$bookId", ExistingWorkPolicy.KEEP, req)
        }
    }
}

/** Resumo das métricas guardado no banco (as listas grandes do histograma não são necessárias). */
@kotlinx.serialization.Serializable
data class StoredMetrics(
    val words: Int,
    val sentences: Int,
    val meanSentenceLength: Double,
    val vocabulary95: Int,
    val vocabulary98: Int,
    val literaryPer1000: Double,
    val archaismsPer1000: Double,
    val dialoguePercent: Int,
    val scores: DimensionScores,
) {
    companion object {
        fun of(m: TextMetrics, s: DimensionScores) = StoredMetrics(
            words = m.words, sentences = m.sentences, meanSentenceLength = m.meanSentenceLength,
            vocabulary95 = m.vocabularyFor(0.95), vocabulary98 = m.vocabularyFor(0.98),
            literaryPer1000 = if (m.words == 0) 0.0 else m.literaryForms * 1000.0 / m.words,
            archaismsPer1000 = if (m.words == 0) 0.0 else m.archaisms * 1000.0 / m.words,
            dialoguePercent = if (m.totalChars == 0) 0 else m.dialogueChars * 100 / m.totalChars,
            scores = s,
        )
    }
}

/** Observa os livros que precisam de análise e agenda o trabalho (chamado na inicialização do app). */
class AnalysisScheduler(private val context: Context, private val db: VirgilDatabase, private val scope: CoroutineScope) {
    fun start() {
        scope.launch {
            db.books().observeNeedingAnalysis(DifficultyAnalyzer.VERSION).distinctUntilChanged().collect { ids ->
                ids.forEach { AnalysisWorker.enqueue(context, it) }
            }
        }
    }
}
