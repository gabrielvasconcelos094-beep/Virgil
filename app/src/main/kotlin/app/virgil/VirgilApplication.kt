package app.virgil

import android.app.Application
import android.content.ComponentCallbacks2

class VirgilApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Livros importados (ou analisados por versão antiga) recebem a estimativa de dificuldade em segundo plano.
        container.analysisScheduler.start()
    }

    /** Sob pressão de memória, o modelo de tradução ocioso é liberado antes que o sistema mate o app. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            container.translatorIfCreated?.onTrimMemory(level)
        }
    }
}
