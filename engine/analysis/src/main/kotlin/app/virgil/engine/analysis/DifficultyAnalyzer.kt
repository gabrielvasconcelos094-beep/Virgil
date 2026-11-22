package app.virgil.engine.analysis

import app.virgil.core.model.CefrLevel
import app.virgil.core.model.CefrRange
import app.virgil.core.model.Language
import app.virgil.core.text.TextSegmenter
import kotlinx.serialization.Serializable
import java.util.zip.GZIPInputStream
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Lista de frequência: forma → posição (1 = mais frequente). */
class FrequencyList(private val ranks: Map<String, Int>) {
    fun rank(word: String): Int? = ranks[word]
    val size: Int get() = ranks.size

    companion object {
        private val cache = HashMap<String, FrequencyList>()

        /** Carrega de src/main/resources/freq/<iso>.txt.gz (funciona no APK e nos testes da JVM). */
        @Synchronized
        fun load(language: Language): FrequencyList = cache.getOrPut(language.iso639) {
            val stream = FrequencyList::class.java.getResourceAsStream("/freq/${language.iso639}.txt.gz")
                ?: error("Lista de frequência ausente para ${language.iso639}")
            val map = HashMap<String, Int>(70_000)
            GZIPInputStream(stream).bufferedReader().useLines { lines -> lines.forEachIndexed { i, w -> map.putIfAbsent(w, i + 1) } }
            FrequencyList(map)
        }
    }
}

/** Métricas brutas de um trecho (capítulo ou livro). Somáveis, para agregar capítulos. */
@Serializable
data class TextMetrics(
    val words: Int = 0,
    val sentences: Int = 0,
    val longSentences: Int = 0,          // > 30 palavras
    val subordinators: Int = 0,
    val literaryForms: Int = 0,
    val archaisms: Int = 0,
    val particles: Int = 0,              // phrasal verbs (EN) / verbos separáveis (DE)
    val idioms: Int = 0,
    val properNouns: Int = 0,
    val rareTokens: Int = 0,             // fora das 20 mil formas mais frequentes
    val unknownTokens: Int = 0,          // fora da lista de 50 mil
    val dialogueChars: Int = 0,
    val totalChars: Int = 0,
    /** Histograma de posições de frequência (faixas logarítmicas) para calcular cobertura. */
    val rankHistogram: List<Int> = List(RANK_BUCKETS.size + 1) { 0 },
    val sentenceLengths: List<Int> = emptyList(),
    val archaicExamples: List<String> = emptyList(),
    val literaryExamples: List<String> = emptyList(),
) {
    operator fun plus(o: TextMetrics) = TextMetrics(
        words + o.words, sentences + o.sentences, longSentences + o.longSentences, subordinators + o.subordinators,
        literaryForms + o.literaryForms, archaisms + o.archaisms, particles + o.particles, idioms + o.idioms,
        properNouns + o.properNouns, rareTokens + o.rareTokens, unknownTokens + o.unknownTokens,
        dialogueChars + o.dialogueChars, totalChars + o.totalChars,
        rankHistogram.zip(o.rankHistogram) { a, b -> a + b },
        (sentenceLengths + o.sentenceLengths).let { if (it.size > 4000) it.shuffled(java.util.Random(7)).take(4000) else it },
        (archaicExamples + o.archaicExamples).distinct().take(6),
        (literaryExamples + o.literaryExamples).distinct().take(6),
    )

    val meanSentenceLength: Double get() = if (sentences == 0) 0.0 else words.toDouble() / sentences
    val p90SentenceLength: Int get() = sentenceLengths.sorted().let { if (it.isEmpty()) 0 else it[(it.size * 0.9).toInt().coerceAtMost(it.lastIndex)] }

    /** Quantas formas mais frequentes é preciso conhecer para cobrir [coverage] das palavras do texto. */
    fun vocabularyFor(coverage: Double): Int {
        val counted = rankHistogram.sum()
        if (counted == 0) return 0
        var acc = 0
        rankHistogram.forEachIndexed { i, n ->
            acc += n
            if (acc >= counted * coverage) return if (i < RANK_BUCKETS.size) RANK_BUCKETS[i] else 60_000
        }
        return 60_000
    }

    companion object {
        /** Limites superiores das faixas de frequência. Última faixa implícita: desconhecidas. */
        val RANK_BUCKETS = listOf(250, 500, 750, 1000, 1500, 2000, 2500, 3000, 4000, 5000, 6000, 8000, 10_000, 12_500, 15_000, 20_000, 25_000, 30_000, 40_000, 50_000)
        fun bucketOf(rank: Int): Int = RANK_BUCKETS.indexOfFirst { rank <= it }.let { if (it < 0) RANK_BUCKETS.size else it }
    }
}

/** Pontuação de uma dimensão em escala CEFR contínua (1 = A1 … 6 = C2). */
@Serializable
data class DimensionScores(val vocabulary: Double, val syntax: Double, val grammar: Double, val style: Double) {
    val combined: Double get() = (0.5 * vocabulary + 0.2 * syntax + 0.15 * grammar + 0.15 * style).coerceIn(1.0, 6.0)
    val spread: Double get() {
        val xs = listOf(vocabulary, syntax, grammar, style)
        val m = xs.average()
        return sqrt(xs.sumOf { (it - m) * (it - m) } / xs.size)
    }
}

data class ChapterResult(val metrics: TextMetrics, val scores: DimensionScores?, val range: CefrRange?)

data class BookResult(
    val metrics: TextMetrics,
    val scores: DimensionScores,
    val range: CefrRange,
    val score: Double,
    val findings: List<String>,
    val variant: String?,
)

/**
 * Estimativa de dificuldade de leitura por idioma (ADR-0004). Determinística, explicável e local.
 * Os limiares são heurísticos, ancorados na literatura de tamanho de vocabulário por nível
 * (Milton, 2010) e de cobertura lexical (Nation, 2006; Laufer, 1989): até que sejam calibrados
 * com textos de nível conhecido, a confiança informada fica limitada a [MAX_CONFIDENCE].
 */
class DifficultyAnalyzer(private val language: Language, private val freq: FrequencyList = FrequencyList.load(language)) {
    private val res = LanguageResources.forLanguage(language)
    private val segmenter = TextSegmenter(language)

    /** Analisa os blocos de texto de um capítulo. */
    fun measure(blocks: List<String>): TextMetrics {
        var m = TextMetrics()
        val hist = IntArray(TextMetrics.RANK_BUCKETS.size + 1)
        val lengths = ArrayList<Int>()
        var words = 0; var sentences = 0; var longS = 0; var subs = 0; var lit = 0; var arch = 0
        var parts = 0; var idioms = 0; var proper = 0; var rare = 0; var unknown = 0; var dialogue = 0; var chars = 0
        val archEx = LinkedHashSet<String>(); val litEx = LinkedHashSet<String>()
        for (block in blocks) {
            if (block.isBlank()) continue
            chars += block.length
            dialogue += dialogueChars(block)
            val lower = block.lowercase()
            res.literaryForms.forEach { r -> r.findAll(block).forEach { lit++; if (litEx.size < 6) litEx += it.value } }
            res.archaicPatterns.forEach { r -> r.findAll(block).forEach { arch++; if (archEx.size < 6) archEx += it.value } }
            res.idioms.forEach { idiom -> var i = lower.indexOf(idiom); while (i >= 0) { idioms++; i = lower.indexOf(idiom, i + idiom.length) } }
            for (s in segmenter.sentences(block)) {
                val sentence = block.substring(s.first, s.last + 1)
                val ws = segmenter.words(sentence)
                if (ws.isEmpty()) continue
                sentences++
                lengths += ws.size
                if (ws.size > 30) longS++
                ws.forEachIndexed { idx, r ->
                    val token = sentence.substring(r.first, r.last + 1)
                    val norm = normalize(token)
                    if (norm.isEmpty() || norm.any { it.isDigit() }) return@forEachIndexed
                    words++
                    if (norm in res.subordinators) subs++
                    if (norm in res.archaisms) { arch++; if (archEx.size < 6) archEx += token }
                    if (res.particles.isNotEmpty() && norm in res.particles && isClauseFinalParticle(sentence, r, idx, ws.size)) parts++
                    val rank = freq.rank(norm)
                    val capitalized = token.first().isUpperCase()
                    // Nomes próprios: maiúscula fora do início da frase (ou qualquer maiúscula em alemão) e forma ausente da lista.
                    if (capitalized && rank == null && (idx > 0 || language == Language.DE)) { proper++; return@forEachIndexed }
                    if (capitalized && idx > 0 && language != Language.DE && rank != null && rank > 3_000 && isLikelyName(token)) { proper++; return@forEachIndexed }
                    val effective = rank ?: 60_000
                    if (effective > 20_000) rare++
                    if (rank == null) unknown++
                    hist[TextMetrics.bucketOf(effective)]++
                }
            }
        }
        m = TextMetrics(
            words, sentences, longS, subs, lit, arch, parts, idioms, proper, rare, unknown, dialogue, chars,
            hist.toList(), if (lengths.size > 2000) lengths.shuffled(java.util.Random(3)).take(2000) else lengths,
            archEx.toList(), litEx.toList(),
        )
        return m
    }

    fun score(m: TextMetrics): DimensionScores? {
        if (m.words < MIN_WORDS) return null
        val per1000 = { n: Int -> n * 1000.0 / m.words }
        val v95 = m.vocabularyFor(0.95).toDouble()
        val vocabulary = interpolateLog(v95, VOCAB_ANCHORS)
        val sentenceLen = 0.6 * m.meanSentenceLength + 0.4 * (m.p90SentenceLength / 1.8)
        val subPerSentence = if (m.sentences == 0) 0.0 else m.subordinators.toDouble() / m.sentences
        val syntax = 0.6 * interpolate(sentenceLen, SENTENCE_ANCHORS) + 0.4 * interpolate(subPerSentence, SUBORDINATION_ANCHORS)
        val grammar = interpolate(per1000(m.literaryForms) + 0.5 * per1000(m.particles), GRAMMAR_ANCHORS)
        val rarePer100 = m.rareTokens * 100.0 / m.words
        val style = (interpolate(rarePer100, RARE_ANCHORS) + min(1.0, per1000(m.archaisms) / 4.0) + min(0.5, per1000(m.idioms) / 6.0)).coerceIn(1.0, 6.0)
        return DimensionScores(vocabulary, syntax.coerceIn(1.0, 6.0), grammar, style)
    }

    fun rangeFor(scores: DimensionScores, words: Int, chapterSpread: Double = 0.0): CefrRange {
        val c = scores.combined
        val half = max(0.3, 0.35 * scores.spread + 0.5 * chapterSpread).coerceAtMost(1.0)
        val low = levelOf(c - half)
        val high = levelOf(c + half)
        val lengthFactor = min(1.0, words / 8_000.0)
        val agreement = 1.0 - min(0.6, scores.spread / 2.5)
        val confidence = (lengthFactor * agreement * MAX_CONFIDENCE).toFloat()
        return CefrRange(low, maxOf(low, high), confidence)
    }

    fun analyzeBook(chapters: List<List<String>>): Pair<List<ChapterResult>, BookResult?> {
        val perChapter = chapters.map { blocks ->
            val m = measure(blocks)
            val s = score(m)
            ChapterResult(m, s, s?.let { rangeFor(it, m.words) })
        }
        val total = perChapter.fold(TextMetrics()) { acc, c -> acc + c.metrics }
        val scores = score(total) ?: return perChapter to null
        val chapterScores = perChapter.mapNotNull { it.scores?.combined }
        val chapterSpread = if (chapterScores.size < 2) 0.0 else chapterScores.let { xs -> val mu = xs.average(); sqrt(xs.sumOf { (it - mu) * (it - mu) } / xs.size) }
        val range = rangeFor(scores, total.words, chapterSpread)
        val text = chapters.asSequence().flatten().take(400).joinToString(" ")
        return perChapter to BookResult(total, scores, range, scores.combined, findings(total, scores), VariantDetector.detect(language, text))
    }

    /** Observações em português, das mais relevantes para as menos. */
    fun findings(m: TextMetrics, s: DimensionScores): List<String> {
        val out = ArrayList<Pair<Double, String>>()
        val v95 = m.vocabularyFor(0.95)
        val v98 = m.vocabularyFor(0.98)
        out += s.vocabulary to when {
            v95 >= 60_000 -> "Vocabulário muito diversificado: mais de 5% das palavras estão fora das 50 mil formas mais comuns do idioma."
            else -> "Vocabulário: para reconhecer 95% das palavras é preciso conhecer cerca de ${fmt(v95)} formas mais frequentes do idioma (98%: ${if (v98 >= 60_000) "mais de 50 mil" else fmt(v98)})."
        }
        val mean = m.meanSentenceLength
        val longPct = if (m.sentences == 0) 0 else m.longSentences * 100 / m.sentences
        out += s.syntax to when {
            mean >= 22 -> "Frases longas: média de ${mean.toInt()} palavras por frase; $longPct% passam de 30 palavras."
            mean >= 14 -> "Frases de extensão média (${mean.toInt()} palavras em média), com orações subordinadas frequentes."
            else -> "Frases curtas e diretas (${mean.toInt()} palavras em média)."
        }
        if (m.literaryForms * 1000.0 / m.words >= 3) {
            out += s.grammar to "Formas verbais literárias frequentes: ${res.literaryLabel}" + (m.literaryExamples.takeIf { it.isNotEmpty() }?.let { " — ex.: ${it.take(3).joinToString(", ")}" } ?: "") + "."
        }
        if (m.particles * 1000.0 / m.words >= 4) {
            out += 3.5 to (if (language == Language.EN_GB) "Muitos phrasal verbs (verbo + partícula), cujo sentido nem sempre se deduz das partes."
            else "Muitos verbos separáveis: a partícula aparece no fim da oração e muda o sentido do verbo.")
        }
        if (m.archaisms > 3 && m.archaisms * 1000.0 / m.words >= 0.5) {
            out += 4.5 to "Arcaísmos ou ortografia antiga" + (m.archaicExamples.takeIf { it.isNotEmpty() }?.let { ": ${it.take(4).joinToString(", ")}" } ?: "") + "."
        }
        if (m.idioms * 1000.0 / m.words >= 1) out += 3.0 to "Expressões idiomáticas recorrentes (por exemplo, locuções adverbiais fixas)."
        val dialoguePct = if (m.totalChars == 0) 0 else m.dialogueChars * 100 / m.totalChars
        if (dialoguePct >= 25) out += 2.0 to "Bastante diálogo ($dialoguePct% do texto), o que costuma tornar a leitura mais ágil."
        else if (dialoguePct < 5 && m.words > 3000) out += 2.5 to "Pouco diálogo: narração contínua, mais densa."
        return out.sortedByDescending { it.first }.map { it.second }.take(6)
    }

    private fun normalize(token: String): String =
        token.lowercase().replace('’', '\'').trim('\'', '-').let { w ->
            // Elisões já separadas pelo segmentador: "l'" → "l" (como na lista de frequência).
            if (w.endsWith('\'')) w.dropLast(1) else w
        }

    private fun isLikelyName(token: String) = token.length > 1 && token.drop(1).all { it.isLowerCase() }

    /** Partícula no fim da oração ("gave it up.", "fing … an."), indício de phrasal/separável. */
    private fun isClauseFinalParticle(sentence: String, r: IntRange, idx: Int, count: Int): Boolean {
        if (idx == 0) return false
        val after = sentence.substring(r.last + 1).trimStart()
        return idx == count - 1 || after.firstOrNull()?.let { it == ',' || it == ';' || it == '.' || it == '!' || it == '?' || it == ':' } == true
    }

    private fun dialogueChars(block: String): Int {
        val t = block.trimStart()
        if (t.startsWith('—') || t.startsWith('–') || t.startsWith('-')) return block.length
        var n = 0
        Regex("[“\"«„»‹][^”\"»“«›]{2,}[”\"»“«›]").findAll(block).forEach { n += it.value.length }
        return n
    }

    private fun fmt(n: Int) = "%,d".format(n).replace(',', '.')

    companion object {
        const val VERSION = 1
        const val MIN_WORDS = 120
        /** Limite de confiança até a calibração com textos de nível conhecido (ver ADR-0004). */
        const val MAX_CONFIDENCE = 0.6

        // Âncoras (valor da métrica em A1, A2, B1, B2, C1, C2).
        // Formas flexionadas (a lista conta "throws" e "throw" separadamente): cerca do dobro do número de
        // famílias de palavras de Milton (2010) para cada nível.
        private val VOCAB_ANCHORS = listOf(1_000.0, 2_000.0, 4_000.0, 8_000.0, 16_000.0, 32_000.0)
        private val SENTENCE_ANCHORS = listOf(7.0, 10.0, 13.0, 17.0, 22.0, 30.0)
        private val SUBORDINATION_ANCHORS = listOf(0.1, 0.25, 0.45, 0.7, 1.0, 1.4)
        private val GRAMMAR_ANCHORS = listOf(0.0, 2.0, 5.0, 10.0, 18.0, 28.0)
        private val RARE_ANCHORS = listOf(0.3, 0.8, 1.5, 2.5, 4.0, 6.5)

        fun levelOf(score: Double): CefrLevel = when {
            score < 1.5 -> CefrLevel.A1
            score < 2.5 -> CefrLevel.A2
            score < 3.5 -> CefrLevel.B1
            score < 4.5 -> CefrLevel.B2
            score < 5.5 -> CefrLevel.C1
            else -> CefrLevel.C2
        }

        internal fun interpolate(x: Double, anchors: List<Double>): Double {
            if (x <= anchors.first()) return 1.0
            if (x >= anchors.last()) return 6.0
            val i = anchors.indexOfFirst { it >= x }
            val (a, b) = anchors[i - 1] to anchors[i]
            return i + (x - a) / (b - a)
        }

        internal fun interpolateLog(x: Double, anchors: List<Double>): Double =
            interpolate(ln(max(x, 1.0)), anchors.map { ln(it) })
    }
}

/** Variante linguística provável, a partir de marcas de ortografia e morfologia. */
object VariantDetector {
    private val gbUs = listOf(
        "colour" to "color", "honour" to "honor", "favour" to "favor", "labour" to "labor", "neighbour" to "neighbor", "behaviour" to "behavior",
        "centre" to "center", "theatre" to "theater", "metre" to "meter", "grey" to "gray", "travelled" to "traveled", "jewellery" to "jewelry",
        "realise" to "realize", "recognise" to "recognize", "apologise" to "apologize", "defence" to "defense", "programme" to "program", "whilst" to "while",
    )

    fun detect(language: Language, text: String): String? {
        val lower = " " + text.lowercase() + " "
        fun count(w: String) = Regex("\\b${Regex.escape(w)}\\w{0,3}\\b").findAll(lower).count()
        return when (language) {
            Language.EN_GB -> {
                val gb = gbUs.sumOf { count(it.first) }
                val us = gbUs.filter { it.second != "while" && it.second != "program" }.sumOf { count(it.second) }
                when {
                    gb >= 3 && gb >= us * 2 -> "Inglês britânico (ortografia: ${gbUs.filter { count(it.first) > 0 }.take(3).joinToString(", ") { it.first }})"
                    us >= 3 && us >= gb * 2 -> "Inglês americano (ortografia: ${gbUs.filter { count(it.second) > 0 }.take(3).joinToString(", ") { it.second }})"
                    else -> null
                }
            }
            Language.ES -> {
                val vosotros = Regex("(?U)\\b(vosotros|vosotras|vuestro|vuestra|vuestros|vuestras|habéis|tenéis|sois|estáis|queréis|podéis|decís|\\w+áis|\\w+éis)\\b").findAll(lower).count()
                if (vosotros >= 3) "Espanhol europeu (formas de \"vosotros\")" else null
            }
            Language.PT_BR -> {
                val br = Regex("\\b(você|vocês|ônibus|trem|fato|registro|time|banheiro|geladeira)\\b").findAll(lower).count()
                val pt = Regex("\\b(comboio|autocarro|facto|registo|equipa|casa de banho|frigorífico|telemóvel)\\b").findAll(lower).count()
                when { br >= 3 && br > pt * 2 -> "Português brasileiro"; pt >= 3 && pt > br * 2 -> "Português europeu"; else -> null }
            }
            else -> null
        }
    }
}
