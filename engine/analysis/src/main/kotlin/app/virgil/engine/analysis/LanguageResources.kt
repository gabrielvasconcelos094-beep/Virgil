package app.virgil.engine.analysis

import app.virgil.core.model.Language

/**
 * Recursos linguísticos por idioma usados pela análise. Listas fechadas e curtas, de alta precisão:
 * preferimos deixar de contar uma ocorrência a contar uma falsa.
 */
internal class LanguageResources(
    val language: Language,
    /** Conjunções subordinativas e pronomes relativos (marcadores de oração subordinada). */
    val subordinators: Set<String>,
    /** Formas verbais literárias ou complexas (tempos/modos pouco usados na fala). */
    val literaryForms: List<Regex>,
    val literaryLabel: String,
    /** Grafias e formas arcaicas. */
    val archaisms: Set<String>,
    val archaicPatterns: List<Regex> = emptyList(),
    /** Partículas de phrasal verbs (inglês) ou de verbos separáveis (alemão). */
    val particles: Set<String> = emptySet(),
    /** Expressões idiomáticas frequentes na literatura (minúsculas). */
    val idioms: List<String> = emptyList(),
) {
    companion object {
        fun forLanguage(l: Language): LanguageResources = when (l) {
            Language.EN_GB -> EN
            Language.FR -> FR
            Language.DE -> DE
            Language.IT -> IT
            Language.ES -> ES
            Language.PT_BR -> PT
        }

        // (?U): \w e \b reconhecem letras acentuadas (o padrão do Java é só ASCII).
        private fun rx(vararg p: String) = p.map { Regex("(?U)" + it, RegexOption.IGNORE_CASE) }

        private val EN = LanguageResources(
            Language.EN_GB,
            subordinators = setOf("that", "which", "who", "whom", "whose", "because", "although", "though", "while", "whilst", "whereas", "unless", "until", "since", "if", "when", "whenever", "where", "wherever", "as", "lest", "whether"),
            literaryForms = rx("\\b(had|having) been \\w+ed\\b", "\\bwere (he|she|it|i|they|we) to\\b", "\\bhad (he|she|they|i|we) (known|been|seen)\\b", "\\bwould have been\\b", "\\bmight have\\b", "\\bshould have\\b"),
            literaryLabel = "tempos compostos, condicionais e inversões (\"had he known\", \"would have been\")",
            archaisms = setOf("thee", "thou", "thy", "thine", "hath", "doth", "art", "ye", "'tis", "’tis", "whilst", "amongst", "hither", "thither", "whence", "wherefore", "ere", "oft", "nigh", "betwixt", "methinks", "shalt", "wilt", "couldst", "wouldst"),
            particles = setOf("up", "down", "out", "off", "away", "back", "over", "through", "about", "along", "around", "aside", "forth", "in", "on"),
            idioms = listOf("by and by", "at once", "in vain", "on the whole", "by no means", "out of the question", "at length", "as it were", "to be sure", "in spite of", "for good", "all the same", "make up one's mind", "take leave", "set out", "give way", "come to pass", "at all events", "of course", "no doubt"),
        )

        private val FR = LanguageResources(
            Language.FR,
            subordinators = setOf("que", "qui", "dont", "où", "lequel", "laquelle", "lesquels", "lesquelles", "auquel", "duquel", "quand", "lorsque", "puisque", "parce", "quoique", "bien", "afin", "pour", "si", "comme", "tandis", "pendant", "avant", "après", "sans"),
            literaryForms = rx(
                "\\b\\w+(âmes|âtes|èrent|îmes|îtes|ûmes|ûtes)\\b",
                "\\b\\w{2,}(irent|urent)\\b",
                "\\b(fut|furent|eut|eurent|fit|firent|dit|dirent|vint|vinrent|prit|prirent|mit|mirent|vit|virent|sut|surent|put|purent|fallut|voulut|parut|connut|mourut|naquit|tint|tinrent|reprit|répondit|s'écria|demanda|regarda|entra|sortit)\\b",
                // Subjuntivo imperfeito: só terminações inequívocas ("-issent" também é presente: "finissent").
                "\\b\\w{2,}(assent|assiez|ussent|ussiez)\\b",
                "\\b(fût|eût|fît|vînt|dût|pût|sût|fussent|eussent)\\b",
            ),
            literaryLabel = "passé simple e subjuntivo imperfeito",
            archaisms = setOf("icelle", "icelui", "moult", "oncques", "jadis", "naguère", "point", "sitôt", "quérir", "céans"),
            idioms = listOf("de bonne heure", "tout à coup", "tout de suite", "à peine", "au fur et à mesure", "à la fois", "tant bien que mal", "faire semblant", "avoir beau", "en vain", "à l'improviste", "peu à peu", "tout à fait", "coûte que coûte", "à tort", "en effet", "d'ailleurs", "au fond", "du reste", "sur-le-champ"),
        )

        private val DE = LanguageResources(
            Language.DE,
            subordinators = setOf("dass", "daß", "weil", "obwohl", "obgleich", "wenn", "als", "ob", "damit", "während", "nachdem", "bevor", "ehe", "bis", "seit", "seitdem", "indem", "sodass", "welcher", "welche", "welches", "wo", "wie", "falls"),
            literaryForms = rx(
                "\\b(wäre|wären|wärst|hätte|hätten|hättest|würde|würden|könnte|könnten|müsste|müßte|müssten|möchte|dürfte|sollte|käme|kämen|ginge|gingen|bliebe|ließe|stünde|sähe|fände|wüsste|wüßte)\\b",
                "\\b(sei|seien|habe|werde|könne|müsse|wolle|solle|dürfe)\\b",
            ),
            literaryLabel = "Konjunktiv I e II (discurso indireto e hipóteses)",
            archaisms = setOf("daß", "mußte", "muß", "müßte", "läßt", "ließ", "wußte", "bißchen", "thun", "theil", "seyn", "drey", "giebt", "itzt", "ward", "ihro"),
            archaicPatterns = rx("\\b\\w*th(un|eil|ür|ier|räne)\\w*\\b"),
            particles = setOf("an", "auf", "aus", "ab", "ein", "mit", "nach", "vor", "zu", "zurück", "weg", "her", "hin", "fort", "los", "zusammen", "um"),
            idioms = listOf("auf einmal", "mit einem mal", "ohne weiteres", "nach und nach", "zum ersten mal", "auf jeden fall", "von neuem", "gang und gäbe", "aufs neue", "im grunde", "nach wie vor", "ab und zu", "vor allem", "zu fuß", "eines tages", "eines morgens", "eines abends", "zu hause", "im stillen", "allerdings"),
        )

        private val IT = LanguageResources(
            Language.IT,
            subordinators = setOf("che", "cui", "quale", "quali", "perché", "poiché", "sebbene", "benché", "quando", "mentre", "se", "come", "dove", "affinché", "finché", "purché", "qualora", "siccome", "dopo", "prima"),
            literaryForms = rx(
                "\\b\\w{2,}(arono|erono|irono|ettero|ebbero)\\b",
                "\\b(fu|furono|ebbe|disse|dissero|fece|fecero|venne|vennero|prese|presero|vide|videro|mise|misero|rispose|stette|diede|seppe|volle|nacque|morì|parlò|andò|tornò|guardò|entrò|domandò|esclamò)\\b",
                // Congiuntivo imperfetto: "-asse/-esse" isolados capturariam "classe", "stesse"; usam-se formas inequívocas.
                "\\b\\w{2,}(assero|essero|issero)\\b",
                "\\b(fosse|fossero|fossi|avesse|avessero|avessi|facesse|dicesse|potesse|volesse|sapesse)\\b",
            ),
            literaryLabel = "passato remoto e congiuntivo imperfetto",
            archaisms = setOf("egli", "ella", "esso", "essa", "codesto", "costui", "colui", "cotesto", "giacché", "ivi", "onde", "testé", "uopo", "imperocché", "dappoi", "chè"),
            idioms = listOf("a un tratto", "a poco a poco", "di nuovo", "per lo più", "a malapena", "in fretta", "di colpo", "a stento", "fare finta", "prendere un granchio", "tutto a un tratto", "da capo", "per forza", "in fondo", "del resto", "a lungo", "a vicenda", "di gran lunga", "a ogni modo", "senz'altro"),
        )

        private val ES = LanguageResources(
            Language.ES,
            subordinators = setOf("que", "quien", "quienes", "cual", "cuales", "cuyo", "cuya", "porque", "aunque", "cuando", "mientras", "si", "como", "donde", "pues", "puesto", "aunque", "para", "hasta", "según", "conforme"),
            literaryForms = rx(
                "\\b\\w{3,}(ara|aras|áramos|aran|iera|ieras|iéramos|ieran|ase|ases|ásemos|asen|iese|ieses|iésemos|iesen)\\b",
                "\\b(fuera|fueras|fuéramos|fueran|fuese|fuesen|hubiera|hubieran|hubiese|hubiesen)\\b",
                "\\b\\w{2,}(áronse|ióse|óse|ábase|íase)\\b",
            ),
            literaryLabel = "subjuntivo imperfeito e pronomes enclíticos arcaicos (\"quedóse\")",
            archaisms = setOf("vuesa", "agora", "asaz", "otrosí", "maguer", "empero", "quedóse", "díjole", "fuéronse", "hase", "harto", "merced", "hidalgo", "ínsula", "fermosa"),
            idioms = listOf("de repente", "a menudo", "de nuevo", "sin embargo", "por lo tanto", "a lo mejor", "de hecho", "a duras penas", "en seguida", "de pronto", "al fin", "por fin", "a ciegas", "a la vez", "de golpe", "en balde", "a solas", "de veras", "poco a poco", "sin más"),
        )

        private val PT = LanguageResources(
            Language.PT_BR,
            subordinators = setOf("que", "quem", "qual", "quais", "cujo", "cuja", "porque", "embora", "quando", "enquanto", "se", "como", "onde", "pois", "conquanto", "caso", "conforme", "segundo"),
            literaryForms = rx("\\b\\w{2,}(áramos|êramos|íramos|ássemos|êssemos|íssemos)\\b", "\\b\\w{3,}(assem|essem|issem)\\b", "\\b(fora|foras|tivera|houvera|fizera|dissera|estivera)\\b"),
            literaryLabel = "mais-que-perfeito simples e subjuntivo imperfeito",
            archaisms = setOf("vosmecê", "vossemecê", "outrossim", "destarte", "porquanto", "conquanto", "mister", "assaz", "outrora"),
            idioms = listOf("de repente", "aos poucos", "de novo", "de fato", "à toa", "de vez em quando", "às vezes", "de propósito", "a fim de", "em vão"),
        )
    }
}
