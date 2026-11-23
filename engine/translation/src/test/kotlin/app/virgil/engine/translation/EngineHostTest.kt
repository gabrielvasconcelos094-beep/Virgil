package app.virgil.engine.translation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.virgil.core.database.VirgilDatabase
import app.virgil.core.model.Language
import app.virgil.core.model.SelectionKind
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/**
 * O motor do app de ponta a ponta com o modelo real (TranslateGemma), na JVM.
 * Requer VIRGIL_LLAMA_HOST_LIB e VIRGIL_TEST_MODEL (ver engine/llama/LlamaHostTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EngineHostTest {
    private val lib = System.getProperty("virgil.llama.lib")
    private val model = System.getProperty("virgil.test.model")
    private lateinit var db: VirgilDatabase
    private lateinit var engine: LocalTranslationEngine

    @Before fun setUp() = runBlocking {
        assumeTrue("modelo de teste não configurado", lib != null && model != null)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, VirgilDatabase::class.java).allowMainThreadQueries().build()
        val store = ModelStore(ctx, db)
        val spec = ModelCatalog.TranslateGemma4B
        val link = store.fileFor(spec).toPath()
        Files.deleteIfExists(link)
        Files.createSymbolicLink(link, File(model!!).toPath())
        store.markReady(spec)
        withTimeout(5_000) { store.installed.first { it != null } }
        engine = LocalTranslationEngine(db, store, nativeLibDir = null, hostLibraryPath = lib, cpuSupported = true, threads = 4)
    }

    @After fun tearDown() { if (::db.isInitialized) db.close() }

    private fun word(term: String, sentence: String, lang: Language = Language.EN_GB) =
        TranslationRequest(SelectionKind.Word, lang, term, sentence, bookId = null, chapterIndex = 0)

    @Test fun traduzCacheiaERegistraHistorico() = runBlocking {
        val req = word("scarcely", "She could scarcely believe what she had witnessed.")
        val first = engine.translate(req).filterIsInstance<TranslationEvent.Done>().first()
        println("scarcely -> '${first.text}' em ${first.millis} ms (inclui carregar o modelo)")
        assertThat(first.fromCache).isFalse()
        assertThat(first.text).isNotEmpty()

        val second = engine.translate(req).filterIsInstance<TranslationEvent.Done>().first()
        assertThat(second.fromCache).isTrue()
        assertThat(second.text).isEqualTo(first.text)

        val fr = engine.translate(word("passé", "Il a passé l'examen hier, mais il ne connaît pas encore le résultat.", Language.FR))
            .filterIsInstance<TranslationEvent.Done>().first()
        println("passé -> '${fr.text}' em ${fr.millis} ms (modelo já carregado)")

        val sentence = engine.translate(TranslationRequest(SelectionKind.Sentence, Language.DE, "Sie schlug das Buch auf und fing an zu lesen."))
            .toList()
        val done = sentence.filterIsInstance<TranslationEvent.Done>().single()
        println("frase DE -> '${done.text}' em ${done.millis} ms; eventos parciais: ${sentence.count { it is TranslationEvent.Partial }}")
        assertThat(done.text.lowercase()).contains("livro")

        assertThat(db.vocabulary().observeLookupCount().first()).isEqualTo(4)
    }

    /** Antes: o primeiro toque pagava a carga do modelo. Depois: o leitor aquece ao abrir. */
    @Test fun aquecimentoAoAbrirOLeitor() = runBlocking {
        val req = word("witnessed", "She could scarcely believe what she had witnessed.")
        val t0 = System.nanoTime()
        engine.warmUp(Language.EN_GB)
        withTimeout(600_000) { engine.status.first { it is EngineStatus.Ready } }
        kotlinx.coroutines.delay(50)
        val warmMs = (System.nanoTime() - t0) / 1_000_000
        val first = engine.translate(req).filterIsInstance<TranslationEvent.Done>().first()
        println("aquecimento (carga + prefixo) em $warmMs ms; primeiro toque depois: '${first.text}' em ${first.millis} ms")
        assertThat(first.fromCache).isFalse()
        assertThat(first.text).isNotEmpty()
    }

    @Test fun ultimaConsultaVence() = runBlocking {
        val long = async {
            engine.translate(TranslationRequest(SelectionKind.Paragraph, Language.FR,
                "Parfois, à peine ma bougie éteinte, mes yeux se fermaient si vite que je n'avais pas le temps de me dire : « Je m'endors. » Et, une demi-heure après, la pensée qu'il était temps de chercher le sommeil m'éveillait."))
                .toList()
        }
        kotlinx.coroutines.delay(1_500)
        val quick = engine.translate(word("bright", "His future looks bright.")).filterIsInstance<TranslationEvent.Done>().first()
        val longEvents = long.await()
        println("palavra durante parágrafo -> '${quick.text}' (${quick.millis} ms); parágrafo terminou? ${longEvents.any { it is TranslationEvent.Done }}")
        assertThat(quick.text).isNotEmpty()
        assertThat(longEvents.none { it is TranslationEvent.Done }).isTrue()
    }
}
