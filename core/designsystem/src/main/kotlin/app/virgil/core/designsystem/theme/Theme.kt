package app.virgil.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp
import app.virgil.core.model.ThemeMode

/** Cantos levemente arredondados: discretos, nunca "bolhas". */
val VirgilShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

private fun VirgilColors.toMaterial() = if (isDark) darkColorScheme(
    primary = primary, onPrimary = onPrimary,
    primaryContainer = surfaceRaised, onPrimaryContainer = textPrimary,
    secondary = secondary, onSecondary = background,
    secondaryContainer = surfaceRaised, onSecondaryContainer = textPrimary,
    tertiary = accent, onTertiary = background,
    background = background, onBackground = textPrimary,
    surface = surface, onSurface = textPrimary,
    surfaceVariant = surfaceRaised, onSurfaceVariant = textSecondary,
    surfaceContainerLowest = surfaceSunken, surfaceContainerLow = background,
    surfaceContainer = surface, surfaceContainerHigh = surfaceRaised, surfaceContainerHighest = surfaceRaised,
    outline = divider, outlineVariant = divider,
    error = error,
) else lightColorScheme(
    primary = primary, onPrimary = onPrimary,
    primaryContainer = surfaceSunken, onPrimaryContainer = primary,
    secondary = secondaryInk, onSecondary = onPrimary,
    secondaryContainer = surfaceSunken, onSecondaryContainer = textPrimary,
    tertiary = accentInk, onTertiary = onPrimary,
    background = background, onBackground = textPrimary,
    surface = surface, onSurface = textPrimary,
    surfaceVariant = surfaceSunken, onSurfaceVariant = textSecondary,
    surfaceContainerLowest = surfaceRaised, surfaceContainerLow = surface,
    surfaceContainer = surface, surfaceContainerHigh = surfaceSunken, surfaceContainerHighest = surfaceSunken,
    outline = divider, outlineVariant = divider,
    error = error,
)

@Composable
fun VirgilTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = if (dark) MidnightLibrary else PaperAndForest
    CompositionLocalProvider(LocalVirgilColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = VirgilTypography,
            shapes = VirgilShapes,
        ) {
            // Ícones e textos fora de um Surface herdam a cor de texto do tema (evita ícones pretos no escuro).
            CompositionLocalProvider(LocalContentColor provides colors.textPrimary, content = content)
        }
    }
}

/** Acesso às cores completas do Virgil (inclui tokens que o Material 3 não modela). */
object Virgil {
    val colors: VirgilColors
        @Composable @ReadOnlyComposable get() = LocalVirgilColors.current
}

/** Escala de espaçamento (múltiplos de 4dp). */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val screen = 20.dp
}
