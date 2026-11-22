package app.virgil.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import app.virgil.core.designsystem.theme.Spacing
import app.virgil.core.designsystem.theme.Virgil
import app.virgil.core.model.ReadingFont
import app.virgil.core.model.ReadingPreferences
import app.virgil.core.model.TextAlign
import app.virgil.core.model.ThemeMode

/** Chip discreto: preenchido em verde quando selecionado, contorno fino quando não. */
@Composable
fun QuietChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, modifier = modifier,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Virgil.colors.primary, selectedLabelColor = Virgil.colors.onPrimary,
            labelColor = Virgil.colors.textSecondary, containerColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = Virgil.colors.divider),
    )
}

@Composable
fun ChipRow(modifier: Modifier = Modifier, horizontalPadding: androidx.compose.ui.unit.Dp = Spacing.screen, content: @Composable () -> Unit) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = horizontalPadding, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) { content() }
}

/**
 * Slider com trilho fino (2 dp) e botão pequeno, mais próximo da linguagem editorial do Virgil
 * do que o trilho espesso padrão. O valor só é entregue ao soltar ([onCommit]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThinSlider(
    label: String,
    valueLabel: (Float) -> String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember(value, steps, range) { SliderState(value, steps, range) }
    val primary = Virgil.colors.primary
    val divider = Virgil.colors.divider
    Column(modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textPrimary, modifier = Modifier.weight(1f))
            Text(valueLabel(state.value), style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary)
        }
        Slider(
            state = state,
            onValueChange = { state.value = it },
            onValueChangeFinished = { onCommit(state.value) },
            thumb = { Box(Modifier.size(16.dp).clip(CircleShape).background(primary)) },
            track = { st ->
                val fraction = ((st.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                    val y = size.height / 2
                    drawLine(divider, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(primary, Offset(0f, y), Offset(size.width * fraction, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
            },
        )
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Virgil.colors.textPrimary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary)
        }
        Spacer(Modifier.width(Spacing.md))
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Virgil.colors.primary, checkedThumbColor = Virgil.colors.onPrimary))
    }
}

/** Controles de tipografia de leitura (Configurações e painel "Aa" do leitor). */
@Composable
fun ReadingControls(
    reading: ReadingPreferences,
    onChange: (ReadingPreferences) -> Unit,
    theme: ThemeMode? = null,
    onTheme: ((ThemeMode) -> Unit)? = null,
) {
    val r = reading
    if (theme != null && onTheme != null) {
        ChipRow {
            listOf(ThemeMode.System to "Automático", ThemeMode.Light to "Claro", ThemeMode.Dark to "Escuro").forEach { (m, l) ->
                QuietChip(l, theme == m) { onTheme(m) }
            }
        }
    }
    ChipRow { ReadingFont.entries.forEach { f -> QuietChip(f.labelPt, r.font == f) { onChange(r.copy(font = f)) } } }
    ThinSlider("Tamanho da letra", { "${it.toInt()} pt" }, r.fontSizeSp, 14f..30f, 15, { onChange(r.copy(fontSizeSp = it)) })
    ThinSlider("Entrelinha", { "%.2f".format(it) }, r.lineHeight, 1.2f..2.0f, 15, { onChange(r.copy(lineHeight = it)) })
    ThinSlider("Espaço entre parágrafos", { if (it < 0.05f) "recuo" else "%.1f".format(it) }, r.paragraphSpacingEm, 0f..1.6f, 15, { onChange(r.copy(paragraphSpacingEm = it)) })
    ThinSlider("Margens", { "${it.toInt()} dp" }, r.horizontalMarginDp.toFloat(), 12f..48f, 8, { onChange(r.copy(horizontalMarginDp = it.toInt())) })
    ChipRow {
        QuietChip("Alinhado à esquerda", r.align == TextAlign.Start) { onChange(r.copy(align = TextAlign.Start)) }
        QuietChip("Justificado", r.align == TextAlign.Justify) { onChange(r.copy(align = TextAlign.Justify)) }
    }
    SwitchRow("Hifenização", "Quebra palavras longas no fim da linha", r.hyphenate) { onChange(r.copy(hyphenate = it)) }
}
