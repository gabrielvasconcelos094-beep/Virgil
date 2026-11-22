package app.virgil.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.virgil.core.designsystem.component.QuietChip
import app.virgil.core.designsystem.component.VirgilDivider
import app.virgil.core.designsystem.theme.ForeignTermStyle
import app.virgil.core.designsystem.theme.Spacing
import app.virgil.core.designsystem.theme.Virgil
import app.virgil.core.designsystem.theme.VirgilFonts
import app.virgil.core.model.SelectionKind

/**
 * Cartão de consulta: pequeno, ancorado no rodapé, sem cobrir a passagem selecionada
 * (o leitor ganha espaço inferior extra enquanto o cartão está aberto).
 */
@Composable
internal fun LookupCard(
    l: LookupUi,
    onKind: (SelectionKind) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val isTerm = l.selection.kind == SelectionKind.Word || l.selection.kind == SelectionKind.Expression
    Surface(
        color = Virgil.colors.surfaceRaised,
        shape = MaterialTheme.shapes.large.copy(bottomStart = androidx.compose.foundation.shape.CornerSize(0), bottomEnd = androidx.compose.foundation.shape.CornerSize(0)),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth().clickable(enabled = false) {},
    ) {
        Column(Modifier.navigationBarsPadding().padding(start = Spacing.xl, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    QuietChip(if (l.selection.kind == SelectionKind.Expression) "Expressão" else "Palavra", isTerm) { onKind(SelectionKind.Word) }
                    QuietChip("Frase", l.selection.kind == SelectionKind.Sentence) { onKind(SelectionKind.Sentence) }
                    QuietChip("Parágrafo", l.selection.kind == SelectionKind.Paragraph) { onKind(SelectionKind.Paragraph) }
                }
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Fechar", tint = Virgil.colors.textSecondary) }
            }
            Column(Modifier.padding(end = Spacing.lg).heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                if (isTerm) {
                    Text(l.text, style = ForeignTermStyle, color = Virgil.colors.textPrimary)
                } else {
                    Text(l.text, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = VirgilFonts.Literata), color = Virgil.colors.textPrimary)
                }
                Spacer(Modifier.height(Spacing.sm))
                TranslationArea(l.translation, isTerm, onOpenSettings)
                if (isTerm && l.context != null && l.context != l.text) {
                    Spacer(Modifier.height(Spacing.md))
                    VirgilDivider()
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        l.context,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = VirgilFonts.Literata, fontStyle = FontStyle.Italic),
                        color = Virgil.colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(Modifier.padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                if (isTerm) {
                    TextButton(onClick = onSave, enabled = !l.saved) {
                        Icon(if (l.saved) Icons.Outlined.Check else Icons.Outlined.BookmarkAdd, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (l.saved) "No vocabulário" else "Salvar no vocabulário")
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { clipboard.setText(AnnotatedString(l.text)) }) { Icon(Icons.Outlined.ContentCopy, "Copiar texto", tint = Virgil.colors.textSecondary) }
            }
        }
    }
}

@Composable
private fun TranslationArea(t: TranslationUi, isTerm: Boolean, onOpenSettings: () -> Unit) {
    when (t) {
        TranslationUi.NoModel -> Column {
            Text(
                "Tradução local ainda não disponível: nenhum modelo de tradução instalado.",
                style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textSecondary,
            )
            TextButton(onClick = onOpenSettings, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("Ver opções de tradução") }
        }
        TranslationUi.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = Virgil.colors.secondary)
            Spacer(Modifier.size(Spacing.sm))
            Text("traduzindo no aparelho…", style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary)
        }
        is TranslationUi.Ready -> Text(
            t.text,
            style = if (isTerm) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            color = Virgil.colors.primary,
        )
        is TranslationUi.Failed -> Text(t.message, style = MaterialTheme.typography.bodySmall, color = Virgil.colors.error)
    }
}
