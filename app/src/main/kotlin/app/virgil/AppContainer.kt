package app.virgil

import android.content.Context
import app.virgil.core.data.LibraryRepository
import app.virgil.core.data.PreferencesRepository
import app.virgil.core.data.ProfileRepository
import app.virgil.core.data.ProgressRepository
import app.virgil.core.data.ReviewRepository
import app.virgil.core.data.VocabularyRepository
import app.virgil.core.database.VirgilDatabase
import app.virgil.core.datastore.PreferencesStore
import app.virgil.engine.books.BookFiles
import app.virgil.engine.books.BookStore
import app.virgil.engine.books.importer.ImportController
import app.virgil.engine.analysis.AnalysisScheduler
import app.virgil.engine.analysis.BookInsights
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import app.virgil.engine.translation.LocalTranslationEngine
import app.virgil.engine.translation.ModelStore

/**
 * Grafo de dependências do app (injeção manual — ADR-0006).
 * Tudo é criado de forma preguiçosa: abrir o app não abre o banco antes da primeira tela precisar.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: VirgilDatabase by lazy { VirgilDatabase.getInstance(appContext) }
    private val preferencesStore by lazy { PreferencesStore(appContext) }

    val preferences by lazy { PreferencesRepository(preferencesStore) }
    val booksRoot by lazy { java.io.File(appContext.filesDir, "books") }
    val library by lazy { LibraryRepository(database, booksRoot) }
    val vocabulary by lazy { VocabularyRepository(database) }
    val profiles by lazy { ProfileRepository(database) }
    val progress by lazy { ProgressRepository(database) }
    val reviews by lazy { ReviewRepository(database) }
    val bookFiles by lazy { BookFiles(booksRoot) }
    val bookStore by lazy { BookStore(database, bookFiles) }
    val imports by lazy { ImportController(appContext) }
    val models by lazy { ModelStore(appContext, database) }
    val insights by lazy { BookInsights(database) }
    /** Escopo de longa duração para tarefas do app (agendamento de análises). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val analysisScheduler by lazy { AnalysisScheduler(appContext, database, appScope) }
    private val translatorLazy = lazy { LocalTranslationEngine(database, models, appContext.applicationInfo.nativeLibraryDir) }
    val translator by translatorLazy
    /** Evita criar o motor só para responder a onTrimMemory. */
    val translatorIfCreated: LocalTranslationEngine? get() = if (translatorLazy.isInitialized()) translator else null
    /** Voz do sistema (TextToSpeech); criada na primeira tela que precisa dela. */
    val speech by lazy { app.virgil.engine.speech.AndroidSpeechEngine(appContext) }
    /** Léxicos IPA offline (assets), carregados por idioma sob demanda. */
    val ipa by lazy { app.virgil.engine.speech.AssetIpaSource(appContext) }
}
