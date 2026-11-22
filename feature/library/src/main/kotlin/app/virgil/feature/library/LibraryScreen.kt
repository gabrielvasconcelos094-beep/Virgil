package app.virgil.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.virgil.core.data.LibraryRepository
import app.virgil.core.designsystem.component.EmptyState
import app.virgil.core.designsystem.component.LevelBadge
import app.virgil.core.designsystem.component.ScreenHeader
import app.virgil.core.designsystem.component.ThinProgress
import app.virgil.core.designsystem.component.TypographicCover
import app.virgil.core.designsystem.theme.Spacing
import app.virgil.core.designsystem.theme.Virgil
import app.virgil.core.model.Book
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.Normalizer

data class LibraryUiState(val loading: Boolean = true, val books: List<Book> = emptyList(), val query: String = "", val totalBooks: Int = 0)

class LibraryViewModel(repo: LibraryRepository) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<LibraryUiState> = combine(repo.shelf(), query) { books, q ->
        val needle = q.fold()
        LibraryUiState(
            loading = false,
            books = if (needle.isBlank()) books else books.filter { (it.title + " " + (it.author ?: "")).fold().contains(needle) },
            query = q,
            totalBooks = books.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun search(q: String) { query.value = q }

    private fun String.fold() = Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}

@Composable
fun LibraryRoute(
    repo: LibraryRepository,
    onOpenBook: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    importAction: (@Composable () -> Unit)? = null,
) {
    val vm: LibraryViewModel = viewModel(factory = viewModelFactory { initializer { LibraryViewModel(repo) } })
    val state by vm.state.collectAsStateWithLifecycle()
    LibraryScreen(state, vm::search, onOpenBook, onOpenSettings, importAction)
}

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onSearch: (String) -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    importAction: (@Composable () -> Unit)?,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                ScreenHeader(
                    title = "Biblioteca",
                    subtitle = when (state.totalBooks) {
                        0 -> null
                        1 -> "1 livro"
                        else -> "${state.totalBooks} livros"
                    },
                    modifier = Modifier.padding(start = 0.dp),
                    trailing = {
                        Row {
                            if (state.totalBooks > 0) {
                                IconButton(onClick = { searching = !searching }) { Icon(Icons.Outlined.Search, "Pesquisar") }
                            }
                            IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Configurações") }
                        }
                    },
                )
                if (searching) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onSearch,
                        placeholder = { Text("Título ou autor") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Virgil.colors.divider),
                    )
                }
            }
        }
        if (!state.loading && state.totalBooks == 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    icon = Icons.Outlined.AutoStories,
                    title = "Sua estante está vazia",
                    message = "Importe um livro em EPUB ou TXT do seu aparelho. Tudo fica guardado apenas aqui, sem conta e sem nuvem.",
                    action = importAction,
                )
            }
        }
        items(state.books, key = { it.id }) { book -> BookTile(book, onClick = { onOpenBook(book.id) }) }
    }
}

@Composable
private fun BookTile(book: Book, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        // Capas extraídas do EPUB são exibidas a partir da Fase 2; até lá, capa tipográfica.
        TypographicCover(book.title, book.author, compact = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.sm))
        if (book.progress > 0f) {
            ThinProgress(book.progress)
            Spacer(Modifier.height(Spacing.sm))
        }
        Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Virgil.colors.textPrimary)
        if (!book.author.isNullOrBlank()) {
            Text(book.author!!, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Virgil.colors.textSecondary)
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            book.language?.let {
                Text(it.iso639.uppercase(), style = MaterialTheme.typography.labelSmall, color = Virgil.colors.textSecondary)
                Spacer(Modifier.width(Spacing.sm))
            }
            if (book.difficulty != null) LevelBadge(book.difficulty)
        }
    }
}

