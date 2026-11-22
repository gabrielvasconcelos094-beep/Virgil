package app.virgil.engine.books.parse

import app.virgil.core.text.TextNormalizer
import app.virgil.engine.books.model.ChapterDoc
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Leitor de EPUB 2 e 3 (ADR-0003): container.xml → OPF (metadados, manifesto, spine) →
 * capítulos XHTML convertidos em [ChapterDoc] → sumário (NAV do EPUB 3 ou NCX do EPUB 2).
 *
 * Processa um capítulo por vez e grava cada um imediatamente via [sink], para que livros
 * grandes não fiquem inteiros na memória. Imagens referenciadas são extraídas para
 * [outDir]/images. Arquivos com DRM são recusados com uma mensagem clara.
 */
class EpubParser(
    private val maxEntryBytes: Long = 64L * 1024 * 1024,
    private val maxImages: Int = 2_000,
) {
    fun parse(epub: File, outDir: File, sink: ChapterSink, onProgress: (Float) -> Unit = {}): ParsedBook {
        ZipFile(epub).use { zip ->
            checkDrm(zip)
            val opfPath = rootfilePath(zip)
            val opfDir = opfPath.substringBeforeLast('/', "")
            val opf = Jsoup.parse(zip.readText(opfPath), "", Parser.xmlParser())

            val manifest = opf.allTags("item").associate { it.attr("id") to ManifestItem(it.attr("id"), resolve(opfDir, it.attr("href")), it.attr("media-type"), it.attr("properties")) }
            val spineEl = opf.allTags("spine").firstOrNull() ?: throw ParseException("O EPUB não tem spine (ordem de leitura).")
            val itemrefs = spineEl.children().filter { it.normalName().substringAfter(':') == "itemref" }
            val linear = itemrefs.filter { it.attr("linear") != "no" } + itemrefs.filter { it.attr("linear") == "no" }
            val spine = linear.mapNotNull { manifest[it.attr("idref")] }.filter { it.isHtml }
            if (spine.isEmpty()) throw ParseException("O EPUB não tem capítulos legíveis.")

            val meta = metadata(opf)
            val imageDir = File(outDir, "images").apply { mkdirs() }
            val extracted = HashMap<String, String?>()
            fun extractImage(path: String): String? = extracted.getOrPut(path) {
                if (extracted.size >= maxImages) return@getOrPut null
                val entry = zip.getEntry(path) ?: return@getOrPut null
                if (entry.size > 20L * 1024 * 1024) return@getOrPut null
                val ext = path.substringAfterLast('.', "img").lowercase().take(5)
                if (ext !in IMAGE_EXT) return@getOrPut null
                val name = "%04d.%s".format(extracted.size, ext)
                zip.getInputStream(entry).use { input -> File(imageDir, name).outputStream().use { input.copyTo(it) } }
                "images/$name"
            }

            val coverFile = coverPath(opf, manifest)?.let { p ->
                zip.getEntry(p)?.let { entry ->
                    val f = File(outDir, "cover." + p.substringAfterLast('.', "jpg").lowercase())
                    zip.getInputStream(entry).use { input -> f.outputStream().use { input.copyTo(it) } }
                    f
                }
            }

            // Capítulos
            val chapters = ArrayList<ParsedChapter>()
            val hrefToChapter = HashMap<String, Int>()
            val anchorsByHref = HashMap<String, Map<String, Int>>()
            var startChar = 0L
            spine.forEachIndexed { i, item ->
                onProgress(i.toFloat() / spine.size)
                val html = zip.readText(item.path)
                val doc = Jsoup.parse(html, "", Parser.htmlParser())
                val itemDir = item.path.substringBeforeLast('/', "")
                val conv = HtmlToBlocks { src -> extractImage(resolve(itemDir, src.substringBefore('#'))) }
                val result = conv.convert(doc.body())
                val isCoverPage = result.blocks.size <= 2 && result.blocks.all { it.text.isBlank() } &&
                    (i == 0 || item.properties.contains("cover"))
                if (result.blocks.isEmpty() || (isCoverPage && coverFile != null)) {
                    // Página vazia ou só com a capa: aponta para o próximo capítulo real.
                    hrefToChapter[item.path] = chapters.size
                    return@forEachIndexed
                }
                val index = chapters.size
                val chapter = ChapterDoc(index, result.firstHeading?.take(200), result.blocks, result.anchors)
                sink.write(chapter)
                val words = result.blocks.sumOf { b -> WORD.findAll(b.text).count() }
                chapters += ParsedChapter(index, chapter.title, chapter.charCount, words, startChar)
                startChar += chapter.charCount
                hrefToChapter[item.path] = index
                anchorsByHref[item.path] = result.anchors
            }
            if (chapters.isEmpty()) throw ParseException("Não foi possível extrair texto deste EPUB.")

            val toc = toc(zip, opf, manifest, spineEl)
                .mapNotNull { raw ->
                    val path = raw.href.substringBefore('#')
                    val chapter = hrefToChapter[path]?.coerceAtMost(chapters.lastIndex) ?: return@mapNotNull null
                    val fragment = raw.href.substringAfter('#', "")
                    val offset = if (fragment.isEmpty()) 0 else anchorsByHref[path]?.get(fragment) ?: 0
                    TocItem(raw.title, raw.depth, chapter, offset)
                }
            // Títulos de capítulo: entrada do sumário que aponta para o início, senão o primeiro título do texto.
            val titled = chapters.map { c ->
                val fromToc = toc.firstOrNull { it.chapterIndex == c.index && it.charOffset == 0 }?.title
                c.copy(title = fromToc ?: c.title)
            }
            onProgress(1f)
            return ParsedBook(meta, titled, toc.ifEmpty { titled.map { TocItem(it.title ?: "Capítulo ${it.index + 1}", 0, it.index, 0) } }, coverFile)
        }
    }

    // ---------------------------------------------------------------- partes

    private data class ManifestItem(val id: String, val path: String, val mediaType: String, val properties: String) {
        val isHtml get() = mediaType.contains("html") || path.endsWith(".xhtml") || path.endsWith(".html") || path.endsWith(".htm")
    }

    private data class RawToc(val title: String, val depth: Int, val href: String)

    private fun checkDrm(zip: ZipFile) {
        val enc = zip.getEntry("META-INF/encryption.xml") ?: return
        val xml = Jsoup.parse(zip.getInputStream(enc).use { String(it.readBytes()) }, "", Parser.xmlParser())
        val algorithms = xml.allTags("EncryptionMethod").map { it.attr("Algorithm") }
        // Ofuscação de fontes (IDPF/Adobe) não impede a leitura do texto; qualquer outra cifra é DRM.
        val drm = algorithms.any { it !in FONT_OBFUSCATION }
        if (drm || zip.getEntry("META-INF/rights.xml") != null) {
            throw ParseException("Este EPUB está protegido por DRM e não pode ser aberto. O Virgil só lê arquivos sem proteção.")
        }
    }

    private fun rootfilePath(zip: ZipFile): String {
        val container = zip.getEntry("META-INF/container.xml")
        if (container != null) {
            val xml = Jsoup.parse(zip.getInputStream(container).use { String(it.readBytes()) }, "", Parser.xmlParser())
            xml.allTags("rootfile").firstOrNull { it.attr("media-type").contains("oebps") || it.attr("full-path").endsWith(".opf") }
                ?.attr("full-path")?.takeIf { it.isNotBlank() }?.let { return decode(it) }
        }
        // EPUBs malformados sem container: procura o primeiro .opf.
        return zip.entries().asSequence().firstOrNull { it.name.endsWith(".opf") }?.name
            ?: throw ParseException("Arquivo EPUB inválido (sem container.xml nem pacote OPF).")
    }

    private fun metadata(opf: Document): BookMeta {
        val md = opf.allTags("metadata").firstOrNull() ?: opf
        fun dc(name: String) = md.allTags(name).map { TextNormalizer.clean(it.text()).trim() }.filter { it.isNotEmpty() }
        val creators = md.allTags("creator").let { els ->
            // Prefere quem tem papel de autor (EPUB 2: opf:role="aut"; EPUB 3: refines com role).
            val authors = els.filter { it.attr("opf:role").let { r -> r.isEmpty() || r == "aut" } }
            (authors.ifEmpty { els }).map { TextNormalizer.clean(it.text()).trim() }.filter { it.isNotEmpty() }
        }
        return BookMeta(
            title = dc("title").firstOrNull(),
            author = creators.take(3).joinToString(", ").ifEmpty { null },
            language = dc("language").firstOrNull(),
            publisher = dc("publisher").firstOrNull(),
            description = dc("description").firstOrNull()?.let { Jsoup.parse(it).text() }?.take(2_000),
            date = dc("date").firstOrNull(),
        )
    }

    private fun coverPath(opf: Document, manifest: Map<String, ManifestItem>): String? {
        manifest.values.firstOrNull { it.properties.split(' ').contains("cover-image") }?.let { return it.path }
        opf.allTags("meta").firstOrNull { it.attr("name") == "cover" }?.attr("content")?.let { id -> manifest[id]?.takeIf { it.mediaType.startsWith("image") }?.let { return it.path } }
        return manifest.values.firstOrNull { it.mediaType.startsWith("image") && (it.id.contains("cover", true) || it.path.substringAfterLast('/').contains("cover", true)) }?.path
    }

    private fun toc(zip: ZipFile, opf: Document, manifest: Map<String, ManifestItem>, spine: Element): List<RawToc> {
        // EPUB 3: documento de navegação
        manifest.values.firstOrNull { it.properties.split(' ').contains("nav") }?.let { nav ->
            runCatching {
                val doc = Jsoup.parse(zip.readText(nav.path), "", Parser.htmlParser())
                val navEl = doc.getElementsByTag("nav").firstOrNull { it.attr("epub:type").contains("toc") || it.attr("role") == "doc-toc" }
                    ?: doc.getElementsByTag("nav").firstOrNull()
                val dir = nav.path.substringBeforeLast('/', "")
                val out = ArrayList<RawToc>()
                fun walk(ol: Element, depth: Int) {
                    ol.children().filter { it.normalName() == "li" }.forEach { li ->
                        val a = li.children().firstOrNull { it.normalName() == "a" } ?: li.selectFirst("a")
                        val title = TextNormalizer.clean(a?.text() ?: li.ownText()).trim()
                        if (a != null && title.isNotEmpty() && a.attr("href").isNotBlank()) {
                            out += RawToc(title, depth, resolveHref(dir, a.attr("href")))
                        }
                        li.children().filter { it.normalName() == "ol" || it.normalName() == "ul" }.forEach { walk(it, depth + 1) }
                    }
                }
                navEl?.children()?.filter { it.normalName() == "ol" || it.normalName() == "ul" }?.forEach { walk(it, 0) }
                if (out.isNotEmpty()) return out
            }
        }
        // EPUB 2: NCX
        val ncxItem = manifest[spine.attr("toc")] ?: manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" } ?: return emptyList()
        return runCatching {
            val ncx = Jsoup.parse(zip.readText(ncxItem.path), "", Parser.xmlParser())
            val dir = ncxItem.path.substringBeforeLast('/', "")
            val out = ArrayList<RawToc>()
            fun walk(parent: Element, depth: Int) {
                parent.children().filter { it.normalName().substringAfter(':') == "navpoint" }.forEach { np ->
                    val title = np.allTags("text").firstOrNull()?.text()?.let { TextNormalizer.clean(it).trim() }.orEmpty()
                    val src = np.allTags("content").firstOrNull()?.attr("src").orEmpty()
                    if (title.isNotEmpty() && src.isNotEmpty()) out += RawToc(title, depth, resolveHref(dir, src))
                    walk(np, depth + 1)
                }
            }
            ncx.allTags("navMap").firstOrNull()?.let { walk(it, 0) }
            out
        }.getOrDefault(emptyList())
    }

    private fun ZipFile.readText(path: String): String {
        val entry: ZipEntry = getEntry(path) ?: getEntry(decode(path)) ?: throw ParseException("Arquivo ausente no EPUB: $path")
        if (entry.size > maxEntryBytes) throw ParseException("Um capítulo excede o tamanho máximo suportado.")
        // Lê com limite real, mesmo que o cabeçalho do ZIP minta o tamanho (proteção contra zip bomb).
        getInputStream(entry).use { input ->
            val bytes = input.readNBytes((maxEntryBytes + 1).toInt())
            if (bytes.size > maxEntryBytes) throw ParseException("Um capítulo excede o tamanho máximo suportado.")
            return decodeXmlBytes(bytes)
        }
    }

    companion object {
        private val WORD = Regex("\\p{L}+")
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg")
        private val FONT_OBFUSCATION = setOf("http://www.idpf.org/2008/embedding", "http://ns.adobe.com/pdf/enc#RC")

        /** Elementos por nome local, ignorando prefixo de namespace (dc:title, opf:meta, ncx:navPoint). */
        internal fun Element.allTags(local: String): List<Element> =
            getAllElements().filter { it.normalName().substringAfter(':').equals(local, ignoreCase = true) }

        internal fun decode(p: String): String = runCatching { URLDecoder.decode(p.replace("+", "%2B"), "UTF-8") }.getOrDefault(p)

        /** Resolve um caminho relativo (com ../ e ./) em relação a um diretório do ZIP. */
        internal fun resolve(dir: String, href: String): String {
            val clean = decode(href.substringBefore('#').substringBefore('?'))
            if (clean.startsWith("/")) return clean.trimStart('/')
            val parts = ArrayList<String>()
            (if (dir.isEmpty()) emptyList() else dir.split('/')).forEach { parts += it }
            clean.split('/').forEach { seg ->
                when (seg) {
                    "", "." -> {}
                    ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                    else -> parts += seg
                }
            }
            return parts.joinToString("/")
        }

        internal fun resolveHref(dir: String, href: String): String {
            val frag = href.substringAfter('#', "")
            val path = resolve(dir, href)
            return if (frag.isEmpty()) path else "$path#$frag"
        }

        /** Respeita a codificação declarada no prólogo XML (UTF-8 por padrão, conforme a especificação). */
        internal fun decodeXmlBytes(bytes: ByteArray): String {
            if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, Charsets.UTF_16BE)
            if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, Charsets.UTF_16LE)
            val head = String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1)
            val declared = Regex("encoding=[\"']([A-Za-z0-9_\\-]+)[\"']").find(head)?.groupValues?.get(1)
            val cs = runCatching { declared?.let { charset(it) } }.getOrNull() ?: Charsets.UTF_8
            return String(bytes, cs).removePrefix("﻿")
        }
    }
}

/** Destino dos capítulos convertidos (arquivo em disco no app, memória nos testes). */
fun interface ChapterSink {
    fun write(chapter: ChapterDoc)
}

class ParseException(message: String) : Exception(message)
