package app.virgil.feature.vocabulary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.virgil.core.data.VocabularyFilter
import app.virgil.core.data.VocabularyRepository
import app.virgil.core.designsystem.component.EmptyState
import app.virgil.core.designsystem.component.ScreenHeader
import app.virgil.core.designsystem.component.VirgilDivider
import app.virgil.core.designsystem.theme.Spacing
import app.virgil.core.designsystem.theme.Virgil
import app.virgil.core.designsystem.theme.VirgilFonts
import app.virgil.core.model.Language
import app.virgil.core.model.LearningState
import app.virgil.core.model.VocabularyEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VocabularyUiState(
    val loading: Boolean = true,
    val filter: VocabularyFilter = VocabularyFilter(),
    val entries: List<VocabularyEntry> = emptyList(),
)

class VocabularyViewModel(private val repo: VocabularyRepository) : ViewModel() {
    private val filter = MutableStateFlow(VocabularyFilter())

    val state: StateFlow<VocabularyUiState> = combine(filter, filter.flatMapLatest { repo.entries(it) }) { f, list ->
        VocabularyUiState(loading = false, filter = f, entries = list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VocabularyUiState())

    fun setFilter(f: VocabularyFilter) { filter.value = f }

    fun add(term: String, translation: String, language: Language, note: String?) =
        viewModelScope.launch { repo.addManual(term, translation, language, note) }

    fun save(id: Long, translation: String, lemma: String?, note: String?) =
        viewModelScope.launch { repo.update(id, translation, lemma, note) }

    fun setState(id: Long, s: LearningState) = viewModelScope.launch { repo.setState(id, s) }
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
}

@Composable
fun VocabularyRoute(repo: VocabularyRepository, openEntryId: Long? = null, onEntryConsumed: () -> Unit = {}) {
    val vm: VocabularyViewModel = viewModel(factory = viewModelFactory { initializer { VocabularyViewModel(repo) } })
    val state by vm.state.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf(openEntryId) }
    if (openEntryId != null && selectedId != openEntryId) { selectedId = openEntryId }

    VocabularyScreen(state, vm::setFilter, onAdd = { adding = true }, onSelect = { selectedId = it.id })

    if (adding) {
        AddEntryDialog(onDismiss = { adding = false }, onSave = { t, tr, l, n -> vm.add(t, tr, l, n); adding = false })
    }
    selectedId?.let { id ->
        EntrySheet(
            entryFlow = repo.entry(id),
            onDismiss = { selectedId = null; onEntryConsumed() },
            onSave = vm::save,
            onSetState = vm::setState,
            onDelete = { vm.delete(it); selectedId = null; onEntryConsumed() },
        )
    }
}

@Composable
fun VocabularyScreen(
    state: VocabularyUiState,
    onFilter: (VocabularyFilter) -> Unit,
    onAdd: () -> Unit,
    onSelect: (VocabularyEntry) -> Unit,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    val f = state.filter
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            ScreenHeader(
                title = "Vocabulário",
                subtitle = if (state.entries.isEmpty()) null else "${state.entries.size} ${if (state.entries.size == 1) "entrada" else "entradas"}",
                trailing = {
                    Row {
                        IconButton(onClick = { searching = !searching }) { Icon(Icons.Outlined.Search, "Pesquisar") }
                        IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, "Adicionar palavra") }
                    }
                },
            )
        }
        if (searching) {
            item {
                OutlinedTextField(
                    value = f.query.orEmpty(),
                    onValueChange = { onFilter(f.copy(query = it)) },
                    placeholder = { Text("Palavra ou tradução") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.xs),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Virgil.colors.divider),
                )
            }
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                QuietChip("Todos", f.language == null) { onFilter(f.copy(language = null)) }
                Language.Learnable.forEach { l ->
                    QuietChip(l.displayNamePt.substringBefore(" ("), f.language == l) { onFilter(f.copy(language = if (f.language == l) null else l)) }
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                listOf(LearningState.New, LearningState.Learning, LearningState.Known).forEach { s ->
                    QuietChip(s.labelPt, f.state == s) { onFilter(f.copy(state = if (f.state == s) null else s)) }
                }
                QuietChip("Últimos 7 dias", f.since != null) {
                    onFilter(f.copy(since = if (f.since == null) System.currentTimeMillis() - 7L * 86_400_000 else null))
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
        if (!state.loading && state.entries.isEmpty()) {
            item {
                val filtered = f != VocabularyFilter()
                EmptyState(
                    icon = Icons.Outlined.Bookmarks,
                    title = if (filtered) "Nada com esses filtros" else "Seu vocabulário começa nos livros",
                    message = if (filtered) "Tente remover um dos filtros." else
                        "Cada palavra consultada durante a leitura aparece aqui com a frase em que você a encontrou. Também é possível adicionar palavras manualmente.",
                )
            }
        }
        items(state.entries, key = { it.id }) { e ->
            EntryRow(e, onClick = { onSelect(e) })
            VirgilDivider(Modifier.padding(horizontal = Spacing.screen))
        }
    }
}

@Composable
internal fun QuietChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Virgil.colors.primary,
            selectedLabelColor = Virgil.colors.onPrimary,
            labelColor = Virgil.colors.textSecondary,
        ),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = Virgil.colors.divider),
    )
}

@Composable
private fun EntryRow(e: VocabularyEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.term, style = MaterialTheme.typography.titleMedium.copy(fontFamily = VirgilFonts.Literata), color = Virgil.colors.textPrimary)
            Text(e.translation, style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            e.contextSentence?.let {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = VirgilFonts.Literata, fontStyle = FontStyle.Italic),
                    color = Virgil.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(Spacing.md))
        Box(Modifier.padding(top = 4.dp)) {
            Text(e.language.iso639.uppercase(), style = MaterialTheme.typography.labelSmall, color = Virgil.colors.secondaryInk)
        }
    }
}
