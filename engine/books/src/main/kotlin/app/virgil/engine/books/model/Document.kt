package app.virgil.engine.books.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modelo de documento do Virgil (ADR-0003). Cada capítulo é uma lista de blocos com texto
 * normalizado e trechos de estilo. Offsets são índices de caractere no texto do capítulo,
 * formado pela concatenação dos textos dos blocos separados por um caractere — imagens e
 * separadores ocupam zero caracteres de texto mais o separador.
 */
@Serializable
data class ChapterDoc(
    val index: Int,
    val title: String? = null,
    val blocks: List<Block>,
    /** id de elemento HTML → offset no capítulo (para sumário com âncoras e notas). */
    val anchors: Map<String, Int> = emptyMap(),
) {
    val charCount: Int get() = blocks.lastOrNull()?.let { it.start + it.text.length } ?: 0

    /** Índice do bloco que contém o offset (ou o último antes dele). */
    fun blockIndexAt(offset: Int): Int {
        if (blocks.isEmpty()) return 0
        var lo = 0
        var hi = blocks.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (blocks[mid].start <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }
}

@Serializable
enum class BlockType {
    @SerialName("h") Heading,
    @SerialName("p") Paragraph,
    @SerialName("q") Quote,
    @SerialName("v") Verse,
    @SerialName("li") ListItem,
    @SerialName("pre") Preformatted,
    @SerialName("img") Image,
    @SerialName("hr") Separator,
}

@Serializable
data class Block(
    @SerialName("t") val type: BlockType,
    @SerialName("s") val start: Int,
    @SerialName("x") val text: String = "",
    @SerialName("sp") val spans: List<Span> = emptyList(),
    /** Nível do título (1–6), profundidade de lista ou de citação. */
    @SerialName("l") val level: Int = 0,
    /** Caminho da imagem relativo ao diretório do livro. */
    @SerialName("i") val image: String? = null,
    @SerialName("a") val alt: String? = null,
)

@Serializable
enum class SpanStyle {
    @SerialName("i") Italic,
    @SerialName("b") Bold,
    @SerialName("sup") Superscript,
    @SerialName("sub") Subscript,
    @SerialName("sc") SmallCaps,
    @SerialName("a") Link,
}

/** Trecho [start, end) relativo ao texto do bloco. */
@Serializable
data class Span(
    @SerialName("s") val start: Int,
    @SerialName("e") val end: Int,
    @SerialName("y") val style: SpanStyle,
    @SerialName("h") val href: String? = null,
)
