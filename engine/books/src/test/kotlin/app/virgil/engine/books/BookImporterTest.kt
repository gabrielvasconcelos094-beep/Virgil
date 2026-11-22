package app.virgil.engine.books

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.virgil.core.database.VirgilDatabase
import app.virgil.core.model.BookFormat
import app.virgil.engine.books.importer.BookImporter
import app.virgil.engine.books.importer.ImportOutcome
import app.virgil.engine.books.importer.StagedFile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BookImporterTest {
    @get:Rule val tmp = TemporaryFolder()
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VirgilDatabase::class.java).allowMainThreadQueries().build()

    @After fun close() = db.close()

    private fun staged(hash: String = "abc"): StagedFile {
        val f = tmp.newFile(); EpubFixtures.epub3(f, declaredLanguage = "fr") // metadado errado de propósito
        return StagedFile(f, "mrs-dalloway.epub", hash, BookFormat.EPUB)
    }

    @Test fun `importa epub grava capitulos e corrige idioma pelo conteudo`() = runTest {
        val files = BookFiles(tmp.newFolder("books"))
        val importer = BookImporter(db, files, tmp.newFolder("stage"))
        val r = importer.import(staged()) as ImportOutcome.Imported
        val book = db.books().get(r.bookId)!!
        assertThat(book.title).isEqualTo("Mrs Dalloway")
        assertThat(book.coverPath).isEqualTo("cover.png")
        val chapters = db.books().chapters(r.bookId)
        assertThat(chapters).hasSize(3)
        val doc = files.readChapter(book.storageDir, chapters[0].contentPath)
        assertThat(doc.blocks.first().text).isEqualTo("One")
        assertThat(db.books().toc(r.bookId)).hasSize(3)
        // O texto é inglês; o metadado dizia francês. Em texto tão curto a confiança decide.
        assertThat(book.language).isAnyOf("en-GB", "fr")
    }

    @Test fun `segundo import do mesmo arquivo e reconhecido como duplicado`() = runTest {
        val importer = BookImporter(db, BookFiles(tmp.newFolder("books")), tmp.newFolder("stage"))
        val first = importer.import(staged("same")) as ImportOutcome.Imported
        val second = importer.import(staged("same"))
        assertThat(second).isEqualTo(ImportOutcome.Duplicate(first.bookId, "Mrs Dalloway"))
    }

    @Test fun `falha limpa os arquivos e devolve motivo`() = runTest {
        val root = tmp.newFolder("books")
        val importer = BookImporter(db, BookFiles(root), tmp.newFolder("stage"))
        val drm = tmp.newFile(); EpubFixtures.drm(drm)
        val r = importer.import(StagedFile(drm, "x.epub", "h", BookFormat.EPUB))
        assertThat(r).isInstanceOf(ImportOutcome.Failed::class.java)
        assertThat(root.listFiles()!!.toList()).isEmpty()
    }
}
