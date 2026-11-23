package app.virgil.feature.settings

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.virgil.core.data.PreferencesRepository
import app.virgil.core.designsystem.component.ChipRow
import app.virgil.core.designsystem.component.QuietChip
import app.virgil.core.designsystem.component.SectionLabel
import app.virgil.core.designsystem.component.SwitchRow
import app.virgil.core.designsystem.component.VirgilDivider
import app.virgil.core.designsystem.theme.Spacing
import app.virgil.core.designsystem.theme.Virgil
import app.virgil.core.model.Language
import app.virgil.core.model.SpeechPreferences
import app.virgil.engine.speech.SpeechEngine
import app.virgil.engine.speech.VoiceAvailability
import kotlinx.coroutines.launch

/** Frases de teste curtas, na variante de cada idioma. */
private val Samples = mapOf(
    Language.EN_GB to "The rain had stopped, and the street was quiet again.",
    Language.FR to "La pluie avait cessé, et la rue était de nouveau calme.",
    Language.DE to "Der Regen hatte aufgehört, und die Straße war wieder still.",
    Language.IT to "La pioggia era cessata e la strada era di nuovo tranquilla.",
    Language.ES to "La lluvia había cesado y la calle volvía a estar tranquila.",
    Language.PT_BR to "A chuva tinha parado, e a rua estava quieta outra vez.",
)

internal val SpeechRates = listOf(0.6f to "Bem lenta", 0.75f to "Lenta", 0.9f to "Natural", 1.0f to "Normal", 1.2f to "Rápida")

@Composable
fun SpeechSettingsRoute(prefsRepo: PreferencesRepository, speech: SpeechEngine, onBack: () -> Unit) {
    val prefs by prefsRepo.preferences.collectAsStateWithLifecycle(initialValue = null)
    val ready by speech.ready.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val p = prefs?.speech ?: return
    SpeechSettingsScreen(
        prefs = p, ready = ready, speech = speech, onBack = onBack,
        onAutoPlay = { scope.launch { prefsRepo.update { setSpeechAutoPlay(it) } } },
        onShowIpa = { scope.launch { prefsRepo.update { setShowIpa(it) } } },
        onRate = { scope.launch { prefsRepo.update { setSpeechRate(it) } } },
        onVoice = { l, v -> scope.launch { prefsRepo.update { setVoice(l.iso639, v) } } },
        onInstall = {
            runCatching { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .recoverCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        },
    )
}

@Composable
internal fun SpeechSettingsScreen(
    prefs: SpeechPreferences,
    ready: Boolean,
    speech: SpeechEngine,
    onBack: () -> Unit,
    onAutoPlay: (Boolean) -> Unit,
    onShowIpa: (Boolean) -> Unit,
    onRate: (Float) -> Unit,
    onVoice: (Language, String?) -> Unit,
    onInstall: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Spacing.xxl)) {
        Row(Modifier.padding(start = Spacing.xs, top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") }
            Text("Pronúncia", style = MaterialTheme.typography.headlineSmall, color = Virgil.colors.textPrimary)
        }
        Text(
            "O Virgil usa o mecanismo de voz do Android, no idioma e na variante do livro (inglês britânico, espanhol da Espanha…). " +
                "As vozes que exigem internet só são usadas se você escolhê-las aqui.",
            style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        )
        SwitchRow("Tocar ao tocar numa palavra", "A pronúncia começa assim que o cartão abre", prefs.autoPlay, onAutoPlay)
        SwitchRow("Mostrar transcrição fonética (IPA)", "Do Wiktionary; palavras sem transcrição não mostram nenhuma", prefs.showIpa, onShowIpa)

        SectionLabel("Velocidade")
        ChipRow { SpeechRates.forEach { (r, label) -> QuietChip(label, kotlin.math.abs(prefs.rate - r) < 0.01f) { onRate(r) } } }

        VirgilDivider(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.lg))
        SectionLabel("Vozes por idioma")
        if (!ready) {
            Text(
                "O mecanismo de voz do aparelho ainda está iniciando ou não está instalado.",
                style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.screen),
            )
        }
        Language.entries.forEach { lang -> LanguageVoices(lang, prefs, ready, speech, onVoice, onInstall) }
    }
}

@Composable
private fun LanguageVoices(
    lang: Language, prefs: SpeechPreferences, ready: Boolean, speech: SpeechEngine,
    onVoice: (Language, String?) -> Unit, onInstall: () -> Unit,
) {
    val availability = remember(ready, lang) { speech.availability(lang) }
    val voices = remember(ready, lang) { speech.voices(lang).sortedWith(compareBy({ !it.offline }, { -it.quality })).take(6) }
    val chosen = prefs.voices[lang.iso639]
    Column(Modifier.padding(top = Spacing.md)) {
        Row(Modifier.padding(start = Spacing.screen, end = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(lang.displayNamePt, style = MaterialTheme.typography.titleSmall, color = Virgil.colors.textPrimary)
                Text(
                    when (availability) {
                        VoiceAvailability.Ready -> if (voices.any { it.offline }) "Voz offline disponível" else "Disponível"
                        VoiceAvailability.MissingData -> "Voz não instalada"
                        VoiceAvailability.NotSupported -> "Não oferecido pelo mecanismo de voz"
                        VoiceAvailability.Unavailable -> "—"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (availability == VoiceAvailability.Ready) Virgil.colors.textSecondary else Virgil.colors.error,
                )
            }
            when (availability) {
                VoiceAvailability.Ready -> TextButton(onClick = {
                    speech.speak(Samples.getValue(lang), lang, prefs.rate, chosen, id = "sample-${lang.iso639}")
                }) { Text("Testar") }
                VoiceAvailability.MissingData, VoiceAvailability.NotSupported -> TextButton(onClick = onInstall) { Text("Instalar voz") }
                VoiceAvailability.Unavailable -> Unit
            }
        }
        if (voices.size > 1) {
            ChipRow {
                QuietChip("Automática", chosen == null) { onVoice(lang, null) }
                voices.forEachIndexed { i, v ->
                    QuietChip("Voz ${i + 1}" + if (v.offline) "" else " (internet)", chosen == v.id) { onVoice(lang, v.id) }
                }
            }
        }
    }
}
