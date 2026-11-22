package app.virgil.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Paletas do Virgil.
 *
 * Paper & Forest (claro) e Midnight Library (escuro) seguem as cores-base do projeto.
 * Refinamentos documentados em docs/design/identidade-visual.md:
 *  - Texto secundário claro escurecido de #77766D (4,0:1) para #6B6A61 (4,8:1) — atinge WCAG AA.
 *  - Sálvia e terracota atuam como preenchimento/acento; quando viram texto usam variantes "ink".
 *  - No escuro o corpo de leitura usa #DEDACF (12:1) em vez de #EAE7DE (13,7:1) para reduzir
 *    o halo (halation) do texto claro sobre fundo escuro em sessões longas.
 */
@Immutable
data class VirgilColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val primary: Color,          // verde-floresta / verde principal
    val onPrimary: Color,
    val secondary: Color,        // sálvia / verde secundário
    val secondaryInk: Color,     // sálvia legível como texto
    val accent: Color,           // terracota
    val accentInk: Color,        // terracota legível como texto
    val textPrimary: Color,
    val textSecondary: Color,
    val textReading: Color,
    val divider: Color,
    val selection: Color,        // fundo da palavra selecionada no leitor
    val savedWordUnderline: Color,
    val error: Color,
    val isDark: Boolean,
)

val PaperAndForest = VirgilColors(
    background = Color(0xFFF4F0E7),
    surface = Color(0xFFFBF8F1),
    surfaceRaised = Color(0xFFFFFDF8),
    surfaceSunken = Color(0xFFEDE8DC),
    primary = Color(0xFF30483C),
    onPrimary = Color(0xFFFBF8F1),
    secondary = Color(0xFF84917A),
    secondaryInk = Color(0xFF66735D),
    accent = Color(0xFFB77955),
    accentInk = Color(0xFF9A5F3E),
    textPrimary = Color(0xFF292D28),
    textSecondary = Color(0xFF6B6A61),
    textReading = Color(0xFF292D28),
    divider = Color(0xFFDDD6C9),
    selection = Color(0x33B77955),
    savedWordUnderline = Color(0x9984917A),
    error = Color(0xFFA4483A),
    isDark = false,
)

val MidnightLibrary = VirgilColors(
    background = Color(0xFF181E1B),
    surface = Color(0xFF232B26),
    surfaceRaised = Color(0xFF2A332D),
    surfaceSunken = Color(0xFF141917),
    primary = Color(0xFF9BAF91),
    onPrimary = Color(0xFF181E1B),
    secondary = Color(0xFF71836D),
    secondaryInk = Color(0xFF8A9C85),
    accent = Color(0xFFCE956F),
    accentInk = Color(0xFFCE956F),
    textPrimary = Color(0xFFEAE7DE),
    textSecondary = Color(0xFFAAA99F),
    textReading = Color(0xFFDEDACF),
    divider = Color(0xFF39423B),
    selection = Color(0x40CE956F),
    savedWordUnderline = Color(0x8071836D),
    error = Color(0xFFD98A7C),
    isDark = true,
)

val LocalVirgilColors = staticCompositionLocalOf { PaperAndForest }
