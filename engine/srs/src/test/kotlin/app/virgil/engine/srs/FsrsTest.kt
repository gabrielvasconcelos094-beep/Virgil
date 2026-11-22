package app.virgil.engine.srs

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FsrsTest {
    private val fsrs = Fsrs()
    private val day = Fsrs.DAY_MS
    private val t0 = 1_700_000_000_000L

    @Test fun `retencao e 90 porcento quando o tempo decorrido e igual a estabilidade`() {
        assertThat(fsrs.retrievability(10.0, 10.0)).isWithin(1e-9).of(0.9)
        assertThat(fsrs.intervalDays(10.0)).isWithin(1e-6).of(10.0)
    }

    @Test fun `primeira revisao ordena os intervalos esqueci menor que dificil menor que bom menor que facil`() {
        val p = fsrs.preview(CardState(), t0)
        assertThat(p[Rating.Again]).isEqualTo(Fsrs.AGAIN_DELAY_MS)
        assertThat(p[Rating.Hard]!!).isAtMost(p[Rating.Good]!!)
        assertThat(p[Rating.Good]!!).isLessThan(p[Rating.Easy]!!)
        assertThat(p[Rating.Good]!! / day).isEqualTo(3) // S0(Bom) ≈ 3,17 dias
    }

    @Test fun `revisoes bem sucedidas aumentam o intervalo`() {
        var card = CardState()
        var now = t0
        val intervals = ArrayList<Long>()
        repeat(5) {
            val o = fsrs.review(card, Rating.Good, now)
            intervals += (o.card.dueAt - now) / day
            now = o.card.dueAt
            card = o.card
        }
        println("Intervalos com 'Bom' sucessivo: $intervals dias")
        assertThat(intervals).isInStrictOrder()
        assertThat(card.phase).isEqualTo(Phase.Review)
    }

    @Test fun `esquecer reduz a estabilidade, conta lapso e reapresenta em minutos`() {
        var card = fsrs.review(CardState(), Rating.Good, t0).card
        card = fsrs.review(card, Rating.Good, card.dueAt).card
        val before = card.stability
        val o = fsrs.review(card, Rating.Again, card.dueAt)
        assertThat(o.card.stability).isLessThan(before)
        assertThat(o.card.lapses).isEqualTo(1)
        assertThat(o.card.phase).isEqualTo(Phase.Relearning)
        assertThat(o.card.dueAt - card.dueAt).isEqualTo(Fsrs.AGAIN_DELAY_MS)
    }

    @Test fun `dificuldade sobe com esqueci e cai com facil, sempre entre 1 e 10`() {
        var card = fsrs.review(CardState(), Rating.Good, t0).card
        val d0 = card.difficulty
        var c = card
        repeat(20) { c = fsrs.review(c, Rating.Again, c.dueAt).card }
        assertThat(c.difficulty).isGreaterThan(d0)
        assertThat(c.difficulty).isAtMost(10.0)
        var e = card
        repeat(20) { e = fsrs.review(e, Rating.Easy, e.dueAt).card }
        assertThat(e.difficulty).isLessThan(d0)
        assertThat(e.difficulty).isAtLeast(1.0)
    }

    @Test fun `rotulos de intervalo`() {
        assertThat(Fsrs.formatInterval(10 * 60_000L)).isEqualTo("10 min")
        assertThat(Fsrs.formatInterval(3 * day)).isEqualTo("3 d")
        assertThat(Fsrs.formatInterval(60 * day)).isEqualTo("2 meses")
    }
}
