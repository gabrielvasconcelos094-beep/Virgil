package app.virgil.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Aplica os recortes do sistema às telas de navegação. Nas abas, a barra inferior já consome o
 * inset da barra de navegação do sistema; telas sem barra inferior pedem [includeBottom].
 * O leitor (Fase 2) gerenciará seus próprios insets para o modo imersivo.
 */
@Composable
fun SafeArea(includeBottom: Boolean = false, content: @Composable () -> Unit) {
    val sides = if (includeBottom) WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical else WindowInsetsSides.Horizontal + WindowInsetsSides.Top
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(sides))) { content() }
}
