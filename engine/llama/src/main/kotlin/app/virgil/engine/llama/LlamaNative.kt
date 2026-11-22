package app.virgil.engine.llama

/** Assinaturas JNI (implementadas em src/main/cpp/virgil_llama.cpp). */
internal object LlamaNative {
    @JvmStatic external fun initBackends(nativeLibDir: String?)
    @JvmStatic external fun load(path: String, nCtx: Int, nThreads: Int, repack: Boolean): Long
    @JvmStatic external fun free(handle: Long)
    @JvmStatic external fun cancel(handle: Long)
    @JvmStatic external fun countTokens(handle: Long, text: String): Int
    @JvmStatic external fun generate(handle: Long, prompt: String, maxTokens: Int, repeatPenalty: Float, callback: PieceCallback): LongArray
}

/** Recebe trechos de texto gerado (bytes UTF-8 completos). Devolver false interrompe a geração. */
fun interface PieceCallback {
    fun onPiece(bytes: ByteArray): Boolean
}
