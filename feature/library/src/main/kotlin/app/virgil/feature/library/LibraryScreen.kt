package app.virgil.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.virgil.core.designsystem.component.ChipRow
import app.virgil.core.designsystem.component.EmptyState
import app.virgil.core.designsystem.component.FileImage
import app.virgil.core.designsystem.component.LevelBadge
import app.virgil.core.designsystem.component.QuietChip
import app.virgil.core.designsystem.component.ScreenHeader
import app.virgil.core.designsystem.component.ThinProgress
import app.virgil.core.designsystem.component.TypographicCover
import app.virgil.core.designsystem.theme.Spacing
import app.virgil.core.designsystem.theme.Virgil
import app.virgil.core.designsystem.theme.VirgilShapes
import app.virgil.core.model.Book
import app.virgil.core.model.Language
import app.virgil.engine.books.BookStore
import app.virgil.engine.books.importer.ImportController
import app.virgil.engine.books.importer.ImportJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.UUID

enum class ShelfSort(val labelPt: String) { Recent("Recentes"), Title("Título"), Author("Autor"), Progress("Progresso") }

data class LibraryUiState(
    val loading: Boolean = true,
    val books: List<Book> = emptyList(),
    val query: String = "",
    val totalBooks: Int = 0,
    val sort: ShelfSort = ShelfSort.Recent,
    val language: Language? = null,
    val languages: List<Language> = emptyList(),
    val imports: List<ImportJob> = emptyList(),
    val message: String? = null,
)

class LibraryViewModel(repo: LibraryRepository, private val store: BookStore?, private val imports: ImportController?) : ViewModel() {
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(ShelfSort.Recent)
    private val language = MutableStateFlow<Language?>(null)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<LibraryUiState> = combine(
        repo.shelf(),
        combine(query, sort, language) { q, s, l -> Triple(q, s, l) },
        imports?.jobs ?: MutableStateFlow(emptyList()),
        message,
    ) { books, (q, s, l), jobs, msg ->
        val needle = q.fold()
        val filtered = books
            .filter { l == null || it.language == l }
            .filter { needle.isBlank() || (it.title + " " + (it.author ?: "")).fold().contains(needle) }
        val sorted = when (s) {
            ShelfSort.Recent -> filtered
            ShelfSort.Title -> filtered.sortedBy { it.title.fold() }
            ShelfSort.Author -> filtered.sortedBy { (it.author ?: "~").fold() }
            ShelfSort.Progress -> filtered.sortedByDescending { it.progress }
        }
        LibraryUiState(
            loading = false, books = sorted, query = q, totalBooks = books.size, sort = s, language = l,
            languages = books.mapNotNull { it.language }.distinct(), imports = jobs, message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init { imports?.pruneFinished() }

    fun search(q: String) { query.value = q }
    fun sortBy(s: ShelfSort) { sort.value = s }
    fun filter(l: Language?) { language.value = l }
    fun import(uri: Uri) = viewModelScope.launch { imports?.start(uri)?.let { message.value = it } }
    fun dismiss(id: UUID) { imports?.dismiss(id) }
    fun dismissMessage() { message.value = null }
    fun delete(id: Long) = viewModelScope.launch { store?.delete(id) }

    private fun String.fold() = Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}

/** Tipos aceitos no seletor. "octet-stream" porque muitos gerenciadores não reconhecem .epub. */
private val ImportMimeTypes = arrayOf("application/epub+zip", "text/plain", "application/octet-stream")

@Composable
fun LibraryRoute(
    repo: LibraryRepository,
    store: BookStore,
    imports: ImportController,
    onOpenBook: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onBookDetails: (Long) -> Unit = {},
) {
    val vm: LibraryViewModel = viewModel(factory = viewModelFactory { initializer { LibraryViewModel(repo, store, imports) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::import) }
    var confirmDelete by remember { mutableStateOf<Book?>(null) }
    LibraryScreen(
        state = state,
        onSearch = vm::search,
        onSort = vm::sortBy,
        onFilter = vm::filter,
        onOpenBook = onOpenBook,
        onOpenSettings = onOpenSettings,
        onImport = { picker.launch(ImportMimeTypes) },
        onDelete = { confirmDelete = it },
        onDetails = { onBookDetails(it.id) },
        onDismissImport = vm::dismiss,
        onDismissMessage = vm::dismissMessage,
    )
    confirmDelete?.let { b ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Excluir “${b.title}”?") },
            text = { Text("O livro, sua posição de leitura e os marcadores serão removidos deste aparelho. As palavras salvas no vocabulário são mantidas.") },
            confirmButton = { TextButton(onClick = { vm.delete(b.id); confirmDelete = null }) { Text("Excluir", color = Virgil.colors.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onSearch: (String) -> Unit,
    onSort: (ShelfSort) -> Unit = {},
    onFilter: (Language?) -> Unit = {},
    onOpenBook: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onImport: () -> Unit = {},
    onDelete: (Book) -> Unit = {},
    onDetails: (Book) -> Unit = {},
    onDismissImport: (UUID) -> Unit = {},
    onDismissMessage: () -> Unit = {},
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
                    subtitle = when (state.totalBooks) { 0 -> null; 1 -> "1 livro"; else -> "${state.totalBooks} livros" },
                    insetStart = false,
                    trailing = {
                        Row {
                            if (state.totalBooks > 0) IconButton(onClick = { searching = !searching }) { Icon(Icons.Outlined.Search, "Pesquisar") }
                            IconButton(onClick = onImport) { Icon(Icons.Outlined.Add, "Importar livro") }
                            IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Configurações") }
                        }
                    },
                )
                if (searching) {
                    OutlinedTextField(
                        value = state.query, onValueChange = onSearch, placeholder = { Text("Título ou autor") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Virgil.colors.divider),
                    )
                }
                if (state.totalBooks > 1) {
                    // A grade já aplica a margem lateral.
                    Box {
                        ChipRow(horizontalPadding = 0.dp) {
                            ShelfSort.entries.forEach { s -> QuietChip(s.labelPt, state.sort == s) { onSort(s) } }
                            if (state.languages.size > 1) {
                                state.languages.forEach { l -> QuietChip(l.iso639.uppercase(), state.language == l) { onFilter(if (state.language == l) null else l) } }
                            }
                        }
                    }
                }
                state.message?.let { Notice(it, isError = true, onDismiss = onDismissMessage) }
                state.imports.forEach { job -> ImportRow(job, onDismissImport) }
            }
        }
        if (!state.loading && state.totalBooks == 0 && state.imports.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    icon = Icons.Outlined.AutoStories,
                    title = "Sua estante está vazia",
                    message = "Importe um livro em EPUB ou TXT do seu aparelho. Tudo fica guardado apenas aqui, sem conta e sem nuvem.",
                    action = { OutlinedButton(onClick = onImport) { Text("Importar livro") } },
                )
            }
        }
        items(state.books, key = { it.id }) { book -> BookTile(book, onClick = { onOpenBook(book.id) }, onDetails = { onDetails(book) }, onDelete = { onDelete(book) }) }
    }
}

@Composable
private fun ImportRow(job: ImportJob, onDismiss: (UUID) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        when (job) {
            is ImportJob.Running -> {
                Text("Importando ${job.name}", style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Spacing.xs))
                if (job.progress > 0f) LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth().height(2.dp), color = Virgil.colors.primary, trackColor = Virgil.colors.divider, drawStopIndicator = {})
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = Virgil.colors.primary, trackColor = Virgil.colors.divider)
            }
            is ImportJob.Duplicate -> Notice("“${job.title}” já está na sua biblioteca.") { onDismiss(job.id) }
            is ImportJob.Failed -> Notice("${job.name}: ${job.message}", isError = true) { onDismiss(job.id) }
        }
    }
}

@Composable
private fun Notice(text: String, isError: Boolean = false, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (isError) Virgil.colors.error else Virgil.colors.textSecondary, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Dispensar", tint = Virgil.colors.textSecondary, modifier = Modifier.size(18.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookTile(book: Book, onClick: () -> Unit, onDetails: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = { menu = true }, onLongClickLabel = "Opções do livro")) {
        Box {
            val placeholder: @Composable () -> Unit = { TypographicCover(book.title, book.author, compact = true, modifier = Modifier.fillMaxWidth()) }
            if (book.coverPath != null) {
                FileImage(
                    path = book.coverPath, contentDescription = "Capa de ${book.title}",
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(VirgilShapes.small),
                    targetWidthPx = 360, placeholder = placeholder,
                )
            } else placeholder()
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Abrir") }, onClick = { menu = false; onClick() })
                DropdownMenuItem(text = { Text("Sobre o livro e dificuldade") }, onClick = { menu = false; onDetails() })
                DropdownMenuItem(text = { Text("Excluir", color = Virgil.colors.error) }, onClick = { menu = false; onDelete() })
            }
        }
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
            if (book.finishedAt != null) {
                Spacer(Modifier.width(Spacing.sm))
                Text("lido", style = MaterialTheme.typography.labelSmall, color = Virgil.colors.secondaryInk)
            }
        }
    }
}
