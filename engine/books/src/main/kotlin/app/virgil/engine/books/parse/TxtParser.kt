package app.virgil.engine.books.parse

import app.virgil.core.text.TextNormalizer
import app.virgil.engine.books.model.Block
import app.virgil.engine.books.model.BlockType
import app.virgil.engine.books.model.ChapterDoc
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Importação de texto simples.
 *
 *  - Codificação: BOM (UTF-8/UTF-16), depois UTF-8 estrito; se inválido, Windows-1252
 *    (superset do Latin-1, comum em textos antigos do Project Gutenberg).
 *  - Remove o cabeçalho e a licença do Project Gutenberg, aproveitando Title/Author.
 *  - Parágrafos separados por linha em branco; linhas quebradas "a 70 colunas" são unidas,
 *    poemas (linhas curtas) são preservados.
 *  - Capítulos detectados por títulos ("CHAPTER IV", "Chapitre 3", "Kapitel 2", "Capítulo I",
 *    "II." sozinho na linha). Sem títulos confiáveis, divide em partes de ~6 000 palavras.
 */
class TxtParser(private val maxBytes: Long = 64L * 1024 * 1024) {

    fun parse(file: File, fallbackTitle: String?, sink: ChapterSink): ParsedBook {
        if (file.length() > maxBytes) throw ParseException("Arquivo de texto grande demais (máximo de 64 MB).")
        val raw = decode(file.readBytes())
        val (body, gutenbergMeta) = stripGutenberg(raw.replace("\r\n", "\n").replace('\r', '\n'))
        val paragraphs = paragraphs(body)
        if (paragraphs.isEmpty()) throw ParseException("O arquivo de texto está vazio.")

        val chapters = splitChapters(paragraphs)
        val parsed = ArrayList<ParsedChapter>()
        var startChar = 0L
        chapters.forEachIndexed { i, (title, paras) ->
            val blocks = ArrayList<Block>()
            var offset = 0
            fun add(type: BlockType, text: String, level: Int = 0) {
                blocks += Block(type, offset, text, level = level)
                offset += text.length + 1
            }
            if (title != null) add(BlockType.Heading, title, 2)
            paras.forEach { p -> add(if (p.contains('\n')) BlockType.Verse else BlockType.Paragraph, p) }
            val doc = ChapterDoc(i, title, blocks)
            sink.write(doc)
            parsed += ParsedChapter(i, title ?: "Parte ${i + 1}", doc.charCount, paras.sumOf { WORD.findAll(it).count() }, startChar)
            startChar += doc.charCount
        }
        val meta = BookMeta(
            title = gutenbergMeta["title"] ?: fallbackTitle,
            author = gutenbergMeta["author"],
            language = gutenbergMeta["language"],
        )
        return ParsedBook(meta, parsed, parsed.map { TocItem(it.title ?: "Parte ${it.index + 1}", 0, it.index, 0) }, null)
    }

    internal fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
    }

    internal fun stripGutenberg(text: String): Pair<String, Map<String, String>> {
        val start = START.find(text)
        val end = END.find(text)
        if (start == null) return text to emptyMap()
        val header = text.substring(0, start.range.first)
        val meta = buildMap {
            Regex("(?m)^Title:\\s*(.+)$").find(header)?.let { put("title", it.groupValues[1].trim()) }
            Regex("(?m)^Author:\\s*(.+)$").find(header)?.let { put("author", it.groupValues[1].trim()) }
            Regex("(?m)^Language:\\s*(.+)$").find(header)?.let { put("language", it.groupValues[1].trim()) }
        }
        val bodyEnd = if (end != null && end.range.first > start.range.last) end.range.first else text.length
        return text.substring(start.range.last + 1, bodyEnd) to meta
    }

    internal fun paragraphs(body: String): List<String> =
        body.split(Regex("\\n[ \\t]*\\n+"))
            .map { block -> block.lines().map { TextNormalizer.clean(it).trim() }.filter { it.isNotEmpty() } }
            .filter { it.isNotEmpty() }
            .map { lines ->
                // Prosa quebrada em colunas: linhas longas e parecidas → junta. Verso: linhas curtas → preserva.
                val prose = lines.size == 1 || lines.dropLast(1).count { it.length >= 55 } >= (lines.size - 1) * 0.7
                if (prose) joinWrapped(lines) else lines.joinToString("\n")
            }

    private fun joinWrapped(lines: List<String>): String {
        val sb = StringBuilder()
        for (l in lines) {
            if (sb.isEmpty()) { sb.append(l); continue }
            // Palavra hifenizada na quebra de linha ("some-\nthing") é reunida.
            if (sb.endsWith('-') && sb.length >= 2 && sb[sb.length - 2].isLetter() && l.firstOrNull()?.isLowerCase() == true) {
                sb.setLength(sb.length - 1); sb.append(l)
            } else {
                sb.append(' ').append(l)
            }
        }
        return sb.toString()
    }

    internal fun splitChapters(paragraphs: List<String>): List<Pair<String?, List<String>>> {
        val headingIdx = paragraphs.indices.filter { isHeading(paragraphs[it]) }
        val words = paragraphs.sumOf { WORD.findAll(it).count() }
        val reliable = headingIdx.size >= 2 && words / (headingIdx.size + 1) >= 150
        if (reliable) {
            val out = ArrayList<Pair<String?, List<String>>>()
            if (headingIdx.first() > 0) {
                val front = paragraphs.subList(0, headingIdx.first())
                if (front.sumOf { WORD.findAll(it).count() } >= 30) out += null to front
            }
            headingIdx.forEachIndexed { k, idx ->
                val end = headingIdx.getOrNull(k + 1) ?: paragraphs.size
                var title = paragraphs[idx].replace('\n', ' ')
                var contentStart = idx + 1
                // "CHAPTER I" seguido de um subtítulo curto ("The Old Sea-dog") vira um título só.
                val next = paragraphs.getOrNull(idx + 1)
                if (next != null && idx + 1 < end && next.length < 60 && !next.trimEnd().endsWith('.') && !next.contains('\n') && WORD.findAll(next).count() <= 8) {
                    title = "$title — $next"; contentStart++
                }
                out += title to paragraphs.subList(contentStart.coerceAtMost(end), end)
            }
            return out.filter { it.second.isNotEmpty() || it.first != null }
        }
        // Sem capítulos: partes de ~6000 palavras, cortando em fim de parágrafo.
        val parts = ArrayList<Pair<String?, List<String>>>()
        var current = ArrayList<String>()
        var count = 0
        for (p in paragraphs) {
            current += p
            count += WORD.findAll(p).count()
            if (count >= 6_000) { parts += null to current; current = ArrayList(); count = 0 }
        }
        if (current.isNotEmpty()) parts += null to current
        return parts
    }

    private fun isHeading(p: String): Boolean {
        if (p.length > 80 || p.contains('\n')) return false
        val t = p.trim()
        return HEADING.matches(t) || ROMAN_ALONE.matches(t)
    }

    companion object {
        private val WORD = Regex("\\p{L}+")
        private val START = Regex("\\*\\*\\*\\s*START OF (THE|THIS) PROJECT GUTENBERG[^\\n]*", RegexOption.IGNORE_CASE)
        private val END = Regex("\\*\\*\\*\\s*END OF (THE|THIS) PROJECT GUTENBERG", RegexOption.IGNORE_CASE)
        private const val NUM = "([0-9]{1,3}|[IVXLCDM]{1,7}|[a-zà-üA-ZÀ-Ü]{3,12})"
        private val HEADING = Regex(
            "^(CHAPTER|Chapter|CHAP\\.|Chap\\.|BOOK|Book|PART|Part|CHAPITRE|Chapitre|LIVRE|Livre|PARTIE|Partie|" +
                "KAPITEL|Kapitel|BUCH|Buch|TEIL|Teil|CAPITOLO|Capitolo|PARTE|Parte|LIBRO|Libro|CAPÍTULO|Capítulo|CAPITULO|Capitulo)" +
                "\\s+$NUM\\.?([\\s.:—–-].{0,60})?$",
        )
        private val ROMAN_ALONE = Regex("^([IVXLCDM]{1,7}|[0-9]{1,3})\\.?$")
    }
}
