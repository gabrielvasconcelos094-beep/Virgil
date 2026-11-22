package app.virgil.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.virgil.core.data.LanguageProfile
import app.virgil.core.data.PreferencesRepository
import app.virgil.core.data.ProfileRepository
import app.virgil.core.designsystem.component.ChipRow
import app.virgil.core.designsystem.component.QuietChip
import app.virgil.core.designsystem.component.ReadingControls
import app.virgil.core.designsystem.component.SectionLabel
import app.virgil.core.designsystem.component.SwitchRow
import app.virgil.core.designsystem.component.VirgilDivider
import app.virgil.core.designsystem.theme.Spacing
import app.virgil.core.designsystem.theme.Virgil
import app.virgil.core.designsystem.theme.VirgilFonts
import app.virgil.core.designsystem.theme.VirgilShapes
import app.virgil.core.model.AppPreferences
import app.virgil.core.model.CefrLevel
import app.virgil.core.model.Language
import app.virgil.core.model.ReadingFont
import app.virgil.core.model.ReadingPreferences
import app.virgil.core.model.TextAlign
import app.virgil.core.model.ThemeMode
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextAlign as ComposeTextAlign

@Composable
fun SettingsRoute(prefsRepo: PreferencesRepository, profileRepo: ProfileRepository, onBack: () -> Unit, onOpenLicenses: () -> Unit, onOpenTranslation: () -> Unit = {}, translationSummary: String? = null) {
    val prefs by prefsRepo.preferences.collectAsStateWithLifecycle(initialValue = null)
    val profiles by remember(profileRepo) { profileRepo.profiles() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val p = prefs ?: return
    SettingsScreen(
        prefs = p,
        profiles = profiles,
        onBack = onBack,
        onTheme = { scope.launch { prefsRepo.update { setTheme(it) } } },
        onReading = { scope.launch { prefsRepo.update { setReading(it) } } },
        onAutoSave = { scope.launch { prefsRepo.update { setAutoSave(it) } } },
        onLevel = { l, c -> scope.launch { profileRepo.setLevel(l, c) } },
        onOpenLicenses = onOpenLicenses,
        onOpenTranslation = onOpenTranslation,
        translationSummary = translationSummary,
    )
}

@Composable
fun SettingsScreen(
    prefs: AppPreferences,
    profiles: List<LanguageProfile>,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onReading: (ReadingPreferences) -> Unit,
    onAutoSave: (Boolean) -> Unit,
    onLevel: (Language, CefrLevel?) -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenTranslation: () -> Unit = {},
    translationSummary: String? = null,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Spacing.xxl)) {
        Row(Modifier.padding(start = Spacing.xs, top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") }
            Text("Configurações", style = MaterialTheme.typography.headlineSmall, color = Virgil.colors.textPrimary)
        }

        SectionLabel("Aparência")
        ChipRow {
            listOf(ThemeMode.System to "Automático", ThemeMode.Light to "Claro", ThemeMode.Dark to "Escuro").forEach { (m, label) ->
                QuietChip(label, prefs.theme == m) { onTheme(m) }
            }
        }

        Divider()
        SectionLabel("Leitura")
        ReadingPreview(prefs.reading)
        Spacer(Modifier.height(Spacing.md))
        ReadingControls(prefs.reading, onReading)

        Divider()
        SectionLabel("Seus níveis")
        Text(
            "Autoavaliação usada para comparar seu nível com a dificuldade estimada dos livros. Não é uma certificação.",
            style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.screen),
        )
        profiles.forEach { prof ->
            Column(Modifier.padding(top = Spacing.md)) {
                Text(prof.language.displayNamePt, style = MaterialTheme.typography.titleSmall, color = Virgil.colors.textPrimary, modifier = Modifier.padding(horizontal = Spacing.screen))
                ChipRow {
                    QuietChip("—", prof.level == null) { onLevel(prof.language, null) }
                    CefrLevel.entries.forEach { lvl -> QuietChip(lvl.label, prof.level == lvl) { onLevel(prof.language, lvl) } }
                }
            }
        }

        Divider()
        SectionLabel("Vocabulário")
        SwitchRow("Salvar consultas automaticamente", "Palavras e expressões consultadas no leitor entram no vocabulário", prefs.autoSaveLookups, onAutoSave)

        Divider()
        SectionLabel("Tradução")
        Column(Modifier.fillMaxWidth().clickable(onClick = onOpenTranslation).padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
            Text("Modelo de tradução", style = MaterialTheme.typography.bodyLarge, color = Virgil.colors.textPrimary)
            Text(
                translationSummary ?: "Nenhum modelo instalado. A tradução acontece inteiramente no aparelho; o modelo é baixado uma única vez.",
                style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary,
            )
        }

        Divider()
        SectionLabel("Sobre")
        Text(
            "Virgil guarda livros, palavras e estatísticas apenas neste aparelho. Não há conta, anúncios nem coleta de dados.",
            style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.screen),
        )
        Text(
            "Licenças de código aberto",
            style = MaterialTheme.typography.labelLarge, color = Virgil.colors.primary,
            modifier = Modifier.clickable(onClick = onOpenLicenses).padding(horizontal = Spacing.screen, vertical = Spacing.md),
        )
    }
}

@Composable
private fun ReadingPreview(r: ReadingPreferences) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).clip(VirgilShapes.medium).background(Virgil.colors.surface)
            .padding(horizontal = r.horizontalMarginDp.dp.coerceAtMost(32.dp), vertical = Spacing.lg),
    ) {
        val style = TextStyle(
            fontFamily = VirgilFonts.reading(r.font),
            fontSize = r.fontSizeSp.sp,
            lineHeight = (r.fontSizeSp * r.lineHeight).sp,
            color = Virgil.colors.textReading,
            textAlign = if (r.align == TextAlign.Justify) ComposeTextAlign.Justify else ComposeTextAlign.Start,
            hyphens = if (r.hyphenate) Hyphens.Auto else Hyphens.None,
            lineBreak = LineBreak.Paragraph,
        )
        Text("Mrs Dalloway said she would buy the flowers herself.", style = style)
        Spacer(Modifier.height((r.fontSizeSp * r.paragraphSpacingEm).dp))
        Text("For Lucy had her work cut out for her. The doors would be taken off their hinges.", style = style)
    }
}

@Composable
private fun Divider() = VirgilDivider(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.lg))

