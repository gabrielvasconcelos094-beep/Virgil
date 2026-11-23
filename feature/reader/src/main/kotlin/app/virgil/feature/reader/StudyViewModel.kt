package app.virgil.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.virgil.core.data.PreferencesRepository
import app.virgil.core.data.VocabularyRepository
import app.virgil.core.model.AppPreferences
import app.virgil.core.model.Language
import app.virgil.core.model.SelectionKind
import app.virgil.core.text.TextSegmenter
import app.virgil.engine.books.BookStore
import app.virgil.engine.books.OpenedBook
import app.virgil.engine.books.model.BlockType
import app.virgil.engine.books.model.ChapterDoc
import app.virgil.engine.speech.IpaSource
import app.virgil.engine.speech.SpeechEngine
import app.virgil.engine.speech.VoiceAvailability
import app.virgil.engine.translation.EngineStatus
import app.virgil.engine.translation.TranslationEvent
import app.virgil.engine.translation.TranslationRequest
import app.virgil.engine.translation.Translator
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Modo de estudo: uma frase ou um parágrafo por vez, com a tradução logo abaixo. */
enum class StudyMode { Sentence, Paragraph }

/** Unidade de estudo: trecho [start, end) do bloco [blockIndex] do capítulo. */
data class StudyUnit(val blockIndex: Int, val start: Int, val end: Int, val text: String, val heading: Boolean)

data class StudyUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val mode: StudyMode = StudyMode.Sentence,
    val language: Language? = null,
    val chapterIndex: Int = 0,
    val chapterCount: Int = 0,
    val chapterTitle: String? = null,
    val units: List<StudyUnit> = emptyList(),
    val index: Int = 0,
    val translation: TranslationUi = TranslationUi.NoModel,
    val translationVisible: Boolean = true,
    /** Consulta de palavra dentro da unidade (seleção relativa ao texto da unidade; blockIndex = índice da unidade). */
    val lookup: LookupUi? = null,
) {
    val unit: StudyUnit? get() = units.getOrNull(index)
    val hasPrevious: Boolean get() = index > 0 || chapterIndex > 0
    val hasNext: Boolean get() = index < units.lastIndex || chapterIndex < chapterCount - 1
}

/**
 * Modos "frase a frase" e "parágrafo a parágrafo". Reaproveita os mesmos serviços do leitor:
 * segmentação ([TextSegmenter]), o motor de tradução (com seu cache em Room e "a última consulta
 * vence"), a voz, o IPA e o vocabulário. Não substitui o leitor tradicional: abre a partir da
 * posição atual e, ao sair, devolve a posição alcançada.
 *
 * Tradução progressiva: só a unidade visível é traduzida; quando termina, a seguinte é pré-traduzida
 * (vai para o cache), nunca o livro inteiro. Uma consulta de palavra interrompe a pré-tradução;
 * a tradução da unidade é retomada quando o cartão fecha.
 */
@OptIn(FlowPreview::class)
class StudyViewModel(
    private val bookId: Long,
    private val mode: StudyMode,
    private val startChapter: Int?,
    private val startOffset: Int?,
    private val store: BookStore,
    private val vocabulary: VocabularyRepository,
    private val prefsRepo: PreferencesRepository,
    private val translator: Translator? = null,
    private val speech: SpeechEngine? = null,
    private val ipa: IpaSource? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(StudyUiState(mode = mode))
    val state: StateFlow<StudyUiState> = _state.asStateFlow()
    val prefs: StateFlow<AppPreferences?> = prefsRepo.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val speaking: StateFlow<String?> = speech?.speaking ?: MutableStateFlow(null)
    val voiceReady: StateFlow<Boolean> = speech?.ready ?: MutableStateFlow(false)
    val hasSpeech: Boolean get() = speech != null

    private var opened: OpenedBook? = null
    private var segmenter = TextSegmenter(Language.EN_GB)
    private var doc: ChapterDoc? = null
    private var unitJob: Job? = null
    private var prefetchJob: Job? = null
    private var lookupJob: Job? = null
    private var ipaJob: Job? = null
    /** Traduções já obtidas nesta sessão (o cache persistente fica no motor). */
    private val done = HashMap<String, String>()
    private val position = MutableStateFlow<Pair<Int, Int>?>(null)

    init {
        viewModelScope.launch {
            val book = store.open(bookId)
            if (book == null) { _state.update { it.copy(loading = false, error = "Livro não encontrado.") }; return@launch }
            opened = book
            val language = book.language ?: Language.EN_GB
            segmenter = TextSegmenter(language)
            translator?.warmUp(language)
            (ipa as? app.virgil.engine.speech.LexiconIpaSource)?.let { launch { it.preload(language) } }
            _state.update { it.copy(language = book.language, chapterCount = book.chapters.size) }
            val chapter = startChapter ?: book.position?.chapterIndex ?: 0
            val offset = startOffset ?: book.position?.charOffset ?: 0
            loadChapter(chapter, atOffset = offset)
        }
        viewModelScope.launch {
            position.filterNotNull().debounce(800).collect { (c, o) -> opened?.let { store.savePosition(it, c, o) } }
        }
    }

    // ------------------------------------------------------------------ unidades

    /** Divide o capítulo em unidades. Imagens e separadores ficam de fora. */
    internal fun units(doc: ChapterDoc): List<StudyUnit> = doc.blocks.flatMapIndexed { bi, b ->
        if (b.type == BlockType.Image || b.type == BlockType.Separator || b.text.isBlank()) return@flatMapIndexed emptyList()
        val heading = b.type == BlockType.Heading
        if (mode == StudyMode.Paragraph || heading) {
            val t = b.text.trim()
            val s = b.text.indexOf(t)
            listOf(StudyUnit(bi, s, s + t.length, t, heading))
        } else {
            segmenter.sentences(b.text).map { r -> StudyUnit(bi, r.first, r.last + 1, b.text.substring(r.first, r.last + 1), false) }
        }
    }

    private fun loadChapter(index: Int, atOffset: Int? = null, atEnd: Boolean = false) {
        val book = opened ?: return
        val ch = book.chapters.getOrNull(index) ?: return
        viewModelScope.launch {
            val d = runCatching { store.chapter(book.book, ch) }.getOrElse {
                _state.update { it.copy(loading = false, error = "Não foi possível abrir o capítulo ${index + 1}.") }
                return@launch
            }
            doc = d
            val units = units(d)
            val i = when {
                atEnd -> units.lastIndex.coerceAtLeast(0)
                atOffset != null -> units.indexOfLast { d.blocks[it.blockIndex].start + it.start <= atOffset }.coerceAtLeast(0)
                else -> 0
            }
            _state.update {
                it.copy(loading = false, error = null, chapterIndex = index, chapterTitle = ch.title ?: d.title, units = units, index = 0, lookup = null)
            }
            if (units.isEmpty()) {
                _state.update { it.copy(translation = TranslationUi.Failed("Este capítulo não tem texto para estudar.")) }
            } else show(i)
        }
    }

    fun next() {
        val s = _state.value
        if (s.index < s.units.lastIndex) show(s.index + 1)
        else if (s.chapterIndex < s.chapterCount - 1) { stopAll(); loadChapter(s.chapterIndex + 1) }
    }

    fun previous() {
        val s = _state.value
        if (s.index > 0) show(s.index - 1)
        else if (s.chapterIndex > 0) { stopAll(); loadChapter(s.chapterIndex - 1, atEnd = true) }
    }

    private fun show(i: Int) {
        val s = _state.value
        val unit = s.units.getOrNull(i) ?: return
        stopAll()
        _state.update { it.copy(index = i, lookup = null) }
        doc?.let { d -> position.value = s.chapterIndex to d.blocks[unit.blockIndex].start + unit.start }
        translateCurrent()
        if (prefs.value?.speech?.autoPlay == true && studyAutoRead) speakUnit()
    }

    /** Lê em voz alta cada unidade ao avançar (desligado por padrão; alternado na tela). */
    var studyAutoRead: Boolean = false
        private set

    fun toggleAutoRead() { studyAutoRead = !studyAutoRead; if (studyAutoRead) speakUnit() }

    private fun stopAll() {
        unitJob?.cancel(); prefetchJob?.cancel(); lookupJob?.cancel(); ipaJob?.cancel()
        speech?.stop()
    }

    private fun key(u: StudyUnit) = "${_state.value.chapterIndex}:${u.blockIndex}:${u.start}"

    private fun requestFor(u: StudyUnit) = TranslationRequest(
        kind = if (mode == StudyMode.Paragraph) SelectionKind.Paragraph else SelectionKind.Sentence,
        language = opened?.language ?: Language.EN_GB, text = u.text,
        bookId = opened?.book?.id, chapterIndex = _state.value.chapterIndex,
    )

    private fun engineAvailable(): Boolean = translator?.status?.value.let { it != null && it !is EngineStatus.NoModel && it !is EngineStatus.Unsupported }

    /** Traduz a unidade atual (cache primeiro) e, ao terminar, pré-traduz a seguinte. */
    private fun translateCurrent() {
        val s = _state.value
        val u = s.unit ?: return
        done[key(u)]?.let { _state.update { st -> st.copy(translation = TranslationUi.Ready(it, fromCache = true)) }; prefetch(s.index + 1); return }
        val t = translator
        if (t == null || !engineAvailable()) {
            _state.update {
                it.copy(translation = (t?.status?.value as? EngineStatus.Unsupported)?.let { e -> TranslationUi.Failed(e.reason) } ?: TranslationUi.NoModel)
            }
            return
        }
        _state.update { it.copy(translation = TranslationUi.Working) }
        val k = key(u)
        unitJob?.cancel()
        unitJob = viewModelScope.launch {
            t.translate(requestFor(u)).collect { ev ->
                val ui = when (ev) {
                    TranslationEvent.Started -> if (t.status.value is EngineStatus.Loading || t.status.value is EngineStatus.Idle) TranslationUi.LoadingModel else TranslationUi.Working
                    is TranslationEvent.Partial -> TranslationUi.Streaming(ev.text)
                    is TranslationEvent.Done -> { done[k] = ev.text; TranslationUi.Ready(ev.text, ev.fromCache) }
                    is TranslationEvent.Failed -> TranslationUi.Failed(ev.message)
                }
                _state.update { st -> if (st.unit?.let(::key) == k) st.copy(translation = ui) else st }
                if (ev is TranslationEvent.Done) prefetch(_state.value.index + 1)
            }
        }
    }

    private fun prefetch(i: Int) {
        val u = _state.value.units.getOrNull(i) ?: return
        val t = translator ?: return
        if (!engineAvailable() || done.containsKey(key(u)) || _state.value.lookup != null) return
        val k = key(u)
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            t.translate(requestFor(u)).collect { ev -> if (ev is TranslationEvent.Done) done[k] = ev.text }
        }
    }

    fun toggleTranslation() = _state.update { it.copy(translationVisible = !it.translationVisible) }

    // ------------------------------------------------------------------ voz

    fun voiceAvailability(): VoiceAvailability = speech?.availability(opened?.language ?: Language.EN_GB) ?: VoiceAvailability.Unavailable

    private fun unitSpeechId() = "unit:" + (_state.value.unit?.let(::key) ?: "")

    fun isUnitSpeaking(id: String?) = id != null && id == unitSpeechId()

    /** Toca (ou repete) a unidade inteira; tocar enquanto fala interrompe. */
    fun speakUnit() {
        val u = _state.value.unit ?: return
        val engine = speech ?: return
        val id = unitSpeechId()
        if (speaking.value == id) { engine.stop(); return }
        say(u.text, id)
    }

    private fun say(text: String, id: String) {
        val language = opened?.language ?: Language.EN_GB
        val sp = prefs.value?.speech
        speech?.speak(text, language, rate = sp?.rate ?: 0.9f, voiceId = sp?.voices?.get(language.iso639), id = id)
    }

    fun setRate(rate: Float) = viewModelScope.launch { prefsRepo.update { setSpeechRate(rate) } }

    // ------------------------------------------------------------------ palavra

    /** Toque numa palavra da unidade (offset no texto da unidade). Toque na mesma palavra fecha o cartão. */
    fun tapWord(offset: Int) {
        val s = _state.value
        val u = s.unit ?: return
        val w = segmenter.wordAt(u.text, offset) ?: run { closeLookup(); return }
        val sel = TextSelection(s.index, w.first, w.last + 1, SelectionKind.Word)
        if (s.lookup?.selection == sel) { closeLookup(); return }
        val term = u.text.substring(w.first, w.last + 1)
        val context = if (mode == StudyMode.Sentence) u.text else segmenter.sentenceAround(u.text, w.first, w.last + 1).let { u.text.substring(it.first, it.last + 1) }
        val speechId = "word:${s.index}:${w.first}"
        val showIpa = ipa != null && prefs.value?.speech?.showIpa != false
        prefetchJob?.cancel()
        lookupJob?.cancel(); ipaJob?.cancel()
        val initial = if (engineAvailable()) TranslationUi.Working else TranslationUi.NoModel
        _state.update { it.copy(lookup = LookupUi(sel, term, context, initial, saved = false, ipa = if (showIpa) IpaUi.Loading else IpaUi.Missing, speechId = speechId)) }
        if (prefs.value?.speech?.autoPlay != false) say(term, speechId)
        if (showIpa) {
            val before = segmenter.words(u.text.substring(0, w.first)).takeLast(3).map { r -> u.text.substring(r.first, r.last + 1) }
            ipaJob = viewModelScope.launch {
                val p = runCatching { ipa?.lookup(opened?.language ?: Language.EN_GB, term, before) }.getOrNull()
                _state.update { st -> st.copy(lookup = st.lookup?.takeIf { it.selection == sel }?.copy(ipa = p?.let { IpaUi.Found(it.ipa, it.note) } ?: IpaUi.Missing) ?: st.lookup) }
            }
        }
        val t = translator ?: return
        if (!engineAvailable()) return
        lookupJob = viewModelScope.launch {
            t.translate(TranslationRequest(SelectionKind.Word, opened?.language ?: Language.EN_GB, term, context, opened?.book?.id, s.chapterIndex)).collect { ev ->
                val ui = when (ev) {
                    TranslationEvent.Started -> TranslationUi.Working
                    is TranslationEvent.Partial -> TranslationUi.Streaming(ev.text)
                    is TranslationEvent.Done -> TranslationUi.Ready(ev.text, ev.fromCache)
                    is TranslationEvent.Failed -> TranslationUi.Failed(ev.message)
                }
                _state.update { st -> st.copy(lookup = st.lookup?.takeIf { it.selection == sel }?.copy(translation = ui) ?: st.lookup) }
                if (ev is TranslationEvent.Done && prefs.value?.autoSaveLookups == true) saveWord()
            }
            // A consulta da palavra pode ter interrompido a tradução da unidade: retoma.
            if (_state.value.translation !is TranslationUi.Ready) translateCurrent()
        }
    }

    fun speakWord() {
        val l = _state.value.lookup ?: return
        if (speaking.value == l.speechId) { speech?.stop(); return }
        say(l.text, l.speechId)
    }

    fun closeLookup() {
        lookupJob?.cancel(); ipaJob?.cancel()
        if (_state.value.lookup != null) speech?.stop()
        _state.update { it.copy(lookup = null) }
        if (_state.value.translation !is TranslationUi.Ready) translateCurrent()
    }

    /** Salva a palavra com a frase original e, quando já traduzida, a tradução da frase (modo frase). */
    fun saveWord() {
        val s = _state.value
        val l = s.lookup ?: return
        val u = s.unit ?: return
        val book = opened ?: return
        val block = doc?.blocks?.getOrNull(u.blockIndex) ?: return
        val sentenceTranslation = if (mode == StudyMode.Sentence) (s.translation as? TranslationUi.Ready)?.text else null
        viewModelScope.launch {
            vocabulary.saveLookup(
                term = l.text, language = book.language ?: Language.EN_GB, kind = SelectionKind.Word,
                translation = (l.translation as? TranslationUi.Ready)?.text, sentence = l.context, sentenceTranslation = sentenceTranslation,
                bookId = book.book.id, chapterIndex = s.chapterIndex, charOffset = block.start + u.start + l.selection.start,
            )
            _state.update { st -> st.copy(lookup = st.lookup?.takeIf { it.selection == l.selection }?.copy(saved = true) ?: st.lookup) }
        }
    }

    /** Posição alcançada (capítulo, offset), para o leitor tradicional continuar dali. */
    fun currentPosition(): Pair<Int, Int>? = position.value

    /** Saída do modo: entrega a posição ao leitor tradicional (que rola até ela) e a grava. */
    fun leave() {
        stopAll()
        val p = position.value ?: return
        StudyHandoff.position.value = StudyHandoff.Position(bookId, p.first, p.second)
        val b = opened ?: return
        viewModelScope.launch { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { store.savePosition(b, p.first, p.second) } }
    }

    override fun onCleared() { stopAll() }
}

/** Repasse da posição do modo de estudo para o leitor tradicional (mesmo processo, mesma tela de origem). */
object StudyHandoff {
    data class Position(val bookId: Long, val chapterIndex: Int, val offset: Int)
    val position = MutableStateFlow<Position?>(null)
}
