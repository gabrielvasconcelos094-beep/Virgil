package app.virgil.engine.books

import app.virgil.engine.books.model.BlockType
import app.virgil.engine.books.model.ChapterDoc
import app.virgil.engine.books.model.SpanStyle
import app.virgil.engine.books.parse.EpubParser
import app.virgil.engine.books.parse.ParseException
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubParserTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun parse(make: (java.io.File) -> Unit): Pair<app.virgil.engine.books.parse.ParsedBook, List<ChapterDoc>> {
        val f = tmp.newFile("b.epub"); make(f)
        val out = tmp.newFolder()
        val docs = ArrayList<ChapterDoc>()
        return EpubParser().parse(f, out, sink = { docs.add(it) }) to docs
    }

    @Test fun `epub3 metadados capa e sumario`() {
        val (book, docs) = parse { EpubFixtures.epub3(it) }
        assertThat(book.meta.title).isEqualTo("Mrs Dalloway")
        assertThat(book.meta.author).isEqualTo("Virginia Woolf")
        assertThat(book.meta.language).isEqualTo("en")
        assertThat(book.cover!!.name).isEqualTo("cover.png")
        // A página que só contém a capa não vira capítulo; notas não lineares vão para o fim.
        assertThat(docs.map { it.title }).containsExactly("One", "Two", null).inOrder()
        assertThat(book.toc.map { it.title }).containsExactly("One", "Scene two", "Two").inOrder()
        val scene = book.toc[1]
        assertThat(scene.depth).isEqualTo(1)
        assertThat(scene.chapterIndex).isEqualTo(0)
        val ch1 = docs[0]
        val block = ch1.blocks[ch1.blockIndexAt(scene.charOffset)]
        assertThat(block.text).isEqualTo("What a lark! What a plunge!")
    }

    @Test fun `estrutura do texto e estilos`() {
        val (_, docs) = parse { EpubFixtures.epub3(it) }
        val b = docs[0].blocks
        assertThat(b[0].type).isEqualTo(BlockType.Heading)
        assertThat(b[1].text).isEqualTo("Mrs. Dalloway said she would buy the flowers herself.1")
        val italic = b[1].spans.first { it.style == SpanStyle.Italic }
        assertThat(b[1].text.substring(italic.start, italic.end)).isEqualTo("flowers")
        assertThat(b[1].spans.any { it.style == SpanStyle.Link && it.href == "notes.xhtml#n1" }).isTrue()
        // Quebras de linha do código-fonte viram um espaço só.
        assertThat(b[2].text).isEqualTo("For Lucy had her work cut out for her.")
        assertThat(b[3].type).isEqualTo(BlockType.Separator)
        val verse = b.first { it.type == BlockType.Verse }
        assertThat(verse.text).isEqualTo("The sea is calm to-night.\nThe tide is full, the moon lies fair")
        assertThat(b.first { it.type == BlockType.Quote }.text).isEqualTo("Fear no more the heat o’ the sun")
        val img = b.first { it.type == BlockType.Image }
        assertThat(img.image).startsWith("images/")
        assertThat(b.none { it.text.contains("alert") }).isTrue()
        assertThat(docs[1].blocks.filter { it.type == BlockType.ListItem }.map { it.text }).containsExactly("First", "Second")
    }

    @Test fun `offsets sao contiguos e consistentes`() {
        val (book, docs) = parse { EpubFixtures.epub3(it) }
        docs.forEach { d ->
            var expected = 0
            d.blocks.forEach { bl -> assertThat(bl.start).isEqualTo(expected); expected += bl.text.length + 1 }
        }
        assertThat(book.chapters[1].startChar).isEqualTo(book.chapters[0].charCount.toLong())
    }

    @Test fun `epub2 com ncx e codificacao latin1`() {
        val (book, docs) = parse { EpubFixtures.epub2(it) }
        assertThat(book.meta.title).isEqualTo("Madame Bovary")
        assertThat(book.meta.author).isEqualTo("Gustave Flaubert") // tradutor (trl) excluído
        assertThat(book.cover!!.name).isEqualTo("cover.jpg")
        assertThat(docs.single().blocks[1].text).isEqualTo("Nous étions à l'Étude, quand le Proviseur entra.")
        assertThat(book.toc.map { it.title to it.depth }).containsExactly("Première partie" to 0, "Chapitre I" to 1).inOrder()
        assertThat(book.toc[1].charOffset).isGreaterThan(0)
    }

    @Test fun `drm e recusado com mensagem clara`() {
        val e = assertThrows(ParseException::class.java) { parse { EpubFixtures.drm(it) } }
        assertThat(e.message).contains("DRM")
    }

    @Test fun `livro grande e processado capitulo a capitulo`() {
        val para = "<p>" + "It was the best of times, it was the worst of times, it was the age of wisdom. ".repeat(12) + "</p>"
        val entries = HashMap<String, ByteArray>()
        entries["META-INF/container.xml"] = EpubFixtures.CONTAINER.toByteArray()
        val n = 120
        entries["OEBPS/content.opf"] = ("""<package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Big</dc:title></metadata><manifest>""" +
            (0 until n).joinToString("") { """<item id="c$it" href="c$it.xhtml" media-type="application/xhtml+xml"/>""" } +
            "</manifest><spine>" + (0 until n).joinToString("") { """<itemref idref="c$it"/>""" } + "</spine></package>").toByteArray()
        repeat(n) { i -> entries["OEBPS/c$i.xhtml"] = EpubFixtures.xhtml("<h1>Chapter ${i + 1}</h1>" + para.repeat(40)).toByteArray() }
        val start = System.nanoTime()
        val (book, _) = parse { EpubFixtures.write(it, entries) }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertThat(book.chapters).hasSize(n)
        assertThat(book.totalChars).isGreaterThan(4_000_000L)
        println("EPUB grande: ${book.totalChars} caracteres, ${book.chapters.sumOf { it.wordCount }} palavras em $ms ms")
        assertThat(ms).isLessThan(30_000L)
    }
}
