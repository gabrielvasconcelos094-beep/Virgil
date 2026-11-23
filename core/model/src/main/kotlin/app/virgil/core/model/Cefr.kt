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
    HighChallenge("Desafio elevado"),
    FarAbove("Muito acima do nível atual");

    companion object {
        /**
         * Compara o leitor com o centro da faixa estimada (média dos limites): uma faixa larga e
         * incerta não deve, sozinha, empurrar o livro para "muito acima".
         */
        fun of(reader: CefrLevel, book: CefrRange): ReadingFit {
            val center = (book.low.ordinal + book.high.ordinal) / 2.0
            val gap = center - reader.ordinal
            return when {
                gap <= 0.25 -> Comfortable
                gap <= 1.25 -> ModerateChallenge
                gap <= 2.25 -> HighChallenge
                else -> FarAbove
            }
        }
    }
}
