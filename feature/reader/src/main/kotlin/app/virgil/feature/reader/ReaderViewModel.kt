package app.virgil.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.virgil.core.data.PreferencesRepository
import app.virgil.core.data.VocabularyRepository
import app.virgil.core.database.entity.BookmarkEntity
import app.virgil.core.model.AppPreferences
import app.virgil.core.model.Language
import app.virgil.core.model.ReadingPreferences
import app.virgil.core.model.SelectionKind
import app.virgil.core.model.ThemeMode
import app.virgil.core.text.TextSegmenter
import app.virgil.engine.books.BookStore
import app.virgil.engine.books.OpenedBook
import app.virgil.engine.books.model.BlockType
import app.virgil.engine.books.model.ChapterDoc
import app.virgil.engine.translation.EngineStatus
import app.virgil.engine.translation.TranslationEvent
import app.virgil.engine.translation.TranslationRequest
import app.virgil.engine.translation.Translator
import app.virgil.engine.speech.IpaSource
import app.virgil.engine.speech.SpeechEngine
import app.virgil.engine.speech.VoiceAvailability
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Seleção ativa: intervalo [start, end) no texto de um bloco. */
data class TextSelection(val blockIndex: Int, val start: Int, val end: Int, val kind: SelectionKind)

/** Estado da tradução no cartão de consulta. A Fase 3 liga o motor local aqui. */
sealed interface TranslationUi {
    data object NoModel : TranslationUi
    data object Working : TranslationUi
    /** O modelo ainda está sendo carregado (primeiro uso, ou aquecimento em andamento). */
    data object LoadingModel : TranslationUi
    data class Streaming(val text: String) : TranslationUi
    data class Ready(val text: String, val fromCache: Boolean) : TranslationUi
    data class Failed(val message: String) : TranslationUi
}

/** Transcrição no cartão: ainda buscando, ausente no dicionário, ou encontrada. */
sealed interface IpaUi {
    data object Loading : IpaUi
    data object Missing : IpaUi
    data class Found(val ipa: String, val note: String?) : IpaUi
}

data class LookupUi(
    val selection: TextSelection,
    val text: String,
    val context: String?,
    val translation: TranslationUi,
    val saved: Boolean,
    val ipa: IpaUi = IpaUi.Missing,
    /** Id do pedido de fala desta seleção (para o botão mostrar "tocando"). */
    val speechId: String = "",
)

/** Pedido de rolagem: ir ao bloco, com fração dentro dele (restaurar posição, sumário, marcador). */
data class ScrollTarget(val blockIndex: Int, val fraction: Float, val token: Long = System.nanoTime())

data class ReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val title: String = "",
    val author: String? = null,
    val language: Language? = null,
    val chapterIndex: Int = 0,
    val chapterCount: Int = 0,
    val chapterTitle: String? = null,
    val doc: ChapterDoc? = null,
    val scrollTarget: ScrollTarget? = null,
    val progress: Float = 0f,
    val controlsVisible: Boolean = false,
    val lookup: LookupUi? = null,
    val toc: List<TocRow> = emptyList(),
    val isLastChapter: Boolean = false,
    val nextChapterTitle: String? = null,
)

data class TocRow(val title: String, val depth: Int, val chapterIndex: Int, val charOffset: Int)

@OptIn(FlowPreview::class)
class ReaderViewModel(
    private val bookId: Long,
    private val store: BookStore,
    private val vocabulary: VocabularyRepository,
    private val prefsRepo: PreferencesRepository,
    private val translator: Translator? = null,
    private val speech: SpeechEngine? = null,
    private val ipa: IpaSource? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private var translateJob: Job? = null
    private var ipaJob: Job? = null

    /** Id do que está sendo falado agora (null = silêncio). */
    val speaking: StateFlow<String?> = speech?.speaking ?: MutableStateFlow(null)
    val voiceReady: StateFlow<Boolean> = speech?.ready ?: MutableStateFlow(false)
    val hasSpeech: Boolean get() = speech != null
    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    val prefs: StateFlow<AppPreferences?> = prefsRepo.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var opened: OpenedBook? = null
    private var segmenter = TextSegmenter(Language.EN_GB)
    private val bookIdFlow = MutableStateFlow<Long?>(null)
    val bookmarks: StateFlow<List<BookmarkEntity>> = bookIdFlow.flatMapLatest { id -> if (id == null) emptyFlow() else store.bookmarks(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Posição visível atual (capítulo, offset no capítulo)
    private val visible = MutableStateFlow<Pair<Int, Int>?>(null)
    private var loadJob: Job? = null

    // Sessão de leitura
    private var sessionStart = 0L
    private var activeMs = 0L
    private var lastPing = 0L
    private var sessionStartChars = 0L
    private var sessionMaxChars = 0L
    private var sessionLookups = 0

    init {
        viewModelScope.launch {
            val book = store.open(bookId)
            if (book == null) {
                _state.update { it.copy(loading = false, error = "Livro não encontrado.") }
                return@launch
            }
            opened = book
            segmenter = TextSegmenter(book.language ?: Language.EN_GB)
            // Modelo de tradução carregado antes do primeiro toque (e prefixo do prompt no KV cache).
            translator?.warmUp(book.language ?: Language.EN_GB)
            // Léxico IPA do idioma do livro já em memória antes do primeiro toque.
            (ipa as? app.virgil.engine.speech.LexiconIpaSource)?.let { src -> launch { src.preload(book.language ?: Language.EN_GB) } }
            bookIdFlow.value = book.book.id
            _state.update {
                it.copy(
                    title = book.book.title, author = book.book.author, language = book.language, chapterCount = book.chapters.size,
                    toc = book.toc.map { t -> TocRow(t.title, t.depth, t.chapterIndex, t.charOffset) },
                )
            }
            val pos = book.position
            loadChapter(pos?.chapterIndex ?: 0, pos?.charOffset ?: 0)
            sessionStartChars = currentGlobalChars()
            sessionMaxChars = sessionStartChars
        }
        // Voltando do modo frase/parágrafo: continua de onde o estudo parou.
        viewModelScope.launch {
            StudyHandoff.position.filterNotNull().collect { p ->
                if (p.bookId != bookId) return@collect
                StudyHandoff.position.value = null
                awaitOpened()
                goToChapter(p.chapterIndex, p.offset)
            }
        }
        // Salva a posição pouco depois de a rolagem parar (e não a cada quadro).
        viewModelScope.launch {
            visible.filterNotNull().debounce(1_200).collect { (c, o) -> opened?.let { store.savePosition(it, c, o) } }
        }
    }

    private suspend fun awaitOpened() { while (opened == null && _state.value.error == null) kotlinx.coroutines.delay(20) }

    // ------------------------------------------------------------------ capítulos

    fun goToChapter(index: Int, offset: Int = 0) {
        val book = opened ?: return
        if (index !in book.chapters.indices) return
        clearSelection()
        loadChapter(index, offset)
    }

    fun nextChapter() = goToChapter(_state.value.chapterIndex + 1)
    fun previousChapter() = goToChapter(_state.value.chapterIndex - 1)

    private fun loadChapter(index: Int, offset: Int) {
        val book = opened ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val ch = book.chapters[index]
            val doc = runCatching { store.chapter(book.book, ch) }.getOrElse { e ->
                _state.update { it.copy(loading = false, error = "Não foi possível abrir o capítulo ${index + 1}.") }
                return@launch
            }
            val blockIndex = doc.blockIndexAt(offset)
            val block = doc.blocks.getOrNull(blockIndex)
            val fraction = if (block == null || block.text.isEmpty()) 0f else ((offset - block.start).toFloat() / block.text.length).coerceIn(0f, 1f)
            _state.update {
                it.copy(
                    loading = false, error = null, chapterIndex = index, doc = doc,
                    chapterTitle = ch.title ?: doc.title,
                    scrollTarget = ScrollTarget(blockIndex, fraction),
                    progress = book.globalProgress(index, offset),
                    isLastChapter = index == book.chapters.lastIndex,
                    nextChapterTitle = book.chapters.getOrNull(index + 1)?.let { n -> n.title ?: "Capítulo ${n.chapterIndex + 1}" },
                )
            }
            visible.value = index to offset
            // Pré-carrega o próximo capítulo no cache, para virar o capítulo sem espera.
            book.chapters.getOrNull(index + 1)?.let { runCatching { store.chapter(book.book, it) } }
        }
    }

    fun scrollConsumed() = _state.update { it.copy(scrollTarget = null) }

    /** Chamado pela interface quando a rolagem muda: bloco visível no topo e fração percorrida dele. */
    fun onVisible(blockIndex: Int, fraction: Float) {
        val s = _state.value
        val doc = s.doc ?: return
        val block = doc.blocks.getOrNull(blockIndex) ?: return
        val offset = block.start + (block.text.length * fraction).toInt()
        visible.value = s.chapterIndex to offset
        opened?.let { b -> _state.update { it.copy(progress = b.globalProgress(s.chapterIndex, offset)) } }
        ping()
        sessionMaxChars = maxOf(sessionMaxChars, currentGlobalChars())
    }

    fun reachedEnd() {
        val s = _state.value
        if (s.isLastChapter) viewModelScope.launch { store.markFinished(bookId) }
    }

    // ------------------------------------------------------------------ controles

    fun onBackgroundTap() {
        if (_state.value.lookup != null) clearSelection() else _state.update { it.copy(controlsVisible = !it.controlsVisible) }
        ping()
    }

    fun hideControls() = _state.update { it.copy(controlsVisible = false) }

    fun setReading(r: ReadingPreferences) = viewModelScope.launch { prefsRepo.update { setReading(r) } }
    fun setTheme(t: ThemeMode) = viewModelScope.launch { prefsRepo.update { setTheme(t) } }

    // ------------------------------------------------------------------ seleção

    /** Toque no texto. Devolve true se havia uma palavra no ponto (o toque é consumido). */
    fun tapText(blockIndex: Int, offset: Int): Boolean {
        val doc = _state.value.doc ?: return false
        val block = doc.blocks.getOrNull(blockIndex) ?: return false
        if (block.type == BlockType.Image || block.type == BlockType.Separator) return false
        val word = segmenter.wordAt(block.text, offset) ?: return false
        val current = _state.value.lookup?.selection
        if (current != null && current.blockIndex == blockIndex && current.start == word.first && current.end == word.last + 1 && current.kind == SelectionKind.Word) {
            clearSelection(); return true
        }
        select(TextSelection(blockIndex, word.first, word.last + 1, SelectionKind.Word))
        ping()
        return true
    }

    private var dragAnchor: Int? = null

    fun startExpression(blockIndex: Int, offset: Int): Boolean {
        val block = _state.value.doc?.blocks?.getOrNull(blockIndex) ?: return false
        val word = segmenter.wordAt(block.text, offset) ?: return false
        dragAnchor = word.first
        select(TextSelection(blockIndex, word.first, word.last + 1, SelectionKind.Word), translate = false)
        return true
    }

    fun extendExpression(blockIndex: Int, offset: Int) {
        val anchor = dragAnchor ?: return
        val sel = _state.value.lookup?.selection ?: return
        if (sel.blockIndex != blockIndex) return
        val block = _state.value.doc?.blocks?.getOrNull(blockIndex) ?: return
        val word = segmenter.wordAt(block.text, offset) ?: return
        val start = minOf(anchor, word.first)
        val anchorWord = segmenter.wordAt(block.text, anchor) ?: return
        val end = maxOf(anchorWord.last + 1, word.last + 1)
        val kind = if (block.text.substring(start, end).contains(' ')) SelectionKind.Expression else SelectionKind.Word
        if (start != sel.start || end != sel.end) select(TextSelection(blockIndex, start, end, kind), translate = false)
    }

    fun finishExpression() {
        dragAnchor = null
        _state.value.lookup?.let { select(it.selection) }
    }

    /** Muda a granularidade da seleção atual: palavra/expressão → frase → parágrafo. */
    fun expand(kind: SelectionKind) {
        val l = _state.value.lookup ?: return
        val block = _state.value.doc?.blocks?.getOrNull(l.selection.blockIndex) ?: return
        val sel = l.selection
        val next = when (kind) {
            SelectionKind.Sentence -> segmenter.sentenceAround(block.text, sel.start, sel.end).let { TextSelection(sel.blockIndex, it.first, it.last + 1, kind) }
            SelectionKind.Paragraph -> TextSelection(sel.blockIndex, 0, block.text.length, kind)
            else -> lastWordSelection?.takeIf { it.blockIndex == sel.blockIndex } ?: return
        }
        select(next)
    }

    private var lastWordSelection: TextSelection? = null

    private fun select(sel: TextSelection, translate: Boolean = true) {
        val block = _state.value.doc?.blocks?.getOrNull(sel.blockIndex) ?: return
        if (sel.kind == SelectionKind.Word || sel.kind == SelectionKind.Expression) lastWordSelection = sel
        val text = block.text.substring(sel.start, sel.end)
        val context = if (sel.kind == SelectionKind.Word || sel.kind == SelectionKind.Expression) {
            segmenter.sentenceAround(block.text, sel.start, sel.end).let { block.text.substring(it.first, it.last + 1) }
        } else null
        translateJob?.cancel()
        val available = translator?.status?.value.let { it != null && it !is EngineStatus.NoModel && it !is EngineStatus.Unsupported }
        val initial = when {
            !translate -> TranslationUi.Working
            available -> TranslationUi.Working
            translator?.status?.value is EngineStatus.Unsupported -> TranslationUi.Failed((translator.status.value as EngineStatus.Unsupported).reason)
            else -> TranslationUi.NoModel
        }
        val isTerm = sel.kind == SelectionKind.Word || sel.kind == SelectionKind.Expression
        val speechId = "${sel.blockIndex}:${sel.start}:${sel.end}"
        val wantsIpa = translate && sel.kind == SelectionKind.Word && ipa != null && prefs.value?.speech?.showIpa != false
        _state.update {
            it.copy(
                lookup = LookupUi(sel, text, context, initial, saved = false, ipa = if (wantsIpa) IpaUi.Loading else IpaUi.Missing, speechId = speechId),
                controlsVisible = false,
            )
        }
        // 1) áudio imediatamente (independe do modelo de tradução); 2) IPA; 3) tradução quando pronta.
        if (translate && isTerm && prefs.value?.speech?.autoPlay != false) speak()
        ipaJob?.cancel()
        if (wantsIpa) {
            val before = wordsBefore(block.text, sel.start)
            ipaJob = viewModelScope.launch {
                val p = runCatching { ipa?.lookup(opened?.language ?: Language.EN_GB, text, before) }.getOrNull()
                val ui = if (p == null) IpaUi.Missing else IpaUi.Found(p.ipa, p.note)
                _state.update { s -> s.copy(lookup = s.lookup?.takeIf { it.selection == sel }?.copy(ipa = ui) ?: s.lookup) }
            }
        }
        if (!translate || !available || translator == null) return
        sessionLookups++
        val book = opened
        val request = TranslationRequest(
            kind = sel.kind, language = book?.language ?: Language.EN_GB, text = text, context = context,
            bookId = book?.book?.id, chapterIndex = _state.value.chapterIndex,
        )
        translateJob = viewModelScope.launch {
            translator.translate(request).collect { ev ->
                val ui = when (ev) {
                    TranslationEvent.Started -> if (translator.status.value is EngineStatus.Loading || translator.status.value is EngineStatus.Idle) TranslationUi.LoadingModel else TranslationUi.Working
                    is TranslationEvent.Partial -> TranslationUi.Streaming(ev.text)
                    is TranslationEvent.Done -> TranslationUi.Ready(ev.text, ev.fromCache)
                    is TranslationEvent.Failed -> TranslationUi.Failed(ev.message)
                }
                _state.update { s -> s.copy(lookup = s.lookup?.takeIf { it.selection == sel }?.copy(translation = ui) ?: s.lookup) }
                // Salvamento automático no vocabulário (desativável nas Configurações).
                if (ev is TranslationEvent.Done && (sel.kind == SelectionKind.Word || sel.kind == SelectionKind.Expression) && prefs.value?.autoSaveLookups == true) {
                    saveSelection()
                }
            }
        }
    }

    // ------------------------------------------------------------------ pronúncia

    /** Disponibilidade de voz para o idioma do livro (o cartão avisa se faltar). */
    fun voiceAvailability(): VoiceAvailability =
        speech?.availability(opened?.language ?: Language.EN_GB) ?: VoiceAvailability.Unavailable

    /** Fala (ou repete) o texto selecionado no idioma do livro; tocar de novo enquanto fala interrompe. */
    fun speak() {
        val l = _state.value.lookup ?: return
        val engine = speech ?: return
        if (speaking.value == l.speechId) { engine.stop(); return }
        val language = opened?.language ?: Language.EN_GB
        val sp = prefs.value?.speech
        engine.speak(l.text, language, rate = sp?.rate ?: 0.9f, voiceId = sp?.voices?.get(language.iso639), id = l.speechId)
    }

    /** Até três palavras antes da seleção, na mesma frase (para escolher entre homógrafos). */
    private fun wordsBefore(text: String, start: Int): List<String> {
        val sentence = segmenter.sentenceAround(text, start, start + 1)
        val from = sentence.first.coerceAtMost(start)
        val chunk = text.substring(from, start)
        return segmenter.words(chunk).takeLast(3).map { chunk.substring(it.first, it.last + 1) }
    }

    fun clearSelection() {
        speech?.stop()
        ipaJob?.cancel()
        translateJob?.cancel()
        lastWordSelection = null
        dragAnchor = null
        _state.update { it.copy(lookup = null) }
    }

    fun saveSelection() {
        val l = _state.value.lookup ?: return
        val book = opened ?: return
        if (l.selection.kind != SelectionKind.Word && l.selection.kind != SelectionKind.Expression) return
        val block = _state.value.doc?.blocks?.getOrNull(l.selection.blockIndex) ?: return
        viewModelScope.launch {
            vocabulary.saveLookup(
                term = l.text, language = book.language ?: Language.EN_GB, kind = l.selection.kind,
                translation = (l.translation as? TranslationUi.Ready)?.text, sentence = l.context, sentenceTranslation = null,
                bookId = book.book.id, chapterIndex = _state.value.chapterIndex, charOffset = block.start + l.selection.start,
            )
            _state.update { s -> s.copy(lookup = s.lookup?.takeIf { it.selection == l.selection }?.copy(saved = true) ?: s.lookup) }
        }
    }

    // ------------------------------------------------------------------ marcadores

    fun toggleBookmarkHere() {
        val (chapter, offset) = visible.value ?: return
        val existing = bookmarkNear(chapter, offset)
        viewModelScope.launch {
            if (existing != null) store.deleteBookmark(existing.id)
            else {
                val doc = _state.value.doc ?: return@launch
                val excerpt = doc.blocks.drop(doc.blockIndexAt(offset)).firstOrNull { it.text.isNotBlank() }?.text ?: (_state.value.chapterTitle ?: "")
                store.addBookmark(bookId, chapter, offset, excerpt)
            }
        }
    }

    fun bookmarkNear(chapter: Int, offset: Int): BookmarkEntity? =
        bookmarks.value.firstOrNull { it.chapterIndex == chapter && kotlin.math.abs(it.charOffset - offset) < 400 }

    fun currentPosition(): Pair<Int, Int>? = visible.value

    fun deleteBookmark(id: Long) = viewModelScope.launch { store.deleteBookmark(id) }

    // ------------------------------------------------------------------ sessão

    fun onStartReading() {
        val now = clock()
        sessionStart = now; lastPing = now; activeMs = 0; sessionLookups = 0
        sessionStartChars = currentGlobalChars(); sessionMaxChars = sessionStartChars
    }

    fun onStopReading() {
        ping()
        val start = sessionStart
        val active = activeMs
        val chars = (sessionMaxChars - sessionStartChars).coerceAtLeast(0)
        val lookups = sessionLookups
        val pos = visible.value
        viewModelScope.launch {
            withContext(NonCancellable) {
                opened?.let { b -> pos?.let { (c, o) -> store.savePosition(b, c, o) } }
                if (start > 0) store.recordSession(bookId, start, active, chars, lookups)
            }
        }
        sessionStart = 0
    }

    /** Conta tempo ativo: intervalos sem interação acima de 2 minutos não contam como leitura. */
    private fun ping() {
        if (sessionStart == 0L) return
        val now = clock()
        activeMs += minOf(now - lastPing, 120_000L)
        lastPing = now
    }

    override fun onCleared() {
        speech?.stop()
        translator?.release()
    }

    private fun currentGlobalChars(): Long {
        val b = opened ?: return 0
        val (c, o) = visible.value ?: return 0
        return (b.chapters.getOrNull(c)?.startChar ?: 0) + o
    }
}
