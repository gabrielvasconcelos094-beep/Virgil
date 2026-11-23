package app.virgil

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.virgil.core.data.PreferencesRepository
import app.virgil.core.data.VocabularyRepository
import app.virgil.core.database.VirgilDatabase
import app.virgil.core.datastore.PreferencesStore
import app.virgil.core.designsystem.theme.VirgilTheme
import app.virgil.core.model.BookFormat
import app.virgil.core.model.ThemeMode
import app.virgil.engine.books.BookFiles
import app.virgil.engine.books.BookStore
import app.virgil.engine.books.importer.BookImporter
import app.virgil.engine.books.importer.ImportOutcome
import app.virgil.engine.books.importer.StagedFile
import app.virgil.feature.reader.ReaderRoute
import app.virgil.core.model.Language
import app.virgil.engine.translation.EngineStatus
import app.virgil.engine.translation.ModelCatalog
import app.virgil.engine.translation.TranslationEvent
import app.virgil.engine.translation.TranslationRequest
import app.virgil.engine.translation.Translator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Leitor de ponta a ponta: importa um EPUB real, abre o leitor, toca numa palavra e confere o
 * cartão de consulta; também salva no vocabulário. Gera capturas em app/build/outputs/roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ReaderTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: VirgilDatabase
    private lateinit var files: BookFiles
    private var bookId = 0L

    private val chapter1 = listOf(
        "It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.",
        "However little known the feelings or views of such a man may be on his first entering a neighbourhood, this truth is so well fixed in the minds of the surrounding families, that he is considered the rightful property of some one or other of their daughters.",
        "“My dear Mr. Bennet,” said his lady to him one day, “have you heard that Netherfield Park is let at last?”",
        "Mr. Bennet replied that he had not.",
        "“But it is,” returned she; “for Mrs. Long has just been here, and she told me all about it.”",
        "Mr. Bennet made no answer.",
        "“Do you not want to know who has taken it?” cried his wife impatiently.",
        "“<i>You</i> want to tell me, and I have no objection to hearing it.”",
        "This was invitation enough.",
        "“Why, my dear, you must know, Mrs. Long says that Netherfield is taken by a young man of large fortune from the north of England; that he came down on Monday in a chaise and four to see the place, and was so much delighted with it, that he agreed with Mr. Morris immediately; that he is to take possession before Michaelmas, and some of his servants are to be in the house by the end of next week.”",
        "“What is his name?”",
        "“Bingley.”",
        "“Is he married or single?”",
        "“Oh! Single, my dear, to be sure! A single man of large fortune; four or five thousand a year. What a fine thing for our girls!”",
        "“How so? How can it affect them?”",
        "“My dear Mr. Bennet,” replied his wife, “how can you be so tiresome! You must know that I am thinking of his marrying one of them.”",
    )

    @Before fun setUp() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, VirgilDatabase::class.java).allowMainThreadQueries().build()
        files = BookFiles(tmp.newFolder("books"))
        val epub = tmp.newFile("pp.epub")
        writeEpub(epub)
        val r = BookImporter(db, files, tmp.newFolder("stage")).import(StagedFile(epub, "pp.epub", "h", BookFormat.EPUB))
        bookId = (r as ImportOutcome.Imported).bookId
    }

    // O banco em memória não é fechado aqui: a regra do Compose destrói a Activity depois do @After,
    // e o leitor ainda grava a posição ao parar (comportamento desejado no app).
    @After fun tearDown() = Unit

    @Test fun leitorAbreTocaPalavraESalva() {
        val vocab = VocabularyRepository(db)
        var dark by mutableStateOf(false)
        compose.setContent {
            VirgilTheme(if (dark) ThemeMode.Dark else ThemeMode.Light) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    ReaderRoute(
                        bookId = bookId, store = BookStore(db, files), vocabulary = vocab,
                        preferences = PreferencesRepository(PreferencesStore(ApplicationProvider.getApplicationContext())),
                        translator = null,
                        imageRoot = { File(files.root, it).absolutePath }, onBack = {}, onOpenTranslationSettings = {},
                    )
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Mr. Bennet replied that he had not.").fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/leitor-claro.png")
        dark = true
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/leitor-escuro.png")
        dark = false

        // Toque sobre "Bennet" na frase curta: o deslocamento real do texto decide a palavra.
        compose.onNodeWithText("Mr. Bennet replied that he had not.").performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width * 0.18f, height / 2f))
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Salvar no vocabulário").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Tradução local ainda não disponível: nenhum modelo de tradução instalado.").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/leitor-consulta.png")

        compose.onNodeWithText("Salvar no vocabulário").performTouchInput { click() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("No vocabulário").fetchSemanticsNodes().isNotEmpty() }
        val saved = runBlocking { db.vocabulary().observe(null, null, null, null, null).first() }
        assertThat(saved).hasSize(1)
        assertThat(saved[0].term).isEqualTo("Bennet")
        assertThat(saved[0].context_sentence).isEqualTo("Mr. Bennet replied that he had not.")
        assertThat(saved[0].book_id).isEqualTo(bookId)

        // Frase: expande a seleção a partir da palavra.
        compose.onNodeWithText("Frase").performTouchInput { click() }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/leitor-frase.png")
    }

    /** Tradutor determinístico: verifica o fluxo da interface sem depender do modelo. */
    private class FakeTranslator : Translator {
        override val status = MutableStateFlow<EngineStatus>(EngineStatus.Ready(ModelCatalog.TranslateGemma4B))
        val requests = mutableListOf<TranslationRequest>()
        override fun translate(request: TranslationRequest) = flow {
            requests += request
            emit(TranslationEvent.Started)
            val text = mapOf("replied" to "respondeu").getOrElse(request.text) { "O Sr. Bennet respondeu que não." }
            emit(TranslationEvent.Done(text, fromCache = false, millis = 850))
        }
        override fun release() {}
    }

    /** Voz de teste: registra a ordem dos eventos (fala antes da tradução). */
    private class FakeSpeech(private val log: MutableList<String>) : app.virgil.engine.speech.SpeechEngine {
        override val ready = MutableStateFlow(true)
        override val speaking = MutableStateFlow<String?>(null)
        val spoken = mutableListOf<Pair<String, Language>>()
        override fun availability(language: Language) = app.virgil.engine.speech.VoiceAvailability.Ready
        override fun voices(language: Language) = emptyList<app.virgil.engine.speech.VoiceOption>()
        override fun speak(text: String, language: Language, rate: Float, voiceId: String?, id: String) {
            log += "fala:$text"; spoken += text to language; speaking.value = id
        }
        override fun stop() { speaking.value = null }
        override fun shutdown() {}
    }

    @Test fun traducaoApareceNoCartaoESalvaAutomaticamente() {
        val log = java.util.Collections.synchronizedList(mutableListOf<String>())
        val fake = object : Translator by FakeTranslator() {
            override fun translate(request: TranslationRequest) = flow {
                log += "traducao:${request.text}"
                emit(TranslationEvent.Started)
                kotlinx.coroutines.delay(300)
                emit(TranslationEvent.Done("respondeu", fromCache = false, millis = 300))
            }
        }
        val speech = FakeSpeech(log)
        val ipa = app.virgil.engine.speech.AssetIpaSource(ApplicationProvider.getApplicationContext())
        compose.setContent {
            VirgilTheme(ThemeMode.Light) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    ReaderRoute(
                        bookId = bookId, store = BookStore(db, files), vocabulary = VocabularyRepository(db),
                        preferences = PreferencesRepository(PreferencesStore(ApplicationProvider.getApplicationContext())),
                        translator = fake, imageRoot = { File(files.root, it).absolutePath }, onBack = {}, onOpenTranslationSettings = {},
                        speech = speech, ipa = ipa,
                    )
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Mr. Bennet replied that he had not.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Mr. Bennet replied that he had not.").performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width * 0.42f, height / 2f))
        }
        // Ordem: cartão com a palavra → áudio já pedido → IPA do léxico → tradução quando pronta.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("/ɹɪˈplaɪd/").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Parar").assertIsDisplayed()     // tocando
        compose.waitUntil(5_000) { compose.onAllNodesWithText("respondeu").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("No vocabulário").fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/leitor-traducao.png")
        assertThat(speech.spoken.first()).isEqualTo("replied" to Language.EN_GB)
        assertThat(log.indexOf("fala:replied")).isLessThan(log.indexOf("traducao:replied"))
        // Repetir: parar e tocar de novo.
        compose.onNodeWithText("Parar").performTouchInput { click() }
        compose.waitUntil(2_000) { compose.onAllNodesWithText("Ouvir").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Ouvir").performTouchInput { click() }
        compose.waitForIdle()
        assertThat(speech.spoken).hasSize(2)
        val saved = runBlocking { db.vocabulary().observe(null, null, null, null, null).first() }
        assertThat(saved.single().translation).isEqualTo("respondeu")
    }

    /** Avança o looper principal do Robolectric até a condição valer (view models fora de composição). */
    private fun awaitMain(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            check(System.currentTimeMillis() < deadline) { "condição não satisfeita a tempo" }
            Thread.sleep(10)
        }
    }

    @Test fun retomaNaPosicaoSalva() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = BookStore(db, files)
        val prefs = PreferencesRepository(PreferencesStore(ctx))
        // Sessão 1: vai para o capítulo II e "fecha" o app.
        val first = app.virgil.feature.reader.ReaderViewModel(bookId, store, VocabularyRepository(db), prefs, null)
        awaitMain { first.state.value.doc != null }
        first.goToChapter(1)
        awaitMain { first.state.value.chapterIndex == 1 && first.state.value.doc?.blocks?.firstOrNull()?.text == "Chapter II" }
        first.onVisible(1, 0.5f)
        first.onStopReading()
        awaitMain { runBlocking { db.books().position(bookId)?.chapterIndex } == 1 }
        val saved = runBlocking { db.books().position(bookId)!! }
        assertThat(saved.charOffset).isGreaterThan(0)
        val progress = runBlocking { db.books().get(bookId)!!.progress }
        assertThat(progress).isGreaterThan(0.5f)
        // Sessão 2: um novo leitor abre direto no mesmo capítulo e bloco.
        val second = app.virgil.feature.reader.ReaderViewModel(bookId, store, VocabularyRepository(db), prefs, null)
        awaitMain { second.state.value.doc != null }
        assertThat(second.state.value.chapterIndex).isEqualTo(1)
        assertThat(second.state.value.scrollTarget?.blockIndex).isEqualTo(1)
    }

    @Test fun analiseDoLivroETelaDeDificuldade() {
        runBlocking {
            app.virgil.engine.analysis.AnalysisWorker.analyze(db, files, bookId)
            db.profiles().upsert(app.virgil.core.database.entity.LanguageProfileEntity("en-GB", "B1", 0))
        }
        val book = runBlocking { db.books().get(bookId)!! }
        assertThat(book.analysisState).isEqualTo("Done")
        val analysis = runBlocking { db.analysis().observe(bookId).first() }
        assertThat(analysis).isNotNull()
        println("Análise: ${analysis!!.levelLow}–${analysis.levelHigh} (confiança ${analysis.confidence})")
        compose.setContent {
            VirgilTheme(ThemeMode.Light) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    app.virgil.feature.library.BookDetailsRoute(bookId, app.virgil.engine.analysis.BookInsights(db), onBack = {}, onRead = {}, onSetLevel = {})
                }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Dificuldade estimada".uppercase()).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/analise-livro.png")
    }

    private fun writeEpub(file: File) {
        val body = chapter1.joinToString("") { "<p>$it</p>" }
        val entries = linkedMapOf(
            "META-INF/container.xml" to """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            "OEBPS/content.opf" to """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Pride and Prejudice</dc:title><dc:creator>Jane Austen</dc:creator><dc:language>en</dc:language></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="c2.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>""",
            "OEBPS/nav.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol><li><a href="c1.xhtml">Chapter I</a></li><li><a href="c2.xhtml">Chapter II</a></li></ol></nav></body></html>""",
            "OEBPS/c1.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><body><h2>Chapter I</h2>$body</body></html>""",
            "OEBPS/c2.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><body><h2>Chapter II</h2><p>Mr. Bennet was among the earliest of those who waited on Mr. Bingley.</p></body></html>""",
        )
        ZipOutputStream(file.outputStream()).use { zip ->
            val mime = "application/epub+zip".toByteArray()
            zip.putNextEntry(ZipEntry("mimetype").apply { method = ZipEntry.STORED; size = mime.size.toLong(); compressedSize = size; crc = CRC32().apply { update(mime) }.value })
            zip.write(mime); zip.closeEntry()
            entries.forEach { (n, c) -> zip.putNextEntry(ZipEntry(n)); zip.write(c.toByteArray()); zip.closeEntry() }
        }
    }
}
