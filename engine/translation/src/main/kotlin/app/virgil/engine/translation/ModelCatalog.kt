package app.virgil.engine.translation

/** Família de prompt: cada modelo tem template de chat e instruções próprios (ADR-0001). */
enum class PromptFamily { TranslateGemma, HyMT }

data class ModelSpec(
    val id: String,
    val displayName: String,
    val summaryPt: String,
    val fileName: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val licenseName: String,
    val licenseUrl: String,
    /** Texto que precisa ser apresentado e aceito antes do download (null = não exige aceite). */
    val licenseNoticePt: String?,
    val family: PromptFamily,
    /** RAM total do aparelho a partir da qual o modelo é recomendado. */
    val recommendedRamBytes: Long,
    val contextTokens: Int = 2048,
)

/**
 * Modelos suportados. Cada entrada fixa a revisão exata no Hugging Face e o SHA-256 do arquivo,
 * verificados no benchmark de novembro/2026 (docs/benchmark). Um único modelo fica instalado.
 */
object ModelCatalog {
    private const val GB = 1024L * 1024 * 1024

    val TranslateGemma4B = ModelSpec(
        id = "translategemma-4b-q4km",
        displayName = "TranslateGemma 4B",
        summaryPt = "Melhor qualidade na consulta de palavras em contexto. Recomendado para aparelhos com 6 GB de RAM ou mais.",
        fileName = "translategemma-4b-it-Q4_K_M.gguf",
        url = "https://huggingface.co/bullerwins/translategemma-4b-it-GGUF/resolve/7c938465a870d8624bcfa98a8e4a3510053c19a8/translategemma-4b-it-Q4_K_M.gguf",
        sha256 = "7f7357c14abd9da4eb200b38b05da502cd6e10d7e1d403fbc9f78c19f3209b72",
        sizeBytes = 2_489_909_312L,
        licenseName = "Gemma Terms of Use",
        licenseUrl = "https://ai.google.dev/gemma/terms",
        licenseNoticePt = "O TranslateGemma é distribuído pelo Google sob os Gemma Terms of Use e a Gemma Prohibited Use Policy " +
            "(ai.google.dev/gemma/terms). Ao instalar, você concorda em usá-lo conforme esses termos. O Virgil executa o modelo " +
            "apenas neste aparelho e não envia nenhum texto para fora dele.",
        family = PromptFamily.TranslateGemma,
        recommendedRamBytes = 6 * GB - 512L * 1024 * 1024, // aparelhos "6 GB" reportam ~5,5 GB
    )

    val HyMT2 = ModelSpec(
        id = "hy-mt2-1.8b-q4km",
        displayName = "Hy-MT2 1.8B (compacto)",
        summaryPt = "Metade do tamanho e cerca de duas vezes mais rápido; menos preciso no sentido de palavras isoladas. Indicado para aparelhos com 4 GB de RAM.",
        fileName = "Hy-MT2-1.8B-Q4_K_M.gguf",
        url = "https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF/resolve/a0c709d9fac510f2c807aa3af52872340dc37a4a/Hy-MT2-1.8B-Q4_K_M.gguf",
        sha256 = "dc5f44fcf1fa496ee7ad725982c0c8c553a4de00259b53af84c4b89fb0c06699",
        sizeBytes = 1_133_080_448L,
        licenseName = "Apache License 2.0",
        licenseUrl = "https://huggingface.co/tencent/Hy-MT2-1.8B/blob/main/LICENSE.txt",
        licenseNoticePt = null,
        family = PromptFamily.HyMT,
        recommendedRamBytes = 0,
    )

    val all = listOf(TranslateGemma4B, HyMT2)

    fun byId(id: String?) = all.firstOrNull { it.id == id }
    fun bySha(sha: String) = all.firstOrNull { it.sha256.equals(sha, ignoreCase = true) }

    /** O mais capaz que cabe na RAM do aparelho. */
    fun recommendedFor(totalRamBytes: Long): ModelSpec =
        all.filter { totalRamBytes >= it.recommendedRamBytes }.maxByOrNull { it.sizeBytes } ?: HyMT2
}
