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
        try {
            analyze(db, files, bookId)
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Antes, a falha era engolida em silêncio e o livro ficava "Failed" para sempre, sem motivo.
            android.util.Log.e("VirgilAnalysis", "Falha ao analisar o livro $bookId", e)
            if (runAttemptCount < 2 && e !is OutOfMemoryError) {
                Result.retry()
            } else {
                fail(db, bookId, "erro interno (${e.javaClass.simpleName}: ${e.message?.take(160)})")
                Result.failure()
            }
        }
    }

    companion object {
        private const val KEY_BOOK = "book"
        private val json = Json { encodeDefaults = false }

        /** Registra a falha com a versão do analisador: uma versão nova tenta de novo automaticamente. */
        suspend fun fail(db: VirgilDatabase, bookId: Long, reason: String) =
            db.books().setAnalysisState(bookId, AnalysisState.Failed.name, "v${DifficultyAnalyzer.VERSION}: $reason")

        /**
         * Analisa o livro inteiro (todos os capítulos, sem amostragem): cada capítulo é medido e
         * pontuado; o livro é pontuado sobre a soma das métricas de todos os capítulos.
         */
        suspend fun analyze(db: VirgilDatabase, files: BookFiles, bookId: Long) {
            val book = db.books().get(bookId) ?: return
            val language = Language.fromTag(book.language)
            if (language == null) {
                fail(db, bookId, "não foi possível identificar o idioma do livro")
                return
            }
            db.books().setAnalysisState(bookId, AnalysisState.Running.name)
            val analyzer = DifficultyAnalyzer(language)
            val chapters = db.books().chapters(bookId)
            db.analysis().deleteChapters(bookId) // resultados de uma versão anterior não se misturam aos novos
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
                fail(db, bookId, if (total.words == 0) "nenhum texto legível foi extraído do livro" else "texto curto demais (${total.words} palavras)")
                return
            }
            val spread = DifficultyAnalyzer.chapterSpread(chapterScores)
            val range = analyzer.rangeFor(scores, total.words, spread)
            val findings = analyzer.findings(total, scores)
            val variant = VariantDetector.detect(language, sample.toString())
            db.books().setLanguage(bookId, book.language, variant)
            db.analysis().upsert(
                DifficultyAnalysisEntity(
                    bookId = bookId, levelLow = range.low.label, levelHigh = range.high.label, confidence = range.confidence,
                    metricsJson = json.encodeToString(StoredMetrics.serializer(), StoredMetrics.of(total, scores, analyzer.summary(scores), chapters.size, chapterScores.size)),
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
    val summary: String = "",
    val mtld: Double = 0.0,
    val above8kPercent: Double = 0.0,
    val passivePer1000: Double = 0.0,
    val conditionalPer1000: Double = 0.0,
    val idiomsPer1000: Double = 0.0,
    val similesPer1000: Double = 0.0,
    val chapters: Int = 0,
    val chaptersScored: Int = 0,
) {
    companion object {
        fun of(m: TextMetrics, s: DimensionScores, summary: String = "", chapters: Int = 0, scored: Int = 0) = StoredMetrics(
            words = m.words, sentences = m.sentences, meanSentenceLength = m.meanSentenceLength,
            vocabulary95 = m.vocabularyFor(0.95), vocabulary98 = m.vocabularyFor(0.98),
            literaryPer1000 = if (m.words == 0) 0.0 else m.literaryForms * 1000.0 / m.words,
            archaismsPer1000 = if (m.words == 0) 0.0 else m.archaisms * 1000.0 / m.words,
            dialoguePercent = if (m.totalChars == 0) 0 else m.dialogueChars * 100 / m.totalChars,
            scores = s,
            summary = summary,
            mtld = m.mtld,
            above8kPercent = m.fractionAbove(8_000) * 100,
            passivePer1000 = if (m.words == 0) 0.0 else m.passives * 1000.0 / m.words,
            conditionalPer1000 = if (m.words == 0) 0.0 else m.conditionals * 1000.0 / m.words,
            idiomsPer1000 = if (m.words == 0) 0.0 else m.idioms * 1000.0 / m.words,
            similesPer1000 = if (m.words == 0) 0.0 else m.similes * 1000.0 / m.words,
            chapters = chapters,
            chaptersScored = scored,
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
