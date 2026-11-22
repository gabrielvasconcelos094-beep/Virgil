package app.virgil.feature.vocabulary

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import app.virgil.core.designsystem.component.SectionLabel
import app.virgil.core.designsystem.theme.ForeignTermStyle
import app.virgil.core.designsystem.theme.Spacing
import app.virgil.core.designsystem.theme.Virgil
import app.virgil.core.designsystem.theme.VirgilFonts
import app.virgil.core.model.Language
import app.virgil.core.model.LearningState
import app.virgil.core.model.VocabularyEntry
import kotlinx.coroutines.flow.Flow
import java.text.DateFormat
import java.util.Date

@Composable
internal fun AddEntryDialog(onDismiss: () -> Unit, onSave: (String, String, Language, String?) -> Unit) {
    var term by rememberSaveable { mutableStateOf("") }
    var translation by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf(Language.EN_GB) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova palavra") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Language.Learnable.forEach { l -> QuietChip(l.iso639.uppercase(), language == l) { language = l } }
                }
                OutlinedTextField(term, { term = it }, label = { Text("Palavra ou expressão") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(translation, { translation = it }, label = { Text("Tradução") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Observação (opcional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = term.isNotBlank() && translation.isNotBlank(), onClick = { onSave(term, translation, language, note) }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Ficha da palavra: termo, tradução, frase original, origem e ações. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntrySheet(
    entryFlow: Flow<VocabularyEntry?>,
    onDismiss: () -> Unit,
    onSave: (Long, String, String?, String?) -> Unit,
    onSetState: (Long, LearningState) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val entry by remember(entryFlow) { entryFlow }.collectAsState(initial = null)
    val e = entry ?: return
    var editing by rememberSaveable { mutableStateOf(false) }
    var translation by rememberSaveable(e.id) { mutableStateOf(e.translation) }
    var lemma by rememberSaveable(e.id) { mutableStateOf(e.lemma.orEmpty()) }
    var note by rememberSaveable(e.id) { mutableStateOf(e.note.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(e.id) { editing = false }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Virgil.colors.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl).navigationBarsPadding().padding(bottom = Spacing.xl),
        ) {
            Text(e.language.displayNamePt.uppercase(), style = MaterialTheme.typography.labelSmall, color = Virgil.colors.secondaryInk)
            Spacer(Modifier.height(Spacing.xs))
            Text(e.term, style = ForeignTermStyle, color = Virgil.colors.textPrimary)
            if (!e.lemma.isNullOrBlank() && e.lemma != e.term) {
                Text("lema: ${e.lemma}", style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary)
            }
            Spacer(Modifier.height(Spacing.sm))
            if (editing) {
                OutlinedTextField(translation, { translation = it }, label = { Text("Tradução") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(lemma, { lemma = it }, label = { Text("Lema") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Observações") }, modifier = Modifier.fillMaxWidth())
            } else {
                Text(e.translation, style = MaterialTheme.typography.titleMedium, color = Virgil.colors.primary)
            }

            e.contextSentence?.let { s ->
                Spacer(Modifier.height(Spacing.lg))
                SectionLabel("No livro", Modifier.padding(start = 0.dp))
                Text(s, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = VirgilFonts.Literata, fontStyle = FontStyle.Italic), color = Virgil.colors.textPrimary)
                e.contextTranslation?.let {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textSecondary)
                }
            }
            if (!editing && !e.note.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.lg))
                SectionLabel("Observações", Modifier.padding(start = 0.dp))
                Text(e.note!!, style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textPrimary)
            }
            Spacer(Modifier.height(Spacing.lg))
            val origin = buildList {
                e.bookTitle?.let { add(it) }
                e.chapterIndex?.let { add("capítulo ${it + 1}") }
                add(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(e.createdAt)))
                if (e.lookupCount > 1) add("${e.lookupCount} consultas")
            }.joinToString(" · ")
            Text(origin, style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary)
            Spacer(Modifier.height(Spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(LearningState.New, LearningState.Learning, LearningState.Known).forEach { s ->
                    QuietChip(s.labelPt, e.state == s) { onSetState(e.id, s) }
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Row {
                if (editing) {
                    TextButton(onClick = { onSave(e.id, translation, lemma, note); editing = false }, enabled = translation.isNotBlank()) { Text("Salvar") }
                    TextButton(onClick = { editing = false }) { Text("Cancelar") }
                } else {
                    TextButton(onClick = { editing = true }) { Text("Editar") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { confirmDelete = true }) { Text("Excluir", color = Virgil.colors.error) }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir “${e.term}”?") },
            text = { Text("A palavra e seu histórico de revisões serão removidos deste aparelho.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(e.id) }) { Text("Excluir", color = Virgil.colors.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

