package app.virgil.engine.llama

import java.io.Closeable
import java.io.File

data class LoadOptions(
    val contextTokens: Int = 2048,
    val threads: Int = 4,
    /** Repack de pesos: mais rápido em algumas CPUs, mas duplica a memória dos pesos (ver benchmark). */
    val repack: Boolean = false,
)

enum class GenerationStatus { Ok, Cancelled, PromptTooLong, EvalError, ContextFull }

data class GenerationResult(
    val text: String,
    val status: GenerationStatus,
    val promptTokens: Int,
    val reusedTokens: Int,
    val generatedTokens: Int,
    val promptMillis: Long,
    val totalMillis: Long,
)

/**
 * Um modelo GGUF carregado com seu contexto. Não é thread-safe: use a partir de um único
 * executor. [cancel] é a exceção — pode ser chamado de qualquer thread.
 */
class LlamaSession private constructor(private var handle: Long, val modelFile: File) : Closeable {

    val isClosed: Boolean get() = handle == 0L

    fun countTokens(text: String): Int = LlamaNative.countTokens(checkOpen(), text)

    /**
     * Gera a continuação de [prompt] (já formatado com o template de chat do modelo).
     * [onText] recebe o texto acumulado a cada trecho; devolver false encerra a geração.
     */
    fun generate(prompt: String, maxTokens: Int, repeatPenalty: Float = 1f, onText: (String) -> Boolean = { true }): GenerationResult {
        val h = checkOpen()
        val sb = StringBuilder()
        val start = System.nanoTime()
        val r = LlamaNative.generate(h, prompt, maxTokens, repeatPenalty) { bytes ->
            sb.append(String(bytes, Charsets.UTF_8))
            onText(sb.toString())
        }
        return GenerationResult(
            text = sb.toString(),
            status = GenerationStatus.entries[r[0].toInt().coerceIn(0, GenerationStatus.entries.lastIndex)],
            promptTokens = r[1].toInt(),
            reusedTokens = r[2].toInt(),
            generatedTokens = r[3].toInt(),
            promptMillis = r[4],
            totalMillis = (System.nanoTime() - start) / 1_000_000,
        )
    }

    /** Interrompe a geração em andamento (seguro de qualquer thread). */
    fun cancel() { if (handle != 0L) LlamaNative.cancel(handle) }

    override fun close() {
        val h = handle
        handle = 0
        if (h != 0L) LlamaNative.free(h)
    }

    private fun checkOpen(): Long = handle.also { check(it != 0L) { "Sessão já encerrada." } }

    companion object {
        @Volatile private var initialized = false

        /**
         * Carrega a biblioteca nativa e as variantes de CPU do ggml. No Android, [nativeLibDir] é
         * applicationInfo.nativeLibraryDir; nos testes de host, [libraryPath] aponta para o .so local.
         */
        @Synchronized
        fun initialize(nativeLibDir: String?, libraryPath: String? = null) {
            if (initialized) return
            if (libraryPath != null) System.load(libraryPath) else System.loadLibrary("virgil_llama")
            LlamaNative.initBackends(nativeLibDir)
            initialized = true
        }

        fun load(file: File, options: LoadOptions = LoadOptions()): LlamaSession {
            check(initialized) { "Chame LlamaSession.initialize() antes." }
            require(file.isFile) { "Modelo não encontrado: ${file.path}" }
            val h = LlamaNative.load(file.absolutePath, options.contextTokens, options.threads, options.repack)
            if (h == 0L) throw IllegalStateException("Não foi possível carregar o modelo (arquivo inválido ou memória insuficiente).")
            return LlamaSession(h, file)
        }
    }
}
