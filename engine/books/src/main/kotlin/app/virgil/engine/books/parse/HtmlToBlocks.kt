package app.virgil.engine.books.parse

import app.virgil.core.text.TextNormalizer
import app.virgil.engine.books.model.Block
import app.virgil.engine.books.model.BlockType
import app.virgil.engine.books.model.Span
import app.virgil.engine.books.model.SpanStyle
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Converte o corpo XHTML de um capítulo em blocos do Virgil.
 *
 * Mantém a estrutura que importa para leitura literária (parágrafos, títulos, citações, versos,
 * listas, imagens, ênfases, notas) e descarta apresentação (CSS, floats, tabelas de layout).
 * [resolveImage] recebe o atributo src/href de uma imagem e devolve o caminho do arquivo extraído
 * (relativo ao livro) ou null se a imagem não puder ser usada.
 */
class HtmlToBlocks(private val resolveImage: (String) -> String?) {

    data class Result(val blocks: List<Block>, val anchors: Map<String, Int>, val firstHeading: String?)

    private val blocks = ArrayList<Block>()
    private val anchors = LinkedHashMap<String, Int>()
    private var offset = 0
    private var firstHeading: String? = null

    // Parágrafo em construção
    private val buf = StringBuilder()
    private val spans = ArrayList<Span>()
    private var pendingAnchors = ArrayList<String>()

    private data class Ctx(val type: BlockType, val level: Int, val preserveWhitespace: Boolean, val verse: Boolean)
    private val ctxStack = ArrayDeque<Ctx>().apply { addLast(Ctx(BlockType.Paragraph, 0, false, false)) }

    fun convert(body: Element): Result {
        walk(body)
        flush()
        return Result(blocks, anchors, firstHeading)
    }

    private fun walk(node: Node) {
        when (node) {
            is TextNode -> appendText(node.wholeText)
            is Element -> element(node)
        }
    }

    private fun element(e: Element) {
        val tag = e.normalName().substringAfter(':')
        if (tag in SKIP) return
        e.id().takeIf { it.isNotEmpty() }?.let { pendingAnchors += it }
        // epub:type de notas/rodapés fica, mas sem gerar quebras estranhas.
        when {
            tag == "br" -> { if (buf.isNotEmpty()) buf.append('\n') }
            tag == "hr" -> { flush(); emit(Block(BlockType.Separator, offset)) }
            tag == "img" -> image(e.attr("src"), e.attr("alt"))
            tag == "image" -> image(e.attr("xlink:href").ifEmpty { e.attr("href") }, null) // <svg><image>
            tag in HEADINGS -> block(e, BlockType.Heading, tag.drop(1).toInt())
            tag == "blockquote" -> block(e, BlockType.Quote, currentQuoteDepth() + 1)
            tag == "li" -> block(e, BlockType.ListItem, listDepth(e))
            tag == "pre" -> block(e, BlockType.Preformatted, 0, preserve = true)
            tag == "td" || tag == "th" -> { if (buf.isNotBlank()) buf.append(" · "); children(e) }
            tag == "tr" -> { block(e, BlockType.Paragraph, 0); }
            tag in BLOCKS -> block(e, inheritedType(), currentCtx().level, verse = isVerseContainer(e) || currentCtx().verse)
            else -> inline(e, tag)
        }
    }

    private fun block(e: Element, type: BlockType, level: Int, preserve: Boolean = false, verse: Boolean = false) {
        flush()
        val parentVerse = currentCtx().verse
        ctxStack.addLast(Ctx(type, level, preserve || currentCtx().preserveWhitespace, verse || parentVerse || isVerseContainer(e)))
        children(e)
        flush()
        ctxStack.removeLast()
    }

    private fun inline(e: Element, tag: String) {
        val style = when {
            tag in ITALIC -> SpanStyle.Italic
            tag in BOLD -> SpanStyle.Bold
            tag == "sup" -> SpanStyle.Superscript
            tag == "sub" -> SpanStyle.Subscript
            tag == "a" && e.hasAttr("href") -> SpanStyle.Link
            e.className().contains("smallcap", ignoreCase = true) || e.attr("style").contains("small-caps") -> SpanStyle.SmallCaps
            e.className().split(' ').any { it.equals("italic", true) || it.equals("i", true) || it.equals("emph", true) } -> SpanStyle.Italic
            else -> null
        }
        val start = buf.length
        children(e)
        if (style != null && buf.length > start) {
            spans += Span(start, buf.length, style, if (style == SpanStyle.Link) e.attr("href") else null)
        }
    }

    private fun children(e: Element) = e.childNodes().forEach { walk(it) }

    private fun appendText(raw: String) {
        if (currentCtx().preserveWhitespace) {
            buf.append(TextNormalizer.clean(raw.replace("\r", "")))
            return
        }
        // Colapsa espaços como o HTML: quebras de linha do código-fonte viram espaço.
        val collapsed = TextNormalizer.clean(raw.replace('\n', ' ').replace('\r', ' '))
        if (collapsed.isEmpty()) return
        val text = if (collapsed.startsWith(' ') && (buf.isEmpty() || buf.last() == ' ' || buf.last() == '\n')) collapsed.trimStart() else collapsed
        buf.append(text)
    }

    private fun image(src: String, alt: String?) {
        if (src.isBlank()) return
        val path = resolveImage(src) ?: return
        flush()
        emit(Block(BlockType.Image, offset, image = path, alt = alt?.takeIf { it.isNotBlank() }))
    }

    /** Fecha o parágrafo corrente em um bloco, ajustando espaços e trechos. */
    private fun flush() {
        if (buf.isEmpty()) {
            if (pendingAnchors.isNotEmpty()) { pendingAnchors.forEach { anchors.putIfAbsent(it, offset) }; pendingAnchors.clear() }
            return
        }
        val ctx = currentCtx()
        val raw = buf.toString()
        // Remove espaços nas bordas e em volta de quebras, ajustando os trechos.
        val (text, map) = trimWithMap(raw, ctx.preserveWhitespace)
        if (text.isEmpty()) { buf.setLength(0); spans.clear(); return }
        val adjusted = spans.mapNotNull { sp ->
            val s = map(sp.start)
            val e = map(sp.end)
            if (e > s) sp.copy(start = s, end = e) else null
        }
        val type = when {
            ctx.type == BlockType.Paragraph && ctx.verse -> BlockType.Verse
            ctx.type == BlockType.Paragraph && text.contains('\n') && looksLikeVerse(text) -> BlockType.Verse
            else -> ctx.type
        }
        pendingAnchors.forEach { anchors.putIfAbsent(it, offset) }
        pendingAnchors.clear()
        if (type == BlockType.Heading && firstHeading == null) firstHeading = text.replace('\n', ' ')
        emit(Block(type, offset, text, adjusted, ctx.level))
        buf.setLength(0)
        spans.clear()
    }

    private fun emit(b: Block) {
        blocks += b.copy(start = offset)
        offset += b.text.length + 1
    }

    private fun currentCtx() = ctxStack.last()
    private fun currentQuoteDepth() = ctxStack.count { it.type == BlockType.Quote }
    private fun inheritedType(): BlockType = when (currentCtx().type) {
        BlockType.Quote, BlockType.ListItem -> currentCtx().type
        else -> BlockType.Paragraph
    }

    private fun listDepth(e: Element): Int = e.parents().count { it.normalName() == "ul" || it.normalName() == "ol" }.coerceAtLeast(1)

    private fun isVerseContainer(e: Element): Boolean {
        val cls = (e.className() + " " + e.attr("epub:type")).lowercase()
        return VERSE_HINTS.any { cls.contains(it) }
    }

    private fun looksLikeVerse(text: String): Boolean {
        val lines = text.lines()
        return lines.size >= 2 && lines.count { it.length < 70 } >= lines.size * 0.8
    }

    companion object {
        private val SKIP = setOf("head", "script", "style", "noscript", "template", "rt", "rp", "title", "button", "input", "select")
        private val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
        private val BLOCKS = setOf(
            "p", "div", "section", "article", "header", "footer", "aside", "main", "figure", "figcaption", "body",
            "dl", "dt", "dd", "ul", "ol", "table", "tbody", "thead", "tfoot", "center", "address", "nav", "hgroup", "caption",
        )
        private val ITALIC = setOf("em", "i", "cite", "dfn", "var", "q")
        private val BOLD = setOf("strong", "b")
        private val VERSE_HINTS = listOf("poem", "verse", "stanza", "poetry", "linegroup", "lg", "vers", "strophe")

        /**
         * Remove espaços nas bordas do texto e de cada linha; devolve o texto e uma função que
         * converte offsets antigos nos novos (para reposicionar os trechos de estilo).
         */
        internal fun trimWithMap(raw: String, preserve: Boolean): Pair<String, (Int) -> Int> {
            val keep = BooleanArray(raw.length) { true }
            if (!preserve) {
                var i = 0
                while (i < raw.length && (raw[i] == ' ' || raw[i] == '\n')) { keep[i] = false; i++ }
                var j = raw.length - 1
                while (j >= 0 && (raw[j] == ' ' || raw[j] == '\n')) { keep[j] = false; j-- }
                for (k in raw.indices) {
                    if (raw[k] == ' ' && ((k + 1 < raw.length && raw[k + 1] == '\n') || (k > 0 && raw[k - 1] == '\n'))) keep[k] = false
                    if (raw[k] == ' ' && k > 0 && raw[k - 1] == ' ' && keep[k - 1]) keep[k] = false
                }
            } else {
                // Em texto pré-formatado só se remove a quebra inicial/final.
                if (raw.startsWith('\n')) keep[0] = false
                if (raw.endsWith('\n')) keep[raw.length - 1] = false
            }
            val prefix = IntArray(raw.length + 1)
            val sb = StringBuilder(raw.length)
            for (k in raw.indices) {
                prefix[k] = sb.length
                if (keep[k]) sb.append(raw[k])
            }
            prefix[raw.length] = sb.length
            return sb.toString() to { old: Int -> prefix[old.coerceIn(0, raw.length)] }
        }
    }
}
