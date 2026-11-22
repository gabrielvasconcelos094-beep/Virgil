package app.virgil.core.model

/** Níveis do Quadro Europeu Comum de Referência. Usados como *estimativa*, nunca como certificação. */
enum class CefrLevel(val label: String) {
    A1("A1"), A2("A2"), B1("B1"), B2("B2"), C1("C1"), C2("C2");

    companion object {
        fun parse(s: String?): CefrLevel? = entries.firstOrNull { it.label.equals(s?.trim(), ignoreCase = true) }
    }
}

/**
 * Faixa estimada de dificuldade (ex.: B2–C1). [confidence] em 0..1 expressa quão
 * consistentes foram os indicadores, não uma probabilidade calibrada estatisticamente.
 */
data class CefrRange(val low: CefrLevel, val high: CefrLevel, val confidence: Float) {
    init {
        require(low <= high) { "Faixa invertida: $low > $high" }
    }
    val label: String get() = if (low == high) low.label else "${low.label}–${high.label}"
}

/** Comparação entre o nível do leitor e a dificuldade do livro. */
enum class ReadingFit(val labelPt: String) {
    Comfortable("Leitura confortável"),
    ModerateChallenge("Desafio moderado"),
    Advanced("Leitura avançada");

    companion object {
        /** Diferença em níveis entre o livro (limite superior) e o leitor. */
        fun of(reader: CefrLevel, book: CefrRange): ReadingFit {
            val gap = book.high.ordinal - reader.ordinal
            return when {
                gap <= 0 -> Comfortable
                gap == 1 -> ModerateChallenge
                else -> Advanced
            }
        }
    }
}
