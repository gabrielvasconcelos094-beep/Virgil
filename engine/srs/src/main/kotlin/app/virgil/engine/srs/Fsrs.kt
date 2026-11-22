package app.virgil.engine.srs

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/** Avaliação do usuário na revisão. */
enum class Rating(val value: Int, val labelPt: String) {
    Again(1, "Esqueci"), Hard(2, "Difícil"), Good(3, "Bom"), Easy(4, "Fácil");
}

enum class Phase { New, Learning, Review, Relearning }

/** Estado de memória de um cartão. Tempos em epoch millis. */
data class CardState(
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val dueAt: Long = 0,
    val lastReviewAt: Long? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
    val phase: Phase = Phase.New,
)

data class ReviewOutcome(val card: CardState, val elapsedDays: Double, val scheduledDays: Double, val retrievability: Double)

/**
 * FSRS-5 (Free Spaced Repetition Scheduler, open-spaced-repetition, licença MIT) — ADR-0005.
 *
 * Modela, para cada cartão, a estabilidade S (dias até a probabilidade de lembrança cair para 90%)
 * e a dificuldade D (1–10). A probabilidade de lembrar após t dias é R(t) = (1 + F·t/S)^C, com
 * C = −0,5 e F = 19/81. O próximo intervalo é escolhido para que R atinja a retenção desejada.
 *
 * Simplificação deliberada: em vez de passos de aprendizagem em minutos, "Esqueci" reapresenta o
 * cartão na mesma sessão (10 min) e as demais respostas agendam em dias inteiros.
 */
class Fsrs(
    private val w: DoubleArray = DEFAULT_WEIGHTS,
    val desiredRetention: Double = 0.9,
    private val maximumIntervalDays: Double = 36_500.0,
) {
    init { require(w.size == 19) { "FSRS-5 usa 19 parâmetros" } }

    fun retrievability(elapsedDays: Double, stability: Double): Double =
        if (stability <= 0) 0.0 else (1 + FACTOR * elapsedDays / stability).pow(DECAY)

    /** Intervalo (dias) para atingir a retenção desejada dada a estabilidade. */
    fun intervalDays(stability: Double): Double {
        val i = stability / FACTOR * (desiredRetention.pow(1 / DECAY) - 1)
        return i.coerceIn(1.0, maximumIntervalDays)
    }

    fun review(card: CardState, rating: Rating, now: Long): ReviewOutcome {
        val elapsed = card.lastReviewAt?.let { max(0.0, (now - it) / DAY_MS.toDouble()) } ?: 0.0
        val g = rating.value
        val (s, d, r) = if (card.phase == Phase.New || card.stability <= 0) {
            Triple(initialStability(g), initialDifficulty(g), 1.0)
        } else {
            val r = retrievability(elapsed, card.stability)
            val nd = nextDifficulty(card.difficulty, g)
            val ns = when {
                elapsed < 1.0 -> shortTermStability(card.stability, g)       // mesma sessão / mesmo dia
                rating == Rating.Again -> forgetStability(card.difficulty, card.stability, r)
                else -> recallStability(card.difficulty, card.stability, r, g)
            }
            Triple(ns, nd, r)
        }
        val stability = s.coerceIn(MIN_STABILITY, maximumIntervalDays)
        val (dueAt, scheduled) = if (rating == Rating.Again) {
            now + AGAIN_DELAY_MS to AGAIN_DELAY_MS / DAY_MS.toDouble()
        } else {
            var days = intervalDays(stability)
            // Garante ordem coerente entre as respostas (Difícil < Bom < Fácil) no primeiro contato.
            if (card.phase == Phase.New && rating == Rating.Easy) days = max(days, 4.0)
            val rounded = days.roundToLong().coerceAtLeast(1)
            now + rounded * DAY_MS to rounded.toDouble()
        }
        val phase = when {
            rating == Rating.Again && card.phase == Phase.Review -> Phase.Relearning
            rating == Rating.Again -> if (card.phase == Phase.New) Phase.Learning else card.phase
            else -> Phase.Review
        }
        val next = CardState(
            stability = stability,
            difficulty = d.coerceIn(1.0, 10.0),
            dueAt = dueAt,
            lastReviewAt = now,
            reps = card.reps + 1,
            lapses = card.lapses + if (rating == Rating.Again && card.phase == Phase.Review) 1 else 0,
            phase = phase,
        )
        return ReviewOutcome(next, elapsed, scheduled, r)
    }

    /** Prévia dos intervalos de cada resposta (rótulos dos botões). */
    fun preview(card: CardState, now: Long): Map<Rating, Long> =
        Rating.entries.associateWith { review(card, it, now).card.dueAt - now }

    private fun initialStability(g: Int) = max(w[g - 1], MIN_STABILITY)
    private fun initialDifficulty(g: Int) = (w[4] - exp(w[5] * (g - 1)) + 1).coerceIn(1.0, 10.0)

    private fun nextDifficulty(d: Double, g: Int): Double {
        val delta = -w[6] * (g - 3)
        val damped = d + delta * (10 - d) / 9            // amortecimento linear (FSRS-5)
        val reverted = w[7] * initialDifficulty(4) + (1 - w[7]) * damped // reversão à média
        return reverted.coerceIn(1.0, 10.0)
    }

    private fun recallStability(d: Double, s: Double, r: Double, g: Int): Double {
        val hard = if (g == 2) w[15] else 1.0
        val easy = if (g == 4) w[16] else 1.0
        return s * (exp(w[8]) * (11 - d) * s.pow(-w[9]) * (exp(w[10] * (1 - r)) - 1) * hard * easy + 1)
    }

    private fun forgetStability(d: Double, s: Double, r: Double): Double {
        val sf = w[11] * d.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) * exp(w[14] * (1 - r))
        return min(sf, s)
    }

    private fun shortTermStability(s: Double, g: Int) = s * exp(w[17] * (g - 3 + w[18]))

    companion object {
        const val DAY_MS = 86_400_000L
        const val AGAIN_DELAY_MS = 10 * 60_000L
        private const val DECAY = -0.5
        private val FACTOR = 0.9.pow(1 / DECAY) - 1 // = 19/81
        private const val MIN_STABILITY = 0.01

        /** Parâmetros padrão publicados do FSRS-5. */
        val DEFAULT_WEIGHTS = doubleArrayOf(
            0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575, 0.1192,
            1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621,
        )

        /** Rótulo curto em português para um intervalo ("10 min", "3 d", "2 meses"). */
        fun formatInterval(ms: Long): String {
            val min = ms / 60_000
            val days = ms / DAY_MS.toDouble()
            return when {
                min < 60 -> "$min min"
                days < 1 -> "${(min / 60)} h"
                days < 30 -> "${days.roundToLong()} d"
                days < 365 -> "${(days / 30).roundToLong()} ${if ((days / 30).roundToLong() == 1L) "mês" else "meses"}"
                else -> "%.1f anos".format(days / 365).replace('.', ',')
            }
        }

    }
}
