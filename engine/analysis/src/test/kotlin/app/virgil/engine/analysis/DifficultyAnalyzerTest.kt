package app.virgil.engine.analysis

import app.virgil.core.model.CefrLevel
import app.virgil.core.model.Language
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DifficultyAnalyzerTest {
    // Textos simples escritos para o teste (estilo de leitura graduada).
    private val simpleEn = """
        Tom has a small dog. The dog is brown and white. Its name is Max. Every morning Tom and Max go to the park.
        The park is near their house. There are many trees in the park. Tom likes to sit under a big tree. Max likes to run.
        One day it is very cold. Tom puts on his coat and his hat. Max does not want to go out. He wants to stay in the warm kitchen.
        Tom laughs. "Come on, Max," he says. "We can walk fast." They walk to the park. They see a girl with a red ball.
        The girl throws the ball and Max runs after it. Max is happy now. Tom is happy too. After the walk they go home and eat lunch.
        In the afternoon Tom reads a book and Max sleeps on the sofa. In the evening Tom's mother comes home from work.
        She is tired but she smiles. "How was your day?" she asks. "Good," says Tom. "We went to the park and Max played with a ball."
    """.trimIndent()

    // Woolf (1925), Austen (1813), Conrad (1899) — domínio público.
    private val literaryEn = """
        For having lived in Westminster—how many years now? over twenty,—one feels even in the midst of the traffic, or waking at night, Clarissa was positive, a particular hush, or solemnity; an indescribable pause; a suspense (but that might be her heart, affected, they said, by influenza) before Big Ben strikes.
        There! Out it boomed. First a warning, musical; then the hour, irrevocable. The leaden circles dissolved in the air.
        It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife. However little known the feelings or views of such a man may be on his first entering a neighbourhood, this truth is so well fixed in the minds of the surrounding families, that he is considered the rightful property of some one or other of their daughters.
        The tranquil waterway leading to the uttermost ends of the earth flowed sombre under an overcast sky—seemed to lead into the heart of an immense darkness. The sea-reach of the Thames stretched before us like the beginning of an interminable waterway, whilst the haze rested on the low shores that ran out to sea in vanishing flatness.
        The air was dark above Gravesend, and farther back still seemed condensed into a mournful gloom, brooding motionless over the biggest, and the greatest, town on earth. The Director of Companies was our captain and our host; we four affectionately watched his back as he stood in the bows looking to seaward.
    """.trimIndent()

    private val simpleFr = """
        Marie habite à Paris avec sa famille. Elle a douze ans. Le matin, elle prend le bus pour aller à l'école. Elle aime le français et la musique.
        À midi, elle mange à la cantine avec ses amies. Le soir, elle rentre à la maison et fait ses devoirs. Sa mère prépare le dîner.
        Le samedi, Marie va au parc avec son petit frère. Ils jouent au ballon et ils mangent une glace. Le dimanche, toute la famille va chez la grand-mère.
        La grand-mère habite dans un petit village. Elle a un jardin avec des fleurs et des arbres. Marie aime beaucoup ce jardin. Elle lit un livre sous un arbre.
        Le soir, ils rentrent à Paris en voiture. Marie est fatiguée mais contente. Elle dit bonne nuit à ses parents et elle va au lit.
    """.trimIndent()

    // Flaubert (1857), Proust (1913) — domínio público.
    private val literaryFr = """
        Nous étions à l'Étude, quand le Proviseur entra, suivi d'un nouveau habillé en bourgeois et d'un garçon de classe qui portait un grand pupitre. Ceux qui dormaient se réveillèrent, et chacun se leva comme surpris dans son travail.
        Le Proviseur nous fit signe de nous rasseoir; puis, se tournant vers le maître d'études: Monsieur Roger, lui dit-il à demi-voix, voici un élève que je vous recommande, il entre en cinquième. Si son travail et sa conduite sont méritoires, il passera dans les grands, où l'appelle son âge.
        Longtemps, je me suis couché de bonne heure. Parfois, à peine ma bougie éteinte, mes yeux se fermaient si vite que je n'avais pas le temps de me dire: Je m'endors. Et, une demi-heure après, la pensée qu'il était temps de chercher le sommeil m'éveillait; je voulais poser le volume que je croyais avoir encore dans les mains et souffler ma lumière.
        Je n'avais pas cessé en dormant de faire des réflexions sur ce que je venais de lire, mais ces réflexions avaient pris un tour un peu particulier; il me semblait que j'étais moi-même ce dont parlait l'ouvrage: une église, un quatuor, la rivalité de François Ier et de Charles Quint.
    """.trimIndent()

    private val simpleDe = """
        Anna wohnt in Berlin. Sie hat einen Bruder und eine Schwester. Ihr Bruder heißt Paul und ist zehn Jahre alt. Am Morgen fährt Anna mit dem Fahrrad zur Schule.
        Die Schule ist nicht weit. Anna lernt gern Englisch und Mathe. Am Nachmittag spielt sie mit ihren Freunden im Park. Dann geht sie nach Hause und macht ihre Hausaufgaben.
        Am Abend kocht ihr Vater das Essen. Die Familie isst zusammen in der Küche. Nach dem Essen sehen sie fern oder spielen ein Spiel. Um neun Uhr geht Anna ins Bett.
        Am Wochenende fährt die Familie oft zu den Großeltern. Die Großeltern haben einen großen Garten mit vielen Blumen. Anna hilft ihrer Oma im Garten. Das macht ihr viel Spaß.
        Im Sommer fährt die Familie ans Meer. Dort schwimmt Anna jeden Tag und baut mit Paul eine Burg aus Sand. Am Abend essen sie ein Eis und gehen am Strand spazieren.
    """.trimIndent()

    // Kafka (1915, 1925) — domínio público.
    private val literaryDe = """
        Als Gregor Samsa eines Morgens aus unruhigen Träumen erwachte, fand er sich in seinem Bett zu einem ungeheueren Ungeziefer verwandelt. Er lag auf seinem panzerartig harten Rücken und sah, wenn er den Kopf ein wenig hob, seinen gewölbten, braunen, von bogenförmigen Versteifungen geteilten Bauch, auf dessen Höhe sich die Bettdecke, zum gänzlichen Niedergleiten bereit, kaum noch erhalten konnte.
        Seine vielen, im Vergleich zu seinem sonstigen Umfang kläglich dünnen Beine flimmerten ihm hilflos vor den Augen. Was ist mit mir geschehen? dachte er. Es war kein Traum.
        Jemand mußte Josef K. verleumdet haben, denn ohne daß er etwas Böses getan hätte, wurde er eines Morgens verhaftet. Die Köchin der Frau Grubach, seiner Zimmervermieterin, die ihm jeden Tag gegen acht Uhr früh das Frühstück brachte, kam diesmal nicht. Das war noch niemals geschehen.
        K. wartete noch ein Weilchen, sah von seinem Kopfkissen aus die alte Frau, die ihm gegenüber wohnte und die ihn mit einer an ihr ganz ungewöhnlichen Neugierde beobachtete, dann aber, gleichzeitig befremdet und hungrig, läutete er.
    """.trimIndent()

    private fun analyze(lang: Language, text: String): Double {
        val a = DifficultyAnalyzer(lang)
        val m = a.measure(text.split("\n"))
        val s = a.score(m)!!
        println("${lang.tag}: ${m.words} palavras, V95=${m.vocabularyFor(0.95)}, frase média=${"%.1f".format(m.meanSentenceLength)}, " +
            "dims=${"%.1f/%.1f/%.1f/%.1f".format(s.vocabulary, s.syntax, s.grammar, s.style)} → ${"%.2f".format(s.combined)} (${a.rangeFor(s, m.words).label})")
        return s.combined
    }

    @Test fun `texto literario e mais dificil que texto simples nos tres idiomas`() {
        listOf(Triple(Language.EN_GB, simpleEn, literaryEn), Triple(Language.FR, simpleFr, literaryFr), Triple(Language.DE, simpleDe, literaryDe))
            .forEach { (lang, simple, literary) ->
                val s = analyze(lang, simple)
                val l = analyze(lang, literary)
                assertThat(l - s).isAtLeast(1.0)
                assertThat(DifficultyAnalyzer.levelOf(s)).isAtMost(CefrLevel.B1)
                assertThat(DifficultyAnalyzer.levelOf(l)).isAtLeast(CefrLevel.B2)
            }
    }

    @Test fun `detecta passe simple, konjunktiv e arcaismos`() {
        val fr = DifficultyAnalyzer(Language.FR).measure(literaryFr.split("\n"))
        assertThat(fr.literaryForms).isAtLeast(3) // réveillèrent, fit, dit, entra…
        val de = DifficultyAnalyzer(Language.DE).measure(literaryDe.split("\n"))
        assertThat(de.archaisms).isAtLeast(2) // mußte, daß
        assertThat(de.literaryForms).isAtLeast(1) // hätte
        assertThat(de.properNouns).isAtLeast(2) // Gregor Samsa, Grubach…
    }

    @Test fun `achados em portugues e confianca limitada`() {
        val a = DifficultyAnalyzer(Language.EN_GB)
        val (_, book) = a.analyzeBook(listOf(literaryEn.split("\n"), literaryEn.split("\n")))
        assertThat(book).isNotNull()
        assertThat(book!!.findings.any { it.startsWith("Vocabulário") }).isTrue()
        assertThat(book.range.confidence.toDouble()).isAtMost(0.95)
        book.findings.forEach { println(" - $it") }
    }

    @Test fun `texto curto demais nao recebe nivel`() {
        val a = DifficultyAnalyzer(Language.EN_GB)
        assertThat(a.score(a.measure(listOf("A short line.")))).isNull()
    }

    @Test fun `variante britanica pela ortografia`() {
        val text = "The colour of the theatre was grey. She would realise it whilst at the centre of the neighbourhood, honouring the programme."
        assertThat(VariantDetector.detect(Language.EN_GB, text)).startsWith("Inglês britânico")
        assertThat(VariantDetector.detect(Language.EN_GB, "The color of the theater was gray at the center of the neighborhood.")).startsWith("Inglês americano")
        assertThat(VariantDetector.detect(Language.ES, "¿Vosotros sabéis dónde estáis? Habéis venido tarde y tenéis prisa.")).startsWith("Espanhol europeu")
    }

    @Test fun `mtld distingue texto repetitivo de texto variado`() {
        val repetitive = List(200) { listOf("the", "dog", "is", "big", "and", "the", "cat")[it % 7] }
        val varied = ("for having lived in westminster how many years now over twenty one feels even in the midst of the traffic or waking at night " +
            "clarissa was positive a particular hush or solemnity an indescribable pause a suspense before big ben strikes there out it boomed first " +
            "a warning musical then the hour irrevocable the leaden circles dissolved in the air").split(" ")
        assertThat(DifficultyAnalyzer.mtld(varied)).isGreaterThan(DifficultyAnalyzer.mtld(repetitive) * 3)
    }
}
