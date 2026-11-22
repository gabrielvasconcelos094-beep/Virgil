package app.virgil.core.text

import app.virgil.core.model.Language

/**
 * Identificação de idioma por palavras funcionais (artigos, preposições, pronomes, auxiliares).
 *
 * Para textos de livro (milhares de palavras) a frequência dessas palavras separa os seis
 * idiomas com folga, sem modelo estatístico. Em trechos curtos a confiança cai e o chamador
 * deve preferir os metadados do arquivo.
 */
object LanguageDetector {
    data class Result(val language: Language?, val confidence: Float, val scores: Map<Language, Float>)

    private val profiles: Map<Language, Set<String>> = mapOf(
        Language.EN_GB to setOf(
            "the", "and", "of", "to", "a", "in", "that", "it", "was", "he", "she", "his", "her", "is", "with", "had", "for",
            "as", "at", "on", "but", "not", "be", "they", "you", "which", "have", "this", "from", "by", "would", "were", "been",
            "what", "there", "could", "said", "them", "their", "him", "into", "when", "who", "an", "or", "so", "if", "all", "my",
        ),
        Language.FR to setOf(
            "le", "la", "les", "de", "des", "du", "et", "un", "une", "il", "elle", "que", "qui", "ne", "pas", "en", "dans", "est",
            "au", "aux", "se", "sa", "son", "ses", "lui", "pour", "sur", "avec", "plus", "mais", "comme", "nous", "vous", "était",
            "avait", "on", "ce", "cette", "je", "me", "y", "tout", "fait", "dit", "leur", "été", "où", "très", "sans",
        ),
        Language.DE to setOf(
            "der", "die", "das", "und", "zu", "den", "ein", "eine", "nicht", "sich", "er", "sie", "es", "mit", "auf", "dem", "ist",
            "war", "ich", "als", "auch", "wie", "an", "aus", "so", "noch", "nur", "hatte", "aber", "wenn", "schon", "einen",
            "einer", "doch", "dass", "daß", "ihm", "ihr", "ihn", "wieder", "nach", "bei", "um", "über", "vor", "durch", "wurde",
        ),
        Language.IT to setOf(
            "il", "di", "che", "e", "la", "un", "una", "non", "per", "in", "si", "le", "del", "della", "lo", "gli", "con", "da",
            "al", "alla", "ma", "come", "era", "è", "anche", "suo", "sua", "più", "nel", "nella", "questo", "quella", "dei",
            "delle", "aveva", "egli", "lei", "lui", "io", "mi", "ci", "poi", "così", "tutto", "sono", "fu", "quando", "ancora",
        ),
        Language.ES to setOf(
            "el", "la", "los", "las", "de", "que", "y", "en", "un", "una", "no", "se", "con", "por", "para", "su", "sus", "lo",
            "al", "del", "como", "más", "pero", "le", "les", "era", "había", "es", "me", "mi", "sin", "sobre", "este", "esta",
            "ya", "muy", "cuando", "también", "porque", "todo", "él", "ella", "yo", "fue", "hasta", "donde", "estaba", "aunque",
        ),
        Language.PT_BR to setOf(
            "o", "a", "os", "as", "de", "do", "da", "dos", "das", "que", "e", "em", "no", "na", "nos", "nas", "um", "uma", "não",
            "se", "com", "por", "para", "seu", "sua", "como", "mais", "mas", "ele", "ela", "era", "foi", "ao", "à", "também",
            "quando", "muito", "já", "eu", "isso", "esse", "essa", "pelo", "pela", "tinha", "estava", "você", "então", "até",
        ),
    )

    // Grafemas que praticamente só ocorrem em um idioma do conjunto (peso extra, moderado).
    private val markers: Map<Language, Regex> = mapOf(
        Language.DE to Regex("[ßäöü]"),
        Language.ES to Regex("[ñ¿¡]"),
        Language.PT_BR to Regex("[ãõ]|ção|ções"),
        Language.FR to Regex("[êëœçè]|eau|aux\\b"),
        Language.IT to Regex("[òì]|zione|zza"),
    )

    private val tokenRegex = Regex("[\\p{L}’']+")

    fun detect(text: String, maxTokens: Int = 20_000): Result {
        val tokens = tokenRegex.findAll(text.lowercase()).take(maxTokens).map { it.value.trim('\'', '’') }.toList()
        if (tokens.size < 5) return Result(null, 0f, emptyMap())
        val raw = profiles.mapValues { (lang, words) ->
            val hits = tokens.count { it in words }.toFloat() / tokens.size
            val markerHits = markers[lang]?.findAll(text.take(200_000).lowercase())?.count() ?: 0
            hits + 0.15f * (markerHits.toFloat() / tokens.size).coerceAtMost(0.3f)
        }
        val sorted = raw.entries.sortedByDescending { it.value }
        val best = sorted[0]
        val second = sorted[1].value
        // Confiança: margem relativa entre o primeiro e o segundo, atenuada para textos curtos.
        val margin = if (best.value <= 0f) 0f else (best.value - second) / best.value
        val lengthFactor = (tokens.size / 60f).coerceAtMost(1f)
        val confidence = (margin * 1.6f).coerceIn(0f, 1f) * lengthFactor
        return Result(if (best.value >= 0.08f) best.key else null, confidence, raw)
    }

    /**
     * Combina detecção e metadados (dc:language). Metadados de EPUB são frequentemente errados
     * ("en" em livro francês); por isso a detecção vence quando é confiante e discorda.
     */
    fun resolve(text: String, declared: Language?): Language? {
        val r = detect(text)
        return when {
            r.language == null -> declared
            declared == null -> r.language
            r.language == declared -> declared
            r.confidence >= 0.5f -> r.language
            else -> declared
        }
    }
}
