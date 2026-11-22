package app.virgil.engine.translation

import app.virgil.core.database.VirgilDatabase
import app.virgil.core.database.entity.LookupHistoryEntity
import app.virgil.core.database.entity.TranslationCacheEntity
import app.virgil.core.model.Language
import app.virgil.core.model.SelectionKind
import app.virgil.engine.llama.GenerationStatus
import app.virgil.engine.llama.LlamaSession
import app.virgil.engine.llama.LoadOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

data class TranslationRequest(
    val kind: SelectionKind,
    val language: Language,
    /** Termo (palavra/expressão) ou texto (frase/parágrafo) a traduzir. */
    val text: String,
    /** Frase em que o termo aparece (só para palavra/expressão). */
    val context: String? = null,
    val bookId: Long? = null,
    val chapterIndex: Int? = null,
)

sealed interface TranslationEvent {
    data object Started : TranslationEvent
    data class Partial(val text: String) : TranslationEvent
    data class Done(val text: String, val fromCache: Boolean, val millis: Long) : TranslationEvent
    data class Failed(val message: String) : TranslationEvent
}

sealed interface EngineStatus {
    data object NoModel : EngineStatus
    data class Unsupported(val reason: String) : EngineStatus
    data class Idle(val model: ModelSpec) : EngineStatus
    data class Loading(val model: ModelSpec) : EngineStatus
    data class Ready(val model: ModelSpec) : EngineStatus
    data class Failed(val model: ModelSpec, val message: String) : EngineStatus
}

/** Interface usada pela interface do leitor; permite trocar o motor (ou simulá-lo em testes de UI). */
interface Translator {
    val status: StateFlow<EngineStatus>
    fun translate(request: TranslationRequest): Flow<TranslationEvent>
    /** O leitor foi fechado: o modelo pode ser descarregado após um período de inatividade. */
    fun release()
}

/**
 * Motor de tradução local (ADR-0001/0002).
 *
 *  - Um único modelo carregado sob demanda e mantido enquanto houver uso; descarregado após
 *    [idleUnloadMillis] sem consultas depois que o leitor libera o motor, ou imediatamente sob
 *    pressão de memória ([onTrimMemory]).
 *  - Inferência num executor dedicado (nunca na thread principal); uma consulta por vez.
 *  - "A última consulta vence": um novo pedido cancela o que estiver em andamento.
 *  - Cache local (Room) por modelo + operação + idioma + texto + contexto: consultas repetidas
 *    são instantâneas e não gastam bateria.
 */
class LocalTranslationEngine(
    private val db: VirgilDatabase,
    private val models: ModelStore,
    private val nativeLibDir: String?,
    /** Só para testes na JVM: caminho absoluto da biblioteca nativa de host. */
    private val hostLibraryPath: String? = null,
    private val cpuSupported: Boolean = CpuInfo.isSupported(),
    private val threads: Int = CpuInfo.performanceCores(),
    private val idleUnloadMillis: Long = 3 * 60_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) : Translator {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "virgil-inference").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val inference = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private var session: LlamaSession? = null
    private var sessionModel: ModelSpec? = null
    private val generation = AtomicLong(0)
    private var unloadJob: Job? = null

    private val _status = MutableStateFlow<EngineStatus>(EngineStatus.NoModel)
    override val status: StateFlow<EngineStatus> = _status.asStateFlow()

    init {
        if (!cpuSupported) {
            _status.value = EngineStatus.Unsupported("Este aparelho não tem processador ARM de 64 bits, necessário para a tradução local.")
        } else {
            scope.launch { models.installed.collect { spec -> onInstalledChanged(spec) } }
        }
    }

    private suspend fun onInstalledChanged(spec: ModelSpec?) {
        lock.withLock {
            if (spec?.id != sessionModel?.id) unloadLocked()
            _status.value = if (spec == null) EngineStatus.NoModel else (if (session != null) EngineStatus.Ready(spec) else EngineStatus.Idle(spec))
        }
    }

    override fun translate(request: TranslationRequest): Flow<TranslationEvent> = callbackFlow {
        val myGen = generation.incrementAndGet()
        session?.cancel() // a última consulta vence
        unloadJob?.cancel()
        val job = launch {
            val spec = models.installed.value
            if (spec == null) { send(TranslationEvent.Failed("Nenhum modelo de tradução instalado.")); return@launch }
            val key = cacheKey(spec, request)
            withContext(Dispatchers.IO) { db.translationCache().get(key) }?.let { hit ->
                withContext(Dispatchers.IO) { db.translationCache().touch(key, clock()) }
                send(TranslationEvent.Done(hit.result, fromCache = true, millis = 0))
                history(request, hit.result, true, 0)
                return@launch
            }
            send(TranslationEvent.Started)
            val started = clock()
            val outcome = lock.withLock {
                if (generation.get() != myGen) return@withLock null // já foi substituída por outra consulta
                withContext(inference) { runLocked(spec, request, myGen) { partial -> trySend(TranslationEvent.Partial(partial)) } }
            } ?: return@launch
            when (outcome) {
                is Outcome.Text -> {
                    val elapsed = clock() - started
                    withContext(Dispatchers.IO) {
                        db.translationCache().put(TranslationCacheEntity(key, spec.id, request.kind.name, request.language.tag, request.text, outcome.value, null, clock(), clock()))
                        db.translationCache().trim(keep = 20_000)
                    }
                    send(TranslationEvent.Done(outcome.value, fromCache = false, millis = elapsed))
                    history(request, outcome.value, false, elapsed)
                }
                is Outcome.Error -> send(TranslationEvent.Failed(outcome.message))
                Outcome.Cancelled -> {}
            }
        }
        job.invokeOnCompletion { close() }
        awaitClose {
            // O coletor desistiu (outra palavra tocada, cartão fechado): interrompe a geração.
            if (generation.get() == myGen) session?.cancel()
            job.cancel()
        }
    }

    private sealed interface Outcome {
        data class Text(val value: String) : Outcome
        data class Error(val message: String) : Outcome
        data object Cancelled : Outcome
    }

    /** Executa no thread de inferência, com [lock] obtido. */
    private fun runLocked(spec: ModelSpec, r: TranslationRequest, myGen: Long, onPartial: (String) -> Unit): Outcome {
        val s = try {
            ensureLoadedLocked(spec)
        } catch (e: Throwable) {
            val msg = if (e is OutOfMemoryError) "Memória insuficiente para carregar o modelo." else (e.message ?: "Falha ao carregar o modelo.")
            _status.value = EngineStatus.Failed(spec, msg)
            return Outcome.Error(msg)
        }
        if (generation.get() != myGen) return Outcome.Cancelled
        val isTerm = r.kind == SelectionKind.Word || r.kind == SelectionKind.Expression
        val prompt = if (isTerm) Prompts.term(spec.family, r.language, r.context ?: r.text, r.text) else Prompts.text(spec.family, r.language, r.text)
        val maxTokens = if (isTerm) 12 else (s.countTokens(r.text) * 2 + 24).coerceIn(32, 900)
        var lastEmit = 0L
        val result = s.generate(prompt, maxTokens, Prompts.repeatPenalty(spec.family)) { text ->
            if (!isTerm) {
                val now = System.nanoTime()
                if (now - lastEmit > 80_000_000) { onPartial(PostProcess.text(text)); lastEmit = now }
            }
            // Para palavras basta a primeira linha.
            !(isTerm && text.contains('\n') && text.substringBefore('\n').isNotBlank())
        }
        return when (result.status) {
            GenerationStatus.Cancelled -> Outcome.Cancelled
            GenerationStatus.PromptTooLong -> Outcome.Error("Trecho longo demais para traduzir de uma vez. Tente uma frase.")
            GenerationStatus.EvalError -> Outcome.Error("O modelo não conseguiu processar este trecho.")
            GenerationStatus.Ok, GenerationStatus.ContextFull -> {
                val text = if (isTerm) PostProcess.term(result.text, r.text, r.language) else PostProcess.text(result.text)
                if (text.isBlank()) Outcome.Error("O modelo não produziu uma tradução para este trecho.") else Outcome.Text(text)
            }
        }
    }

    private fun ensureLoadedLocked(spec: ModelSpec): LlamaSession {
        session?.let { if (sessionModel?.id == spec.id && !it.isClosed) return it }
        unloadLocked()
        _status.value = EngineStatus.Loading(spec)
        val file: File = models.fileFor(spec)
        LlamaSession.initialize(nativeLibDir, hostLibraryPath)
        val s = LlamaSession.load(file, LoadOptions(contextTokens = spec.contextTokens, threads = threads))
        session = s
        sessionModel = spec
        _status.value = EngineStatus.Ready(spec)
        return s
    }

    private fun unloadLocked() {
        session?.close()
        session = null
        sessionModel?.let { _status.value = EngineStatus.Idle(it) }
        sessionModel = null
    }

    /** Pré-carrega o modelo (ex.: ao abrir o leitor, se o usuário preferir respostas mais rápidas). */
    fun warmUp() {
        val spec = models.installed.value ?: return
        scope.launch { lock.withLock { withContext(inference) { runCatching { ensureLoadedLocked(spec) } } } }
    }

    override fun release() {
        unloadJob?.cancel()
        unloadJob = scope.launch {
            delay(idleUnloadMillis)
            lock.withLock { withContext(inference) { unloadLocked() } }
        }
    }

    /** Chamado pela Application em onTrimMemory: sob pressão, libera o modelo se estiver ocioso. */
    fun onTrimMemory(level: Int) {
        if (level >= 15 /* TRIM_MEMORY_RUNNING_CRITICAL */ || level == 20 /* UI_HIDDEN */) {
            scope.launch { if (lock.tryLock()) try { withContext(inference) { unloadLocked() } } finally { lock.unlock() } }
        }
    }

    private suspend fun history(r: TranslationRequest, result: String, fromCache: Boolean, ms: Long) = withContext(Dispatchers.IO) {
        db.vocabulary().insertHistory(
            LookupHistoryEntity(bookId = r.bookId, chapterIndex = r.chapterIndex, language = r.language.tag, kind = r.kind.name, sourceText = r.text.take(4_000), result = result.take(4_000), fromCache = fromCache, latencyMs = ms, createdAt = clock()),
        )
    }

    companion object {
        /** Chave do cache: modelo + operação + idioma + texto + contexto, normalizados. */
        fun cacheKey(spec: ModelSpec, r: TranslationRequest): String {
            val isTerm = r.kind == SelectionKind.Word || r.kind == SelectionKind.Expression
            val raw = listOf(spec.id, if (isTerm) "term" else "text", r.language.tag, r.text.trim(), if (isTerm) r.context?.trim().orEmpty() else "").joinToString("\u0001")
            return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
