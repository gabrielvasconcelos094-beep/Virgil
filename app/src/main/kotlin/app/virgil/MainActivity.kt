package app.virgil

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.virgil.core.designsystem.theme.VirgilTheme
import app.virgil.core.model.ThemeMode
import app.virgil.ui.VirgilApp
import app.virgil.widget.VocabularyWidget
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Palavra a abrir no vocabulário (toque no widget). */
    private val pendingEntry = MutableStateFlow<Long?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readEntry(intent)
    }

    private fun readEntry(intent: Intent?) {
        intent?.getLongExtra(VocabularyWidget.EXTRA_ENTRY, -1L)?.takeIf { it > 0 }?.let { pendingEntry.value = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readEntry(intent)
        val container = (application as VirgilApplication).container
        setContent {
            val prefs by container.preferences.preferences.collectAsStateWithLifecycle(initialValue = null)
            val mode = prefs?.theme ?: ThemeMode.System
            val dark = when (mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // Barras do sistema transparentes, com ícones claros/escuros conforme o tema do app (não o do sistema).
            DisposableEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                )
                onDispose {}
            }
            val entry by pendingEntry.collectAsStateWithLifecycle()
            VirgilTheme(mode) {
                VirgilApp(container, openEntryId = entry, onEntryOpened = { pendingEntry.value = null })
            }
        }
    }
}
