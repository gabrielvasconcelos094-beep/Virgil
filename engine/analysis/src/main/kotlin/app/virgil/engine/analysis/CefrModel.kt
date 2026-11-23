package app.virgil.engine.analysis

import app.virgil.core.model.Language
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.max

/**
 * Modelo de regressão ordinal para o nível CEFR (ADR-0004 v2). Os coeficientes são produzidos por
 * tools/cefr-calibration/fit.py a partir das características calculadas pelo próprio [DifficultyAnalyzer]
 * sobre textos de leitura com nível conhecido, e gravados em src/main/resources/cefr-model.json.
 *
 *   z = (x − média) / desvio      (características padronizadas, limitadas a ±4)
 *   y = intercepto + viés do idioma + Σ (peso compartilhado + ajuste do idioma)·z
 *   nível contínuo = interpolação de y entre os pontos de corte ajustados (A1|A2 … C1|C2)
 */
@Serializable
data class CefrModel(
    val version: Int,
    val features: List<String>,
    val mean: List<Double>,
    val std: List<Double>,
    val weights: List<Double>,
    /** Ajuste dos pesos por idioma (somado a [weights]); idiomas ausentes usam só os pesos compartilhados. */
    val languageWeights: Map<String, List<Double>> = emptyMap(),
    val intercept: Double,
    /** Ajuste por idioma (os sem dados de calibração usam 0). */
    val languageBias: Map<String, Double>,
    /** Cinco limiares de y entre níveis vizinhos (A1|A2, A2|B1, B1|B2, B2|C1, C1|C2). */
    val cuts: List<Double>,
    /** Meia-largura da faixa exibida (em níveis) por idioma; "*" = geral. */
    val halfWidth: Map<String, Double>,
    /** Fração de textos de validação cujo nível real caiu numa faixa de 1, 2 ou 3 níveis. */
    val coverage: Map<String, List<Double>>,
    /** Resumo da validação, para a documentação e a tela "sobre a estimativa". */
    val validation: Map<String, String> = emptyMap(),
) {
    init {
        require(features == Features.NAMES) { "cefr-model.json foi ajustado com outras características" }
        require(cuts.size == 5 && cuts.zipWithNext().all { (a, b) -> a < b })
    }

    fun score(x: DoubleArray, language: Language): DimensionScores {
        val z = DoubleArray(x.size) { i -> ((x[i] - mean[i]) / max(std[i], 1e-9)).coerceIn(-4.0, 4.0) }
        val base = intercept + (languageBias[language.iso639] ?: 0.0)
        val delta = languageWeights[language.iso639]
        val w = DoubleArray(weights.size) { weights[it] + (delta?.get(it) ?: 0.0) }
        fun partial(idx: List<Int>) = toScale(base + idx.sumOf { w[it] * z[it] })
        val y = base + z.indices.sumOf { w[it] * z[it] }
        return DimensionScores(
            vocabulary = partial(Features.GROUPS.getValue("vocabulary")),
            syntax = partial(Features.GROUPS.getValue("syntax")),
            grammar = partial(Features.GROUPS.getValue("grammar")),
            style = partial(Features.GROUPS.getValue("style")),
            combined = toScale(y),
        )
    }

    /** Converte y na escala contínua 1–6, em que o corte k fica em k + 1,5. */
    fun toScale(y: Double): Double {
        val gap = (cuts.last() - cuts.first()) / 4
        val v = when {
            y <= cuts.first() -> 1.5 - (cuts.first() - y) / gap
            y >= cuts.last() -> 5.5 + (y - cuts.last()) / gap
            else -> {
                val k = cuts.indexOfLast { it <= y }
                k + 1.5 + (y - cuts[k]) / (cuts[k + 1] - cuts[k])
            }
        }
        return v.coerceIn(1.0, 6.0)
    }

    fun halfWidth(language: Language): Double = halfWidth[language.iso639] ?: halfWidth.getValue("*")

    fun coverage(language: Language, width: Int): Double {
        val c = coverage[language.iso639] ?: coverage.getValue("*")
        return c[(width - 1).coerceIn(0, c.lastIndex)]
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        val default: CefrModel by lazy {
            val text = CefrModel::class.java.getResourceAsStream("/cefr-model.json")?.bufferedReader()?.readText()
                ?: error("cefr-model.json ausente")
            json.decodeFromString(serializer(), text)
        }
    }
}
