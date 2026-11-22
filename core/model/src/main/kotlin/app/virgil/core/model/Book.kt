package app.virgil.core.model

enum class BookFormat { EPUB, TXT }

/** Estado do processamento em segundo plano de um livro importado. */
enum class AnalysisState { Pending, Running, Done, Failed }

data class Book(
    val id: Long,
    val title: String,
    val author: String?,
    val language: Language?,
    val languageVariant: String?,
    val format: BookFormat,
    val coverPath: String?,
    val chapterCount: Int,
    val totalChars: Long,
    val addedAt: Long,
    val lastOpenedAt: Long?,
    val progress: Float,
    val finishedAt: Long?,
    val difficulty: CefrRange?,
    val analysisState: AnalysisState,
)

/** Posição estável de leitura: capítulo + deslocamento de caractere no texto normalizado do capítulo. */
data class ReadingPosition(val chapterIndex: Int, val charOffset: Int)
