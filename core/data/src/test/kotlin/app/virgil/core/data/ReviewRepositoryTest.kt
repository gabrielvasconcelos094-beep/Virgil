package app.virgil.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.virgil.core.database.VirgilDatabase
import app.virgil.core.model.Language
import app.virgil.core.model.SelectionKind
import app.virgil.engine.srs.Fsrs
import app.virgil.engine.srs.Rating
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReviewRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VirgilDatabase::class.java).allowMainThreadQueries().build()
    private var now = 1_800_000_000_000L
    private val vocab = VocabularyRepository(db) { now }
    private val repo = ReviewRepository(db, Fsrs(), { now }, newPerDay = 3)

    private suspend fun add(term: String, translation: String?) =
        vocab.saveLookup(term, Language.EN_GB, SelectionKind.Word, translation, "A sentence with $term.", null, null, 0, 0)

    @Test fun `fila respeita limite diario de novidades e ignora palavras sem traducao`() = runTest {
        listOf("scarcely", "bright", "witnessed", "hush", "solemnity").forEach { add(it, "tr") }
        add("untranslated", null)
        assertThat(repo.dueCount(now).first()).isEqualTo(3)
        val queue = repo.sessionQueue()
        assertThat(queue.map { it.term }).containsExactly("scarcely", "bright", "witnessed").inOrder()
    }

    @Test fun `bom agenda para dias depois e esqueci volta na sessao`() = runTest {
        add("scarcely", "mal")
        add("bright", "brilhante")
        val (a, b) = repo.sessionQueue()
        assertThat(repo.rate(a, Rating.Good, 1_000)).isNull()
        val again = repo.rate(b, Rating.Again, 1_000)
        assertThat(again).isNotNull()
        // Após rever duas palavras novas hoje, o limite (3) deixa entrar só mais 1 — mas não há outras.
        assertThat(repo.dueCount(now).first()).isEqualTo(0)
        now += 11 * 60_000L // "Esqueci" volta em 10 minutos
        assertThat(repo.sessionQueue().map { it.term }).containsExactly("bright")
        now += 4 * Fsrs.DAY_MS
        assertThat(repo.sessionQueue().map { it.term }).containsExactly("scarcely", "bright")
        assertThat(db.reviews().observeReviewCount().first()).isEqualTo(2)
    }

    @Test fun `estabilidade alta promove a palavra a aprendida`() = runTest {
        add("scarcely", "mal")
        var item = repo.sessionQueue().single()
        repeat(6) {
            repo.rate(item, Rating.Easy, null)
            now = db.reviews().card(item.id)!!.dueAt
            item = repo.sessionQueue().singleOrNull() ?: return@repeat
        }
        assertThat(db.vocabulary().get(1)!!.state).isEqualTo("Known")
        assertThat(repo.sessionQueue()).isEmpty()
    }
}
