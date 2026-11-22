package app.virgil.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.virgil.core.designsystem.theme.Virgil

/**
 * Gráfico de barras de uma série (ex.: minutos lidos por dia). Diretrizes seguidas: uma cor
 * sequencial, barras finas com pontas de 4 dp arredondadas só no topo e ancoradas na base, 2 dp entre
 * barras, eixo recessivo, rótulos seletivos (primeiro e último dia), toque para ver o valor de uma
 * barra e descrição acessível com todos os valores (equivale à visão em tabela).
 */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    format: (Float) -> String,
    summary: String,
    modifier: Modifier = Modifier,
    height: Int = 96,
) {
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val bar = Virgil.colors.primary
    val selectedBar = Virgil.colors.accent
    val baseline = Virgil.colors.divider
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1e-6f)
    val description = summary + ". " + labels.zip(values).joinToString("; ") { (l, v) -> "$l: ${format(v)}" }
    Column(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        // Linha de leitura: o valor tocado, ou o resumo do período.
        Text(
            selected?.let { "${labels[it]}: ${format(values[it])}" } ?: summary,
            style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Canvas(
            Modifier.fillMaxWidth().height(height.dp).pointerInput(values) {
                detectTapGestures { pos ->
                    val slot = size.width / values.size.coerceAtLeast(1)
                    val i = (pos.x / slot).toInt().coerceIn(0, values.lastIndex)
                    selected = if (selected == i) null else i
                }
            },
        ) {
            if (values.isEmpty()) return@Canvas
            val gap = 2.dp.toPx()
            val slot = size.width / values.size
            val w = (slot - gap).coerceAtLeast(1f)
            val r = 4.dp.toPx().coerceAtMost(w / 2)
            values.forEachIndexed { i, v ->
                val x = i * slot + gap / 2
                if (v <= 0f) {
                    drawRect(baseline, Offset(x, size.height - 2.dp.toPx()), Size(w, 2.dp.toPx()))
                } else {
                    val h = (v / max * size.height).coerceAtLeast(r * 2)
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = x, top = size.height - h, right = x + w, bottom = size.height,
                                topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r),
                                bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
                            ),
                        )
                    }
                    drawPath(path, if (selected == i) selectedBar else bar)
                }
            }
            drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(labels.firstOrNull().orEmpty(), style = MaterialTheme.typography.labelSmall, color = Virgil.colors.textSecondary, modifier = Modifier.weight(1f))
            Text(labels.lastOrNull().orEmpty(), style = MaterialTheme.typography.labelSmall, color = Virgil.colors.textSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}
