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
import app.virgil.core.designsystem.component.SectionLabel
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
fun SettingsRoute(prefsRepo: PreferencesRepository, profileRepo: ProfileRepository, onBack: () -> Unit, onOpenLicenses: () -> Unit) {
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
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Spacing.xxl)) {
        Row(Modifier.padding(start = Spacing.xs, top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") }
            Text("Configurações", style = MaterialTheme.typography.headlineSmall, color = Virgil.colors.textPrimary)
        }

        SectionLabel("Aparência")
        ChipRow {
            listOf(ThemeMode.System to "Automático", ThemeMode.Light to "Claro", ThemeMode.Dark to "Escuro").forEach { (m, label) ->
                Chip(label, prefs.theme == m) { onTheme(m) }
            }
        }

        Divider()
        SectionLabel("Leitura")
        ReadingPreview(prefs.reading)
        Spacer(Modifier.height(Spacing.md))
        ChipRow {
            ReadingFont.entries.forEach { f -> Chip(f.labelPt, prefs.reading.font == f) { onReading(prefs.reading.copy(font = f)) } }
        }
        val r = prefs.reading
        LabeledSlider("Tamanho da letra", "${r.fontSizeSp.toInt()} pt", r.fontSizeSp, 14f..30f, 15) { onReading(r.copy(fontSizeSp = it)) }
        LabeledSlider("Entrelinha", "%.2f".format(r.lineHeight), r.lineHeight, 1.2f..2.0f, 15) { onReading(r.copy(lineHeight = it)) }
        LabeledSlider("Espaço entre parágrafos", "%.1f".format(r.paragraphSpacingEm), r.paragraphSpacingEm, 0f..1.6f, 15) { onReading(r.copy(paragraphSpacingEm = it)) }
        LabeledSlider("Margens", "${r.horizontalMarginDp} dp", r.horizontalMarginDp.toFloat(), 12f..48f, 8) { onReading(r.copy(horizontalMarginDp = it.toInt())) }
        ChipRow {
            Chip("Alinhado à esquerda", r.align == TextAlign.Start) { onReading(r.copy(align = TextAlign.Start)) }
            Chip("Justificado", r.align == TextAlign.Justify) { onReading(r.copy(align = TextAlign.Justify)) }
        }
        SwitchRow("Hifenização", "Quebra palavras longas no fim da linha", r.hyphenate) { onReading(r.copy(hyphenate = it)) }

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
                    Chip("—", prof.level == null) { onLevel(prof.language, null) }
                    CefrLevel.entries.forEach { lvl -> Chip(lvl.label, prof.level == lvl) { onLevel(prof.language, lvl) } }
                }
            }
        }

        Divider()
        SectionLabel("Vocabulário")
        SwitchRow("Salvar consultas automaticamente", "Palavras e expressões consultadas no leitor entram no vocabulário", prefs.autoSaveLookups, onAutoSave)

        Divider()
        SectionLabel("Tradução")
        Text(
            "Nenhum modelo de tradução instalado. O Virgil traduz inteiramente no aparelho: o modelo é baixado uma única vez e depois funciona sem internet.",
            style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.screen),
        )

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
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) { content() }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Virgil.colors.primary, selectedLabelColor = Virgil.colors.onPrimary, labelColor = Virgil.colors.textSecondary,
        ),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = Virgil.colors.divider),
    )
}

@Composable
private fun LabeledSlider(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    // Estado local durante o arraste; persiste só ao soltar (evita uma escrita no DataStore por quadro).
    var local by remember(current) { mutableStateOf(current) }
    Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textPrimary, modifier = Modifier.weight(1f))
            Text(if (local == current) value else "…", style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary)
        }
        Slider(
            value = local, onValueChange = { local = it }, onValueChangeFinished = { onChange(local) },
            valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = Virgil.colors.primary, activeTrackColor = Virgil.colors.primary, inactiveTrackColor = Virgil.colors.divider, activeTickColor = Virgil.colors.primary.copy(alpha = 0f), inactiveTickColor = Virgil.colors.divider.copy(alpha = 0f)),
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Virgil.colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary)
        }
        Spacer(Modifier.width(Spacing.md))
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Virgil.colors.primary, checkedThumbColor = Virgil.colors.onPrimary))
    }
}

@Composable
private fun Divider() = VirgilDivider(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.lg))

