package app.virgil.feature.reader

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.virgil.core.data.PreferencesRepository
import app.virgil.core.data.VocabularyRepository
import app.virgil.core.designsystem.component.ReadingControls
import app.virgil.core.designsystem.component.VirgilDivider
import app.virgil.core.designsystem.theme.Spacing
import app.virgil.core.designsystem.theme.Virgil
import app.virgil.core.designsystem.theme.VirgilFonts
import app.virgil.core.model.ReadingPreferences
import app.virgil.engine.books.BookStore
import app.virgil.engine.translation.Translator
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun ReaderRoute(
    bookId: Long,
    store: BookStore,
    vocabulary: VocabularyRepository,
    preferences: PreferencesRepository,
    translator: Translator?,
    imageRoot: (String) -> String,
    onBack: () -> Unit,
    onOpenTranslationSettings: () -> Unit,
) {
    val vm: ReaderViewModel = viewModel(key = "reader-$bookId", factory = viewModelFactory { initializer { ReaderViewModel(bookId, store, vocabulary, preferences, translator) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()

    // Sessão de leitura acompanha o ciclo de vida (tela visível = lendo).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> vm.onStartReading()
                Lifecycle.Event.ON_STOP -> vm.onStopReading()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    val p = prefs ?: return
    ReaderScreen(
        state = state,
        reading = p.reading,
        vm = vm,
        bookmarked = state.doc != null && vm.currentPosition()?.let { (c, o) -> vm.bookmarkNear(c, o) } != null,
        bookmarks = bookmarks.map { BookmarkRow(it.id, it.chapterIndex, it.charOffset, it.excerpt) },
        imagePath = imageRoot,
        onBack = onBack,
        onOpenTranslationSettings = onOpenTranslationSettings,
        theme = p.theme,
    )
}

data class BookmarkRow(val id: Long, val chapterIndex: Int, val charOffset: Int, val excerpt: String)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreen(
    state: ReaderUiState,
    reading: ReadingPreferences,
    vm: ReaderViewModel,
    bookmarked: Boolean,
    bookmarks: List<BookmarkRow>,
    imagePath: (String) -> String,
    onBack: () -> Unit,
    onOpenTranslationSettings: () -> Unit,
    theme: app.virgil.core.model.ThemeMode,
) {
    var sheet by rememberSaveable { mutableStateOf<String?>(null) } // "toc" | "type"
    val listState = rememberLazyListState()
    Immersive(hidden = !state.controlsVisible && sheet == null)

    BackHandler(enabled = state.lookup != null || state.controlsVisible) {
        if (state.lookup != null) vm.clearSelection() else vm.hideControls()
    }

    // Restaura a posição pedida (abertura, sumário, marcador).
    LaunchedEffect(state.scrollTarget) {
        val t = state.scrollTarget ?: return@LaunchedEffect
        listState.scrollToItem(t.blockIndex + 1)
        if (t.fraction > 0f) {
            val size = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == t.blockIndex + 1 }?.size ?: 0
            if (size > 0) listState.scrollToItem(t.blockIndex + 1, (size * t.fraction).toInt())
        }
        vm.scrollConsumed()
    }
    // Informa a posição visível (o view model salva com debounce).
    LaunchedEffect(listState, state.doc) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .filter { state.scrollTarget == null }
            .collect { (index, offset) ->
                if (index == 0) { vm.onVisible(0, 0f); return@collect }
                val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                val blocks = state.doc?.blocks?.size ?: 0
                if (index - 1 < blocks) vm.onVisible(index - 1, if (info == null || info.size == 0) 0f else (offset.toFloat() / info.size).coerceIn(0f, 1f))
                else vm.reachedEnd()
            }
    }

    Box(
        Modifier.fillMaxSize().background(Virgil.colors.background)
            .pointerInput(Unit) { detectTapGestures(onTap = { vm.onBackgroundTap() }) },
    ) {
        val doc = state.doc
        when {
            state.error != null -> Text(state.error, color = Virgil.colors.textSecondary, modifier = Modifier.align(Alignment.Center).padding(32.dp))
            doc == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Virgil.colors.secondary, strokeWidth = 2.dp)
            else -> {
                val top = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
                val bottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
                ChapterContent(
                    doc = doc, chapterTitle = state.chapterTitle, language = state.language, prefs = reading,
                    selection = state.lookup?.selection, listState = listState,
                    contentPadding = PaddingValues(top = top + 8.dp, bottom = bottom + if (state.lookup != null) 260.dp else 24.dp),
                    gestures = remember(vm) { TextGestures(vm::tapText, vm::onBackgroundTap, vm::startExpression, vm::extendExpression, vm::finishExpression) },
                    imagePath = imagePath, nextChapterTitle = state.nextChapterTitle, isLastChapter = state.isLastChapter,
                    onNextChapter = vm::nextChapter,
                )
            }
        }

        // Linha de progresso discreta, sempre visível no rodapé.
        if (!state.controlsVisible && state.lookup == null && doc != null) {
            Text(
                "${(state.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall, color = Virgil.colors.textSecondary.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp),
            )
        }

        AnimatedVisibility(state.controlsVisible, enter = fadeIn(tween(160)), exit = fadeOut(tween(120)), modifier = Modifier.align(Alignment.TopCenter)) {
            TopControls(state, bookmarked, onBack, onToc = { sheet = "toc" }, onBookmark = vm::toggleBookmarkHere)
        }
        AnimatedVisibility(state.controlsVisible, enter = fadeIn(tween(160)), exit = fadeOut(tween(120)), modifier = Modifier.align(Alignment.BottomCenter)) {
            BottomControls(state, onPrev = vm::previousChapter, onNext = vm::nextChapter, onType = { sheet = "type" })
        }
        AnimatedVisibility(
            state.lookup != null,
            enter = slideInVertically(tween(180)) { it / 2 } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(140)) { it / 2 } + fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            state.lookup?.let { l ->
                LookupCard(l, onKind = vm::expand, onSave = vm::saveSelection, onClose = vm::clearSelection, onOpenSettings = onOpenTranslationSettings)
            }
        }
    }

    when (sheet) {
        "toc" -> ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Virgil.colors.surface) {
            TocSheet(state, bookmarks, onJump = { c, o -> sheet = null; vm.goToChapter(c, o); vm.hideControls() }, onDeleteBookmark = vm::deleteBookmark)
        }
        "type" -> ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Virgil.colors.surface) {
            Column(Modifier.navigationBarsPadding().padding(bottom = Spacing.lg)) {
                ReadingControls(reading, vm::setReading, theme, vm::setTheme)
            }
        }
    }
}

@Composable
private fun TopControls(state: ReaderUiState, bookmarked: Boolean, onBack: () -> Unit, onToc: () -> Unit, onBookmark: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Virgil.colors.background.copy(alpha = 0.97f)).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar à biblioteca") }
            Column(Modifier.weight(1f)) {
                Text(state.title, style = MaterialTheme.typography.titleSmall.copy(fontFamily = VirgilFonts.Literata), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Virgil.colors.textPrimary)
                state.chapterTitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Virgil.colors.textSecondary) }
            }
            IconButton(onClick = onBookmark) {
                Icon(if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, if (bookmarked) "Remover marcador" else "Marcar esta página", tint = if (bookmarked) Virgil.colors.accent else Virgil.colors.textPrimary)
            }
            IconButton(onClick = onToc) { Icon(Icons.AutoMirrored.Outlined.List, "Sumário e marcadores") }
        }
        VirgilDivider()
    }
}

@Composable
private fun BottomControls(state: ReaderUiState, onPrev: () -> Unit, onNext: () -> Unit, onType: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Virgil.colors.background.copy(alpha = 0.97f)).navigationBarsPadding()) {
        VirgilDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev, enabled = state.chapterIndex > 0) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Capítulo anterior") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Capítulo ${state.chapterIndex + 1} de ${state.chapterCount}", style = MaterialTheme.typography.labelLarge, color = Virgil.colors.textPrimary)
                Text("${(state.progress * 100).toInt()}% do livro", style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary)
            }
            IconButton(onClick = onType) { Icon(Icons.Outlined.TextFields, "Tipografia e tema") }
            IconButton(onClick = onNext, enabled = !state.isLastChapter) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Próximo capítulo") }
        }
    }
}

@Composable
private fun TocSheet(state: ReaderUiState, bookmarks: List<BookmarkRow>, onJump: (Int, Int) -> Unit, onDeleteBookmark: (Long) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        TabRow(
            selectedTabIndex = tab, containerColor = Virgil.colors.surface, contentColor = Virgil.colors.primary,
            indicator = { pos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), height = 2.dp, color = Virgil.colors.primary) },
            divider = { VirgilDivider() },
        ) {
            Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Sumário") })
            Tab(tab == 1, onClick = { tab = 1 }, text = { Text("Marcadores (${bookmarks.size})") })
        }
        LazyColumn(Modifier.fillMaxWidth().height(460.dp)) {
            if (tab == 0) {
                items(state.toc) { t ->
                    val current = t.chapterIndex == state.chapterIndex
                    Text(
                        t.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = VirgilFonts.Literata),
                        color = if (current) Virgil.colors.primary else Virgil.colors.textPrimary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clickable { onJump(t.chapterIndex, t.charOffset) }
                            .padding(start = Spacing.screen + (16 * t.depth).dp, end = Spacing.screen, top = Spacing.md, bottom = Spacing.md),
                    )
                }
            } else {
                if (bookmarks.isEmpty()) {
                    item { Text("Nenhum marcador. Toque no ícone de marcador no topo para guardar uma passagem.", style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textSecondary, modifier = Modifier.padding(Spacing.screen)) }
                }
                items(bookmarks, key = { it.id }) { b ->
                    Row(Modifier.fillMaxWidth().clickable { onJump(b.chapterIndex, b.charOffset) }.padding(start = Spacing.screen, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Capítulo ${b.chapterIndex + 1}", style = MaterialTheme.typography.labelSmall, color = Virgil.colors.secondaryInk)
                            Text(b.excerpt, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = VirgilFonts.Literata), maxLines = 2, overflow = TextOverflow.Ellipsis, color = Virgil.colors.textPrimary)
                        }
                        IconButton(onClick = { onDeleteBookmark(b.id) }) { Icon(Icons.Outlined.Delete, "Remover marcador", tint = Virgil.colors.textSecondary) }
                    }
                }
            }
        }
    }
}

/** Oculta as barras do sistema durante a leitura; reaparecem com os controles ou com um gesto da borda. */
@Composable
private fun Immersive(hidden: Boolean) {
    val view = LocalView.current
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(hidden) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

