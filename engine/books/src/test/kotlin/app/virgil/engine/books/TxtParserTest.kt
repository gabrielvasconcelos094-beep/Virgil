package app.virgil.engine.books

import app.virgil.engine.books.model.BlockType
import app.virgil.engine.books.model.ChapterDoc
import app.virgil.engine.books.parse.TxtParser
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TxtParserTest {
    @get:Rule val tmp = TemporaryFolder()

    private val prose = "It was a bright cold day in April, and the clocks were striking thirteen. The hallway smelt of\n" +
        "boiled cabbage and old rag mats. At one end of it a coloured poster, too large for indoor\n" +
        "display, had been tacked to the wall, and the stairs were narrow and steep for every-\n" +
        "one who climbed them.\n"

    private fun gutenberg(): String = buildString {
        append("The Project Gutenberg eBook of Treasure Island\n\nTitle: Treasure Island\n\nAuthor: Robert Louis Stevenson\n\nLanguage: English\n\n")
        append("*** START OF THE PROJECT GUTENBERG EBOOK TREASURE ISLAND ***\n\n\n")
        for (i in listOf("I", "II", "III")) {
            append("CHAPTER $i\n\nThe Old Sea-dog\n\n")
            repeat(5) { append(prose).append('\n') }
        }
        append("Under the wide and starry sky,\nDig the grave and let me lie.\nGlad did I live and gladly die,\nAnd I laid me down with a will.\n\n")
        append("*** END OF THE PROJECT GUTENBERG EBOOK TREASURE ISLAND ***\n\nLicense text that must not appear.\n")
    }

    private fun parse(text: String, charset: java.nio.charset.Charset = Charsets.UTF_8): Pair<app.virgil.engine.books.parse.ParsedBook, List<ChapterDoc>> {
        val f = tmp.newFile(); f.writeBytes(text.toByteArray(charset))
        val docs = ArrayList<ChapterDoc>()
        return TxtParser().parse(f, "arquivo") { docs.add(it) } to docs
    }

    @Test fun `remove cabecalho e licenca do gutenberg e le metadados`() {
        val (book, docs) = parse(gutenberg())
        assertThat(book.meta.title).isEqualTo("Treasure Island")
        assertThat(book.meta.author).isEqualTo("Robert Louis Stevenson")
        assertThat(docs.flatMap { it.blocks }.none { it.text.contains("License") || it.text.contains("Gutenberg") }).isTrue()
    }

    @Test fun `detecta capitulos com subtitulo`() {
        val (book, docs) = parse(gutenberg())
        assertThat(book.chapters.map { it.title }).containsExactly(
            "CHAPTER I — The Old Sea-dog", "CHAPTER II — The Old Sea-dog", "CHAPTER III — The Old Sea-dog",
        ).inOrder()
        assertThat(docs[0].blocks[0].type).isEqualTo(BlockType.Heading)
    }

    @Test fun `une linhas quebradas e preserva verso`() {
        val (_, docs) = parse(gutenberg())
        val p = docs[0].blocks[1].text
        assertThat(p).doesNotContain("\n")
        assertThat(p).contains("smelt of boiled cabbage")
        assertThat(p).contains("everyone who climbed") // hífen de quebra de linha reunido
        val verse = docs.last().blocks.last()
        assertThat(verse.type).isEqualTo(BlockType.Verse)
        assertThat(verse.text.lines()).hasSize(4)
    }

    @Test fun `windows-1252 e reconhecido`() {
        val text = "Café, naïveté — « déjà vu ».\n\n" + prose.repeat(3)
        val (_, docs) = parse(text, charset("windows-1252"))
        assertThat(docs[0].blocks[0].text).startsWith("Café, naïveté — « déjà vu ».")
    }

    @Test fun `sem titulos divide em partes`() {
        val (book, _) = parse((prose + "\n").repeat(400))
        assertThat(book.chapters.size).isAtLeast(2)
        assertThat(book.chapters[0].title).isEqualTo("Parte 1")
    }
}
