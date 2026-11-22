package app.virgil.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.virgil.core.designsystem.theme.Spacing
import app.virgil.core.designsystem.theme.Virgil
import app.virgil.core.model.CefrLevel
import app.virgil.core.model.CefrRange

/** Cabeçalho de tela editorial: título serifado e subtítulo discreto. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    /** false quando o contêiner (ex.: grade da estante) já aplica a margem lateral. */
    insetStart: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = if (insetStart) Spacing.screen else 0.dp, end = if (insetStart) Spacing.sm else 0.dp, top = Spacing.xl, bottom = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Virgil.colors.textPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(Spacing.xs))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textSecondary)
            }
        }
        trailing?.invoke()
    }
}

/** Rótulo de seção em versaletes discretos. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Virgil.colors.secondaryInk,
        modifier = modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
    )
}

@Composable
fun VirgilDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = Virgil.colors.divider)
}

/** Estado vazio: uma frase calma, sem ilustração infantil. */
@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.xxl, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Virgil.colors.secondary, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(Spacing.lg))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Virgil.colors.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.sm))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp))
        if (action != null) {
            Spacer(Modifier.height(Spacing.xl))
            action()
        }
    }
}

/** Selo de nível CEFR estimado. Contorno fino: informação, não medalha. */
@Composable
fun LevelBadge(range: CefrRange?, modifier: Modifier = Modifier) {
    val label = range?.label ?: "—"
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, Virgil.colors.secondary.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Virgil.colors.secondaryInk)
    }
}

/**
 * Mapa de dificuldade por capítulo: uma faixa de barras finas, cada uma com a altura
 * proporcional ao nível estimado. Discreto o bastante para caber sob o título do livro.
 */
@Composable
fun DifficultyStrip(levels: List<CefrLevel?>, modifier: Modifier = Modifier, height: Int = 28) {
    Row(
        modifier.fillMaxWidth().height(height.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        levels.forEach { level ->
            val fraction = level?.let { (it.ordinal + 1) / 6f } ?: 0.08f
            val color = when {
                level == null -> Virgil.colors.divider
                level >= CefrLevel.C1 -> Virgil.colors.accent
                level >= CefrLevel.B2 -> Virgil.colors.primary
                else -> Virgil.colors.secondary
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(color),
            )
        }
    }
}

/** Barra de progresso fina, usada sob capas e em estatísticas. */
@Composable
fun ThinProgress(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Virgil.colors.divider)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Virgil.colors.primary))
    }
}
