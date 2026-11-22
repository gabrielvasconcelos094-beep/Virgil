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

/**
 * Grafo de dependências do app (injeção manual — ADR-0006).
 * Tudo é criado de forma preguiçosa: abrir o app não abre o banco antes da primeira tela precisar.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: VirgilDatabase by lazy { VirgilDatabase.build(appContext) }
    private val preferencesStore by lazy { PreferencesStore(appContext) }

    val preferences by lazy { PreferencesRepository(preferencesStore) }
    val library by lazy { LibraryRepository(database) }
    val vocabulary by lazy { VocabularyRepository(database) }
    val profiles by lazy { ProfileRepository(database) }
    val progress by lazy { ProgressRepository(database) }
    val reviews by lazy { ReviewRepository(database) }
}
