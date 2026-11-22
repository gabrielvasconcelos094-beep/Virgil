package app.virgil.engine.books.parse

import java.io.File

data class BookMeta(
    val title: String?,
    val author: String?,
    val language: String?,
    val publisher: String? = null,
    val description: String? = null,
    val date: String? = null,
)

data class ParsedChapter(
    val index: Int,
    val title: String?,
    val charCount: Int,
    val wordCount: Int,
    /** Offset do início do capítulo no texto do livro inteiro. */
    val startChar: Long,
)

data class TocItem(val title: String, val depth: Int, val chapterIndex: Int, val charOffset: Int)

data class ParsedBook(
    val meta: BookMeta,
    val chapters: List<ParsedChapter>,
    val toc: List<TocItem>,
    val cover: File?,
) {
    val totalChars: Long get() = chapters.sumOf { it.charCount.toLong() }
}
