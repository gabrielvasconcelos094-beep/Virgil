package app.virgil.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.virgil.core.database.entity.BookEntity
import app.virgil.core.database.entity.ChapterEntity
import app.virgil.core.database.entity.TocEntryEntity
import app.virgil.core.database.entity.TranslationCacheEntity
import app.virgil.core.database.entity.VocabularyEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VirgilDatabaseTest {
    private lateinit var db: VirgilDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VirgilDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() = db.close()

    private fun book(hash: String = "h1") = BookEntity(
        title = "Mrs Dalloway", author = "Virginia Woolf", language = "en-GB", languageVariant = null, format = "EPUB",
        storageDir = "b1", coverPath = null, contentHash = hash, fileSize = 1000, publisher = null, description = null,
        publishedDate = null, chapterCount = 2, totalChars = 2000, addedAt = 1, lastOpenedAt = null, finishedAt = null,
    )

    private fun vocab(term: String, lang: String = "en-GB", bookId: Long? = null, at: Long = 10) = VocabularyEntity(
        term = term, termKey = term.lowercase(), lemma = null, language = lang, kind = "Word", translation = "t",
        contextSentence = "s", contextTranslation = null, bookId = bookId, chapterIndex = 0, charOffset = 0,
        createdAt = at, updatedAt = at, note = null,
    )

    @Test fun `importacao insere livro, capitulos e sumario juntos`() = runTest {
        val id = db.books().insertImported(
            book(),
            listOf(ChapterEntity(0, 0, "I", "c0.json", 0, 1000, 180), ChapterEntity(0, 1, "II", "c1.json", 1000, 1000, 170)),
            listOf(TocEntryEntity(bookId = 0, ordinal = 0, depth = 0, title = "I", chapterIndex = 0, charOffset = 0)),
        )
        assertThat(db.books().chapters(id).map { it.bookId }).containsExactly(id, id)
        assertThat(db.books().toc(id)).hasSize(1)
        assertThat(db.books().observeShelf().first().single().title).isEqualTo("Mrs Dalloway")
    }

    @Test fun `excluir livro remove capitulos e mantem vocabulario sem vinculo`() = runTest {
        val id = db.books().insertImported(book(), listOf(ChapterEntity(0, 0, null, "c0", 0, 10, 2)), emptyList())
        val v = db.vocabulary().recordLookup(vocab("scarcely", bookId = id))
        db.books().delete(id)
        assertThat(db.books().chapters(id)).isEmpty()
        assertThat(db.vocabulary().get(v)!!.bookId).isNull()
    }

    @Test fun `consulta repetida incrementa contador em vez de duplicar`() = runTest {
        val a = db.vocabulary().recordLookup(vocab("scarcely", at = 10))
        val b = db.vocabulary().recordLookup(vocab("scarcely", at = 20))
        assertThat(a).isEqualTo(b)
        val e = db.vocabulary().get(a)!!
        assertThat(e.lookupCount).isEqualTo(2)
        assertThat(e.updatedAt).isEqualTo(20)
        // Mesmo termo em outro idioma é outra entrada.
        assertThat(db.vocabulary().recordLookup(vocab("scarcely", lang = "fr"))).isNotEqualTo(a)
    }

    @Test fun `filtros de vocabulario combinam idioma, estado e busca`() = runTest {
        db.vocabulary().recordLookup(vocab("bright"))
        db.vocabulary().recordLookup(vocab("Bank", lang = "de"))
        val id = db.vocabulary().recordLookup(vocab("witnessed"))
        db.vocabulary().setState(id, "Known", 99)
        assertThat(db.vocabulary().observe("en-GB", null, null, null, null).first().map { it.term }).containsExactly("bright", "witnessed")
        assertThat(db.vocabulary().observe(null, null, "Known", null, null).first().map { it.term }).containsExactly("witnessed")
        assertThat(db.vocabulary().observe(null, null, null, null, "ban").first().map { it.term }).containsExactly("Bank")
    }

    @Test fun `cache de traducao e limitado por LRU`() = runTest {
        repeat(5) { i ->
            db.translationCache().put(TranslationCacheEntity("k$i", "m", "Word", "en-GB", "w$i", "r$i", null, i.toLong(), i.toLong()))
        }
        db.translationCache().trim(keep = 2)
        assertThat(db.translationCache().get("k4")).isNotNull()
        assertThat(db.translationCache().get("k3")).isNotNull()
        assertThat(db.translationCache().get("k0")).isNull()
    }
}
