package app.virgil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.virgil.core.data.LanguageProfile
import app.virgil.core.designsystem.component.DifficultyStrip
import app.virgil.core.designsystem.component.TypographicCover
import app.virgil.core.designsystem.theme.VirgilTheme
import app.virgil.core.model.AnalysisState
import app.virgil.core.model.AppPreferences
import app.virgil.core.model.Book
import app.virgil.core.model.BookFormat
import app.virgil.core.model.CefrLevel
import app.virgil.core.model.CefrRange
import app.virgil.core.model.Language
import app.virgil.core.model.LearningState
import app.virgil.core.model.SelectionKind
import app.virgil.core.model.ThemeMode
import app.virgil.core.model.VocabularyEntry
import app.virgil.feature.library.LibraryScreen
import app.virgil.feature.library.LibraryUiState
import app.virgil.feature.settings.SettingsScreen
import app.virgil.feature.vocabulary.VocabularyScreen
import app.virgil.feature.vocabulary.VocabularyUiState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renderiza as telas com estados de exemplo para revisão visual do design (não são dados do app).
 * Saída em app/build/outputs/roborazzi/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val books = listOf(
        book(1, "Mrs Dalloway", "Virginia Woolf", Language.EN_GB, 0.42f, CefrRange(CefrLevel.B2, CefrLevel.C1, 0.7f)),
        book(2, "Madame Bovary", "Gustave Flaubert", Language.FR, 0.08f, CefrRange(CefrLevel.B2, CefrLevel.B2, 0.6f)),
        book(3, "Die Verwandlung", "Franz Kafka", Language.DE, 0f, CefrRange(CefrLevel.B1, CefrLevel.B2, 0.6f)),
        book(4, "Il fu Mattia Pascal", "Luigi Pirandello", Language.IT, 1f, null),
        book(5, "Niebla", "Miguel de Unamuno", Language.ES, 0.63f, CefrRange(CefrLevel.B2, CefrLevel.C1, 0.5f)),
        book(6, "Pride and Prejudice", "Jane Austen", Language.EN_GB, 0.15f, CefrRange(CefrLevel.C1, CefrLevel.C1, 0.6f)),
    )

    private val words = listOf(
        entry(1, "scarcely", "mal · quase não", "She could scarcely believe what she had witnessed.", "Mrs Dalloway"),
        entry(2, "lambeaux", "restos · farrapos", "Pendant plusieurs jours de suite des lambeaux d'armée en déroute avaient traversé la ville.", "Boule de suif", Language.FR),
        entry(3, "Ungeziefer", "inseto · praga", "…fand er sich in seinem Bett zu einem ungeheueren Ungeziefer verwandelt.", "Die Verwandlung", Language.DE),
        entry(4, "mezzogiorno", "sul", "Quel ramo del lago di Como, che volge a mezzogiorno…", "I promessi sposi", Language.IT),
        entry(5, "exquisita", "deliciosa", "La cena estaba exquisita, aunque el piso de mis tíos es bastante pequeño.", null, Language.ES),
    )

    /** Renderiza o conteúdo no tema claro e depois no escuro (um único setContent por teste). */
    private fun shot(name: String, themes: List<Boolean> = listOf(false, true), content: @Composable () -> Unit) {
        var dark by mutableStateOf(themes.first())
        compose.setContent {
            VirgilTheme(if (dark) ThemeMode.Dark else ThemeMode.Light) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        themes.forEach { d ->
            dark = d
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name-${if (d) "escuro" else "claro"}.png")
        }
    }

    @Test fun biblioteca() = shot("biblioteca") {
        LibraryScreen(LibraryUiState(loading = false, books = books, totalBooks = books.size, languages = books.mapNotNull { it.language }.distinct()), onSearch = {}, onOpenBook = {}, onOpenSettings = {})
    }

    @Test fun bibliotecaVazia() = shot("biblioteca-vazia", listOf(false)) { LibraryScreen(LibraryUiState(loading = false), onSearch = {}, onOpenBook = {}, onOpenSettings = {}) }

    @Test fun vocabulario() = shot("vocabulario") { VocabularyScreen(VocabularyUiState(loading = false, entries = words), {}, {}, {}) }

    @Test fun configuracoes() = shot("configuracoes") {
        run {
            SettingsScreen(
                prefs = AppPreferences(),
                profiles = Language.Learnable.map { LanguageProfile(it, if (it == Language.EN_GB) CefrLevel.B2 else null) },
                onBack = {}, onTheme = {}, onReading = {}, onAutoSave = {}, onLevel = { _, _ -> }, onOpenLicenses = {},
            )
        }
    }

    @Test fun traducao() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
        val db = androidx.room.Room.inMemoryDatabaseBuilder(ctx, app.virgil.core.database.VirgilDatabase::class.java).allowMainThreadQueries().build()
        val store = app.virgil.engine.translation.ModelStore(ctx, db)
        val translator = object : app.virgil.engine.translation.Translator {
            override val status = kotlinx.coroutines.flow.MutableStateFlow<app.virgil.engine.translation.EngineStatus>(app.virgil.engine.translation.EngineStatus.NoModel)
            override fun translate(request: app.virgil.engine.translation.TranslationRequest) = kotlinx.coroutines.flow.emptyFlow<app.virgil.engine.translation.TranslationEvent>()
            override fun release() {}
        }
        shot("traducao", listOf(false)) { app.virgil.feature.settings.TranslationModelRoute(store, translator, onBack = {}) }
    }

    @Test fun revisao() {
        val item = app.virgil.core.data.ReviewItem(
            1, "scarcely", Language.EN_GB, "mal · quase não", "She could scarcely believe what she had witnessed.", "Ela mal podia acreditar no que tinha presenciado.",
            "Mrs Dalloway", 2, app.virgil.engine.srs.CardState(),
        )
        val intervals = mapOf(
            app.virgil.engine.srs.Rating.Again to "10 min", app.virgil.engine.srs.Rating.Hard to "1 d",
            app.virgil.engine.srs.Rating.Good to "3 d", app.virgil.engine.srs.Rating.Easy to "16 d",
        )
        shot("revisao") {
            app.virgil.feature.review.ReviewScreen(
                app.virgil.feature.review.ReviewUi.Session(item, revealed = true, position = 3, total = 12, intervals = intervals, shownAt = 0),
                due = 12, reviewedToday = 2, onStart = {}, onReveal = {}, onRate = {}, onKnown = {}, onClose = {},
            )
        }
    }

    @Test fun progresso() {
        val minutes = listOf(0f, 22f, 35f, 0f, 18f, 41f, 27f, 0f, 12f, 30f, 45f, 25f, 0f, 33f)
        val reviews = listOf(0f, 10f, 14f, 0f, 8f, 20f, 12f, 0f, 6f, 15f, 18f, 9f, 0f, 11f)
        val labels = (9..22).map { if (it == 22) "hoje" else "$it/11" }
        shot("progresso") {
            app.virgil.feature.progress.ProgressScreen(
                app.virgil.core.data.ProgressSummary(
                    books = 6, finished = 1, readingMs = 288L * 60_000, lookups = 214, vocabulary = 163, reviews = 412,
                    byLanguage = listOf(
                        app.virgil.core.data.LanguageProgress(Language.EN_GB, 92, 31),
                        app.virgil.core.data.LanguageProgress(Language.FR, 51, 12),
                        app.virgil.core.data.LanguageProgress(Language.DE, 20, 2),
                    ),
                ),
                app.virgil.core.data.DailySeries(labels, minutes),
                app.virgil.core.data.DailySeries(labels, reviews),
                listOf(app.virgil.core.data.BookProgress("Mrs Dalloway", 0.42f, false), app.virgil.core.data.BookProgress("Madame Bovary", 0.08f, false)),
            )
        }
    }

    @Test fun capasEMapa() = shot("capas-e-mapa", listOf(false)) {
        Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("Heart of Darkness" to "Joseph Conrad", "Le Rouge et le Noir" to "Stendhal", "Effi Briest" to "Theodor Fontane").forEach { (t, a) ->
                TypographicCover(t, a, modifier = Modifier.width(110.dp))
            }
        }
        Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 240.dp).width(340.dp)) {
            DifficultyStrip(listOf(CefrLevel.B1, CefrLevel.B2, CefrLevel.C1, CefrLevel.B2, CefrLevel.B1, CefrLevel.B2, CefrLevel.C2, CefrLevel.C1, null, CefrLevel.B2), height = 40)
        }
    }

    private fun book(id: Long, t: String, a: String, l: Language, p: Float, d: CefrRange?) = Book(
        id, t, a, l, null, BookFormat.EPUB, null, 20, 400_000, 0, null, p, null, d, AnalysisState.Done,
    )

    private fun entry(id: Long, term: String, tr: String, ctx: String, book: String?, l: Language = Language.EN_GB) = VocabularyEntry(
        id, term, null, l, SelectionKind.Word, tr, ctx, null, null, book, 0, 0, 1, LearningState.New, null,
    )
}
