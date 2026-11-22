package app.virgil.core.text

import app.virgil.core.model.Language
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextSegmenterTest {
    private fun word(lang: Language, text: String, at: String): String? {
        val r = TextSegmenter(lang).wordAt(text, text.indexOf(at)) ?: return null
        return text.substring(r.first, r.last + 1)
    }

    @Test fun `palavra sob o toque`() {
        val s = "She could scarcely believe what she had witnessed."
        assertThat(word(Language.EN_GB, s, "arcely")).isEqualTo("scarcely")
        assertThat(word(Language.EN_GB, s, " believe")).isNull() // espaço
        assertThat(word(Language.EN_GB, s, ".")).isNull()
    }

    @Test fun `contracao inglesa fica inteira`() {
        assertThat(word(Language.EN_GB, "I don't know.", "on't")).isEqualTo("don't")
    }

    @Test fun `elisao francesa e italiana separa artigo`() {
        val fr = "Il regarda l’homme qui passait."
        assertThat(word(Language.FR, fr, "homme")).isEqualTo("homme")
        assertThat(word(Language.FR, fr, "l’h")).isEqualTo("l’")
        assertThat(word(Language.IT, "Quell'anno dell'arte", "arte")).isEqualTo("arte")
        assertThat(word(Language.FR, "aujourd'hui", "hui")).isEqualTo("aujourd'hui")
    }

    @Test fun `abreviacoes e iniciais nao encerram frase`() {
        val s = "Mrs. Dalloway said she would buy the flowers herself. For Lucy had her work cut out for her."
        val seg = TextSegmenter(Language.EN_GB)
        val parts = seg.sentences(s).map { s.substring(it.first, it.last + 1) }
        assertThat(parts).containsExactly(
            "Mrs. Dalloway said she would buy the flowers herself.",
            "For Lucy had her work cut out for her.",
        ).inOrder()
        val fr = "M. Homais entra. Il salua."
        assertThat(TextSegmenter(Language.FR).sentences(fr)).hasSize(2)
    }

    @Test fun `frase ao redor de uma palavra`() {
        val s = "Il pleuvait. Il a passé l'examen hier, mais il ne connaît pas le résultat. Puis il partit."
        val seg = TextSegmenter(Language.FR)
        val i = s.indexOf("passé")
        val r = seg.sentenceAround(s, i, i + 5)
        assertThat(s.substring(r.first, r.last + 1)).isEqualTo("Il a passé l'examen hier, mais il ne connaît pas le résultat.")
    }

    @Test fun `lista de palavras`() {
        val s = "Sie schlug das Buch auf."
        val words = TextSegmenter(Language.DE).words(s).map { s.substring(it.first, it.last + 1) }
        assertThat(words).containsExactly("Sie", "schlug", "das", "Buch", "auf").inOrder()
    }
}

class TextNormalizerTest {
    @Test fun `remove hifen suave e espacos especiais`() {
        assertThat(TextNormalizer.clean("Ver­wand­lung und  so")).isEqualTo("Verwandlung und so")
    }

    @Test fun `compoe acentos em NFC`() {
        assertThat(TextNormalizer.clean("página")).isEqualTo("página")
    }
}

class LanguageDetectorTest {
    private val samples = mapOf(
        Language.EN_GB to "For having lived in Westminster—how many years now? over twenty,—one feels even in the midst of the traffic, or waking at night, Clarissa was positive, a particular hush, or solemnity; an indescribable pause; a suspense before Big Ben strikes.",
        Language.FR to "Parfois, à peine ma bougie éteinte, mes yeux se fermaient si vite que je n'avais pas le temps de me dire : « Je m'endors. » Et, une demi-heure après, la pensée qu'il était temps de chercher le sommeil m'éveillait.",
        Language.DE to "Er lag auf seinem panzerartig harten Rücken und sah, wenn er den Kopf ein wenig hob, seinen gewölbten, braunen, von bogenförmigen Versteifungen geteilten Bauch, auf dessen Höhe sich die Bettdecke kaum noch erhalten konnte.",
        Language.IT to "C'era una volta un pezzo di legno. Non era un legno di lusso, ma un semplice pezzo da catasta, di quelli che d'inverno si mettono nelle stufe e nei caminetti per accendere il fuoco e per riscaldare le stanze.",
        Language.ES to "En un lugar de la Mancha, de cuyo nombre no quiero acordarme, no ha mucho tiempo que vivía un hidalgo de los de lanza en astillero, adarga antigua, rocín flaco y galgo corredor.",
        Language.PT_BR to "Uma noite destas, vindo da cidade para o Engenho Novo, encontrei no trem da Central um rapaz aqui do bairro, que eu conheço de vista e de chapéu. Cumprimentou-me, sentou-se ao pé de mim, falou da lua e dos ministros.",
    )

    @Test fun `identifica os seis idiomas em paragrafos literarios`() {
        samples.forEach { (lang, text) -> assertThat(LanguageDetector.detect(text).language).isEqualTo(lang) }
    }

    @Test fun `deteccao confiante vence metadado errado`() {
        val fr = samples.getValue(Language.FR).repeat(3)
        assertThat(LanguageDetector.resolve(fr, Language.EN_GB)).isEqualTo(Language.FR)
    }

    @Test fun `texto curto demais usa o metadado`() {
        assertThat(LanguageDetector.resolve("Oui.", Language.FR)).isEqualTo(Language.FR)
    }
}
