package app.virgil.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.virgil.core.designsystem.theme.VirgilFonts
import app.virgil.core.designsystem.theme.VirgilShapes

/** Combinações de capa: fundo + tinta. Derivadas da paleta para manter a estante coerente. */
private val CoverPalettes = listOf(
    Color(0xFF30483C) to Color(0xFFF4F0E7), // floresta
    Color(0xFF5E6B55) to Color(0xFFF4F0E7), // sálvia profunda
    Color(0xFF8C5A3C) to Color(0xFFF7EFE4), // terracota queimada
    Color(0xFF2F3A3F) to Color(0xFFE9E4D8), // tinta azulada
    Color(0xFFE6DFD0) to Color(0xFF30483C), // papel sobre verde
    Color(0xFF4B3F36) to Color(0xFFEFE6D6), // couro
)

/**
 * Capa tipográfica gerada para livros sem capa. Determinística (mesmo título → mesma capa),
 * de modo que a estante não "muda" entre aberturas.
 */
@Composable
fun TypographicCover(
    title: String,
    author: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val (bg, ink) = remember(title) { CoverPalettes[Math.floorMod(title.hashCode(), CoverPalettes.size)] }
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(VirgilShapes.small)
            .background(bg),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Filete duplo à esquerda, evocando a lombada de uma edição em tecido.
            val x = size.width * 0.07f
            drawLine(ink.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5f)
            drawLine(ink.copy(alpha = 0.18f), Offset(x + 6f, 0f), Offset(x + 6f, size.height), strokeWidth = 1f)
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = if (compact) 14.dp else 22.dp, end = if (compact) 8.dp else 14.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = VirgilFonts.Literata,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (compact) 13.sp else 20.sp,
                    lineHeight = if (compact) 16.sp else 25.sp,
                    color = ink,
                    textAlign = TextAlign.Center,
                ),
                maxLines = if (compact) 4 else 5,
                overflow = TextOverflow.Ellipsis,
            )
            if (!author.isNullOrBlank()) {
                Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
                Box(Modifier.width(24.dp).height(1.dp).background(ink.copy(alpha = 0.5f)))
                Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
                Text(
                    text = author.uppercase(),
                    style = TextStyle(
                        fontFamily = VirgilFonts.Inter,
                        fontWeight = FontWeight.Medium,
                        fontSize = if (compact) 7.sp else 10.sp,
                        letterSpacing = 0.12.em,
                        color = ink.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
