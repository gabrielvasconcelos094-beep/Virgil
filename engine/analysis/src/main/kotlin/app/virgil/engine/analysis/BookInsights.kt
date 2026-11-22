package app.virgil.engine.analysis

import app.virgil.core.database.VirgilDatabase
import app.virgil.core.model.AnalysisState
import app.virgil.core.model.CefrLevel
import app.virgil.core.model.CefrRange
import app.virgil.core.model.Language
import app.virgil.core.model.ReadingFit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

data class ChapterLevel(val index: Int, val title: String?, val range: CefrRange?)

data class BookDetails(
    val id: Long,
    val title: String,
    val author: String?,
    val language: Language?,
    val variant: String?,
    val publisher: String?,
    val publishedDate: String?,
    val description: String?,
    val chapterCount: Int,
    val words: Int,
    val state: AnalysisState,
    val range: CefrRange?,
    val findings: List<String>,
    val metrics: StoredMetrics?,
    val chapters: List<ChapterLevel>,
    val readerLevel: CefrLevel?,
    val fit: ReadingFit?,
    val progress: Float,
)

/** Junta livro, análise, capítulos e o nível declarado pelo leitor para a tela de análise. */
class BookInsights(private val db: VirgilDatabase) {
    private val json = Json { ignoreUnknownKeys = true }

    fun details(bookId: Long): Flow<BookDetails?> = combine(
        db.books().observe(bookId),
        db.analysis().observe(bookId),
        db.analysis().observeChapters(bookId),
        db.profiles().observeAll(),
    ) { book, analysis, chapterRows, profiles ->
        book ?: return@combine null
        val language = Language.fromTag(book.language)
        val range = analysis?.let { a -> range(a.levelLow, a.levelHigh, a.confidence) }
        val readerLevel = profiles.firstOrNull { it.language == language?.tag }?.let { CefrLevel.parse(it.level) }
        val titles = db.books().chapters(bookId).associate { it.chapterIndex to it.title }
        val words = db.books().chapters(bookId).sumOf { it.wordCount }
        BookDetails(
            id = book.id, title = book.title, author = book.author, language = language, variant = book.languageVariant,
            publisher = book.publisher, publishedDate = book.publishedDate, description = book.description,
            chapterCount = book.chapterCount, words = words,
            state = runCatching { AnalysisState.valueOf(book.analysisState) }.getOrDefault(AnalysisState.Pending),
            range = range,
            findings = analysis?.let { runCatching { json.decodeFromString(ListSerializer(String.serializer()), it.findingsJson) }.getOrNull() }.orEmpty(),
            metrics = analysis?.let { runCatching { json.decodeFromString(StoredMetrics.serializer(), it.metricsJson) }.getOrNull() },
            chapters = (0 until book.chapterCount).map { i ->
                val row = chapterRows.firstOrNull { it.chapterIndex == i }
                ChapterLevel(i, titles[i], row?.let { range(it.levelLow, it.levelHigh, 0f) })
            },
            readerLevel = readerLevel,
            fit = if (readerLevel != null && range != null) ReadingFit.of(readerLevel, range) else null,
            progress = book.progress,
        )
    }

    private fun range(low: String, high: String, confidence: Float): CefrRange? {
        val l = CefrLevel.parse(low) ?: return null
        val h = CefrLevel.parse(high) ?: return null
        return if (l <= h) CefrRange(l, h, confidence) else null
    }
}
