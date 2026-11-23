package app.virgil.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.virgil.core.designsystem.component.VirgilDivider
import app.virgil.core.designsystem.theme.Spacing
import app.virgil.core.designsystem.theme.Virgil

private data class Notice(val name: String, val license: String, val detail: String)

/** Componentes de terceiros efetivamente incluídos no aplicativo nesta versão. */
private val Notices = listOf(
    Notice("Literata", "SIL Open Font License 1.1", "© The Literata Project Authors"),
    Notice("Source Serif 4", "SIL Open Font License 1.1", "© Adobe"),
    Notice("EB Garamond", "SIL Open Font License 1.1", "© The EB Garamond Project Authors"),
    Notice("Inter", "SIL Open Font License 1.1", "© The Inter Project Authors"),
    Notice("Charis SIL (transcrições fonéticas)", "SIL Open Font License 1.1", "© SIL International"),
    Notice(
        "Transcrições fonéticas (IPA)", "Creative Commons BY-SA 4.0",
        "Wiktionary (en.wiktionary.org), colaboradores do Wiktionary; extraídas com wiktextract (Tatu Ylonen) e adaptadas (variante e formato).",
    ),
    Notice("Listas de frequência de palavras (wordfreq)", "Creative Commons BY-SA 4.0", "© Robyn Speer e colaboradores (dados), usadas na estimativa de dificuldade"),
    Notice("llama.cpp / ggml", "MIT License", "© The ggml authors"),
    Notice("jsoup", "MIT License", "© Jonathan Hedley"),
    Notice("Android Jetpack (Compose, Room, DataStore, Navigation, Lifecycle)", "Apache License 2.0", "© The Android Open Source Project"),
    Notice("Kotlin e kotlinx.coroutines", "Apache License 2.0", "© JetBrains s.r.o."),
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = Spacing.xs, top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") }
            Text("Licenças", style = MaterialTheme.typography.headlineSmall, color = Virgil.colors.textPrimary)
        }
        LazyColumn {
            items(Notices) { n ->
                Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md)) {
                    Text(n.name, style = MaterialTheme.typography.titleSmall, color = Virgil.colors.textPrimary)
                    Text(n.license, style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.primary)
                    Text(n.detail, style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary)
                }
                VirgilDivider(Modifier.padding(horizontal = Spacing.screen))
            }
        }
    }
}
