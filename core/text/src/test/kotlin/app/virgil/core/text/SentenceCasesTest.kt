package app.virgil.core.text

import app.virgil.core.model.Language
import org.junit.Assert.assertEquals
import org.junit.Test

/** Segmentação de frases nos casos difíceis da literatura (modo frase do leitor). */
class SentenceCasesTest {
    private fun split(l: Language, t: String) = TextSegmenter(l).sentences(t).map { t.substring(it.first, it.last + 1) }

    @Test fun casos() {
        val cases = listOf(
            Language.EN_GB to "“What is his name?” “Bingley.” “Is he married or single?”" to
                listOf("“What is his name?”", "“Bingley.”", "“Is he married or single?”"),
            Language.EN_GB to "“Oh! Single, my dear, to be sure!” cried she. He said nothing." to
                listOf("“Oh! Single, my dear, to be sure!” cried she.", "He said nothing."),
            Language.EN_GB to "Mr. and Mrs. Dursley, of number four, Privet Drive, were proud. They were normal." to
                listOf("Mr. and Mrs. Dursley, of number four, Privet Drive, were proud.", "They were normal."),
            Language.EN_GB to "J. K. Rowling wrote it. Then she rested." to listOf("J. K. Rowling wrote it.", "Then she rested."),
            Language.EN_GB to "He waited… and waited. Nobody came." to listOf("He waited… and waited.", "Nobody came."),
            Language.EN_GB to "He waited... and then he left. Nobody came." to listOf("He waited... and then he left.", "Nobody came."),
            Language.FR to "« Viens ici ! » dit-il. Elle ne bougea pas." to listOf("« Viens ici ! » dit-il.", "Elle ne bougea pas."),
            Language.FR to "M. Bovary entra. Mme Bovary sortit." to listOf("M. Bovary entra.", "Mme Bovary sortit."),
            Language.DE to "„Kommst du?“, fragte er. Sie schwieg." to listOf("„Kommst du?“, fragte er.", "Sie schwieg."),
            Language.DE to "Hr. Müller kam z.B. spät. Er schlief." to listOf("Hr. Müller kam z.B. spät.", "Er schlief."),
            Language.ES to "—¿Vienes? —preguntó él. Ella calló." to listOf("—¿Vienes? —preguntó él.", "Ella calló."),
            Language.ES to "La Sra. García llegó. El Dr. Pérez no." to listOf("La Sra. García llegó.", "El Dr. Pérez no."),
            Language.IT to "«Vieni?» chiese lui. Lei tacque." to listOf("«Vieni?» chiese lui.", "Lei tacque."),
            Language.PT_BR to "— Você vem? — perguntou ele. Ela calou." to listOf("— Você vem? — perguntou ele.", "Ela calou."),
        )
        val failures = cases.mapNotNull { (lt, expected) ->
            val (l, t) = lt
            val got = split(l, t)
            if (got != expected) "${l.tag}: $t\n  esperado $expected\n  obtido   $got" else null
        }
        assertEquals(failures.joinToString("\n"), 0, failures.size)
    }
}
