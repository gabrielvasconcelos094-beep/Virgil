package app.virgil.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.virgil.core.designsystem.R
import app.virgil.core.model.ReadingFont

/** Fontes embarcadas (todas sob SIL Open Font License 1.1 — ver docs/licenses). */
object VirgilFonts {
    val Literata = FontFamily(
        Font(R.font.literata_regular, FontWeight.Normal),
        Font(R.font.literata_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.literata_semibold, FontWeight.SemiBold),
        Font(R.font.literata_bold, FontWeight.Bold),
        Font(R.font.literata_bold_italic, FontWeight.Bold, FontStyle.Italic),
    )
    val SourceSerif = FontFamily(
        Font(R.font.sourceserif_regular, FontWeight.Normal),
        Font(R.font.sourceserif_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.sourceserif_bold, FontWeight.Bold),
    )
    val EbGaramond = FontFamily(
        Font(R.font.ebgaramond_regular, FontWeight.Normal),
        Font(R.font.ebgaramond_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.ebgaramond_bold, FontWeight.Bold),
    )
    val Inter = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

    fun reading(font: ReadingFont): FontFamily = when (font) {
        ReadingFont.Literata -> Literata
        ReadingFont.SourceSerif -> SourceSerif
        ReadingFont.EbGaramond -> EbGaramond
        ReadingFont.Inter -> Inter
    }
}

private val Ui = VirgilFonts.Inter
private val Display = VirgilFonts.Literata

/**
 * Hierarquia: títulos editoriais em Literata (serifada), interface em Inter.
 * Tamanhos contidos — a hierarquia vem do peso e do espaçamento, não de letras enormes.
 */
val VirgilTypography = Typography(
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.01).em),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Ui, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.02.em),
    labelSmall = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.06.em),
)

/** Estilo para palavras estrangeiras em fichas e pop-ups (sempre serifado, como no livro). */
val ForeignTermStyle = TextStyle(fontFamily = VirgilFonts.Literata, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp)
