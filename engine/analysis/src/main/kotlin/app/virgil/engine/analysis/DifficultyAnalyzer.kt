package app.virgil.engine.analysis

import app.virgil.core.model.CefrLevel
import app.virgil.core.model.CefrRange
import app.virgil.core.model.Language
import app.virgil.core.text.TextSegmenter
import kotlinx.serialization.Serializable
import java.util.zip.GZIPInputStream
import kotlin.math.abs
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
    /** Histograma de posições de frequência (faixas) para cobertura lexical. */
    val rankHistogram: List<Int> = List(RANK_BUCKETS.size + 1) { 0 },
    val sentenceLengths: List<Int> = emptyList(),
    val archaicExamples: List<String> = emptyList(),
    val literaryExamples: List<String> = emptyList(),
    // v2
    val letters: Long = 0,               // letras nas palavras contadas (comprimento médio da palavra)
    val commas: Int = 0,
    val passives: Int = 0,
    val conditionals: Int = 0,
    val similes: Int = 0,
    /** MTLD × palavras, para agregar a diversidade lexical ponderada pelo tamanho. */
    val mtldWeighted: Double = 0.0,
    val idiomExamples: List<String> = emptyList(),
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
        letters + o.letters, commas + o.commas, passives + o.passives, conditionals + o.conditionals, similes + o.similes,
        mtldWeighted + o.mtldWeighted, (idiomExamples + o.idiomExamples).distinct().take(6),
    )

    val meanSentenceLength: Double get() = if (sentences == 0) 0.0 else words.toDouble() / sentences
    val p90SentenceLength: Int get() = sentenceLengths.sorted().let { if (it.isEmpty()) 0 else it[(it.size * 0.9).toInt().coerceAtMost(it.lastIndex)] }
    val mtld: Double get() = if (words == 0) 0.0 else mtldWeighted / words

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

    /** Fração das palavras (sem nomes próprios) com posição de frequência acima de [rank]. */
    fun fractionAbove(rank: Int): Double {
        val counted = rankHistogram.sum()
        if (counted == 0) return 0.0
        val idx = RANK_BUCKETS.indexOf(rank)
        require(idx >= 0) { "limite fora das faixas: $rank" }
        return rankHistogram.drop(idx + 1).sum().toDouble() / counted
    }

    companion object {
        /** Limites superiores das faixas de frequência. Última faixa implícita: desconhecidas. */
        val RANK_BUCKETS = listOf(250, 500, 750, 1000, 1500, 2000, 2500, 3000, 4000, 5000, 6000, 8000, 10_000, 12_500, 15_000, 20_000, 25_000, 30_000, 40_000, 50_000)
        fun bucketOf(rank: Int): Int = RANK_BUCKETS.indexOfFirst { rank <= it }.let { if (it < 0) RANK_BUCKETS.size else it }
    }
}

/**
 * Pontuações por dimensão na escala CEFR contínua (1 = A1 … 6 = C2). [combined] é a estimativa do
 * modelo; as dimensões mostram o que o modelo preveria se só aquele grupo de características se
 * afastasse da média — explicam a nota, não a substituem.
 */
@Serializable
data class DimensionScores(
    val vocabulary: Double,
    val syntax: Double,
    val grammar: Double,
    val style: Double,
    val combined: Double,
) {
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
 * Estimativa de dificuldade de leitura (ADR-0004, versão 2).
 *
 * 1. [measure] extrai características linguísticas do texto real do livro (vocabulário por faixas de
 *    frequência, diversidade lexical MTLD, comprimento de palavras e frases, vírgulas, subordinação,
 *    voz passiva, condicionais, formas verbais literárias, arcaísmos, expressões, comparações,
 *    diálogo, nomes próprios).
 * 2. [score] aplica o [CefrModel]: regressão ordinal ajustada em ~2 800 textos de leitura com nível
 *    CEFR conhecido (inglês, alemão, espanhol, francês; ver docs/adr/0004 e tools/cefr-calibration).
 * 3. [rangeFor] transforma o erro medido na validação cruzada em faixa e confiança.
 */
class DifficultyAnalyzer(
    private val language: Language,
    private val freq: FrequencyList = FrequencyList.load(language),
    private val model: CefrModel = CefrModel.default,
) {
    private val res = LanguageResources.forLanguage(language)
    private val segmenter = TextSegmenter(language)

    /** Analisa os blocos de texto de um capítulo. */
    fun measure(blocks: List<String>): TextMetrics {
        val hist = IntArray(TextMetrics.RANK_BUCKETS.size + 1)
        val lengths = ArrayList<Int>()
        val tokens = ArrayList<String>()
        var words = 0; var sentences = 0; var longS = 0; var subs = 0; var lit = 0; var arch = 0
        var parts = 0; var idioms = 0; var proper = 0; var rare = 0; var unknown = 0; var dialogue = 0; var chars = 0
        var letters = 0L; var commas = 0; var passives = 0; var conditionals = 0; var similes = 0
        val archEx = LinkedHashSet<String>(); val litEx = LinkedHashSet<String>(); val idiomEx = LinkedHashSet<String>()
        for (block in blocks) {
            if (block.isBlank()) continue
            chars += block.length
            dialogue += dialogueChars(block)
            commas += block.count { it == ',' || it == ';' }
            val lower = block.lowercase()
            res.literaryForms.forEach { r -> r.findAll(block).forEach { lit++; if (litEx.size < 6) litEx += it.value } }
            res.archaicPatterns.forEach { r -> r.findAll(block).forEach { arch++; if (archEx.size < 6) archEx += it.value } }
            res.passive.forEach { r -> passives += r.findAll(block).count() }
            res.conditional.forEach { r -> conditionals += r.findAll(block).count() }
            res.similes.forEach { r -> similes += r.findAll(block).count() }
            res.idioms.forEach { idiom ->
                var i = lower.indexOf(idiom)
                while (i >= 0) {
                    // Só conta se estiver isolada por fronteiras de palavra.
                    val before = if (i == 0) ' ' else lower[i - 1]
                    val after = lower.getOrElse(i + idiom.length) { ' ' }
                    if (!before.isLetter() && !after.isLetter()) { idioms++; if (idiomEx.size < 6) idiomEx += idiom }
                    i = lower.indexOf(idiom, i + idiom.length)
                }
            }
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
                    letters += norm.count { it.isLetter() }
                    tokens += norm
                    if (norm in res.subordinators) subs++
                    if (norm in res.archaisms) { arch++; if (archEx.size < 6) archEx += token }
                    if (res.particles.isNotEmpty() && norm in res.particles && isClauseFinalParticle(sentence, r, idx, ws.size)) parts++
                    val rank = freq.rank(norm)
                    val capitalized = token.first().isUpperCase()
                    // Nomes próprios ficam fora do vocabulário: maiúscula fora do início da frase (ou em alemão) e forma ausente da lista.
                    if (capitalized && rank == null && (idx > 0 || language == Language.DE)) { proper++; return@forEachIndexed }
                    if (capitalized && idx > 0 && language != Language.DE && rank != null && rank > 3_000 && isLikelyName(token)) { proper++; return@forEachIndexed }
                    val effective = rank ?: 60_000
                    if (effective > 20_000) rare++
                    if (rank == null) unknown++
                    hist[TextMetrics.bucketOf(effective)]++
                }
            }
        }
        return TextMetrics(
            words, sentences, longS, subs, lit, arch, parts, idioms, proper, rare, unknown, dialogue, chars,
            hist.toList(), if (lengths.size > 2000) lengths.shuffled(java.util.Random(3)).take(2000) else lengths,
            archEx.toList(), litEx.toList(),
            letters, commas, passives, conditionals, similes, mtld(tokens) * words, idiomEx.toList(),
        )
    }

    fun score(m: TextMetrics): DimensionScores? {
        if (m.words < MIN_WORDS || m.sentences == 0) return null
        return model.score(Features.of(m), language)
    }

    /**
     * Faixa exibida e confiança. A meia-largura vem do erro absoluto medido na validação cruzada
     * (por idioma, quando houve dados); a confiança é a fração de textos de validação cujo nível real
     * caiu dentro de uma faixa assim, reduzida para trechos curtos e quando os capítulos divergem.
     */
    fun rangeFor(scores: DimensionScores, words: Int, chapterSpread: Double = 0.0): CefrRange {
        val c = scores.combined
        val half = (model.halfWidth(language) + 0.5 * chapterSpread).coerceIn(0.25, 1.0)
        val low = levelOf(c - half)
        val high = levelOf(c + half)
        val lengthFactor = min(1.0, max(0.3, ln(max(words, 1).toDouble()) / ln(2_000.0)))
        val width = high.ordinal - low.ordinal + 1
        val coverage = model.coverage(language, width)
        return CefrRange(low, maxOf(low, high), (coverage * lengthFactor).toFloat().coerceIn(0f, 0.95f))
    }

    fun analyzeBook(chapters: List<List<String>>): Pair<List<ChapterResult>, BookResult?> {
        val perChapter = chapters.map { blocks ->
            val m = measure(blocks)
            val s = score(m)
            ChapterResult(m, s, s?.let { rangeFor(it, m.words) })
        }
        val total = perChapter.fold(TextMetrics()) { acc, c -> acc + c.metrics }
        val scores = score(total) ?: return perChapter to null
        val range = rangeFor(scores, total.words, chapterSpread(perChapter.mapNotNull { it.scores?.combined }))
        val text = chapters.asSequence().flatten().take(400).joinToString(" ")
        return perChapter to BookResult(total, scores, range, scores.combined, findings(total, scores), VariantDetector.detect(language, text))
    }

    /** Observações em português, das mais relevantes para as menos. */
    fun findings(m: TextMetrics, s: DimensionScores): List<String> {
        val out = ArrayList<Pair<Double, String>>()
        val per1000 = { n: Int -> if (m.words == 0) 0.0 else n * 1000.0 / m.words }
        val v95 = m.vocabularyFor(0.95)
        val above8k = m.fractionAbove(8_000) * 100
        out += s.vocabulary to when {
            v95 >= 60_000 -> "Vocabulário muito diversificado: mais de 5% das palavras estão fora das 50 mil formas mais comuns do idioma."
            else -> "Vocabulário: reconhecer 95% das palavras exige conhecer cerca de ${fmt(v95)} formas frequentes; ${"%.1f".format(above8k).replace('.', ',')}% das palavras estão além das 8 mil mais comuns."
        }
        val mean = m.meanSentenceLength
        val longPct = if (m.sentences == 0) 0 else m.longSentences * 100 / m.sentences
        out += s.syntax to when {
            mean >= 22 -> "Frases longas: média de ${mean.toInt()} palavras; $longPct% passam de 30 palavras, com muitas orações encadeadas."
            mean >= 14 -> "Frases de extensão média (${mean.toInt()} palavras em média), com orações subordinadas frequentes."
            else -> "Frases curtas e diretas (${mean.toInt()} palavras em média)."
        }
        if (per1000(m.literaryForms) >= 3) {
            out += s.grammar to "Formas verbais literárias frequentes: ${res.literaryLabel}" + (m.literaryExamples.takeIf { it.isNotEmpty() }?.let { " — ex.: ${it.take(3).joinToString(", ")}" } ?: "") + "."
        }
        if (per1000(m.passives) >= 4) out += s.grammar - 0.2 to "Voz passiva recorrente (${"%.0f".format(per1000(m.passives))} ocorrências a cada mil palavras)."
        if (per1000(m.conditionals) >= 4) out += s.grammar - 0.3 to "Muitas construções condicionais e hipotéticas."
        if (per1000(m.particles) >= 4) {
            out += 3.5 to (if (language == Language.EN_GB) "Muitos phrasal verbs (verbo + partícula), cujo sentido nem sempre se deduz das partes."
            else "Muitos verbos separáveis: a partícula aparece no fim da oração e muda o sentido do verbo.")
        }
        if (m.archaisms > 3 && per1000(m.archaisms) >= 0.5) {
            out += 4.5 to "Arcaísmos ou ortografia antiga" + (m.archaicExamples.takeIf { it.isNotEmpty() }?.let { ": ${it.take(4).joinToString(", ")}" } ?: "") + "."
        }
        if (per1000(m.idioms) >= 0.8) out += 3.0 to "Expressões idiomáticas recorrentes" + (m.idiomExamples.takeIf { it.isNotEmpty() }?.let { " (${it.take(3).joinToString(", ") { e -> "“$e”" }})" } ?: "") + "."
        if (per1000(m.similes) >= 2) out += 3.2 to "Comparações e imagens frequentes (“como se”, “como um…”), sinal de linguagem figurada."
        val dialoguePct = if (m.totalChars == 0) 0 else m.dialogueChars * 100 / m.totalChars
        if (dialoguePct >= 25) out += 2.0 to "Bastante diálogo ($dialoguePct% do texto), o que costuma tornar a leitura mais ágil."
        else if (dialoguePct < 5 && m.words > 3000) out += 2.5 to "Pouco diálogo: narração contínua, mais densa."
        return out.sortedByDescending { it.first }.map { it.second }.take(6)
    }

    /** Resumo em uma frase, para a tela de análise. */
    fun summary(s: DimensionScores): String {
        val dims = listOf("o vocabulário" to s.vocabulary, "a sintaxe" to s.syntax, "as formas verbais" to s.grammar, "o estilo" to s.style)
        val hardest = dims.maxBy { it.second }
        val easiest = dims.minBy { it.second }
        return if (hardest.second - easiest.second < 0.6) {
            "A dificuldade é equilibrada: nenhum aspecto se destaca muito dos demais."
        } else {
            "O principal fator de dificuldade é ${hardest.first} (${levelOf(hardest.second).label}); ${easiest.first} é o aspecto mais acessível (${levelOf(easiest.second).label})."
        }
    }

    private fun normalize(token: String): String =
        token.lowercase().replace('’', '\'').trim('\'', '-').let { w -> if (w.endsWith('\'')) w.dropLast(1) else w }

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
        DIALOGUE.findAll(block).forEach { n += it.value.length }
        return n
    }

    private fun fmt(n: Int) = "%,d".format(n).replace(',', '.')

    companion object {
        /** Versão do analisador: mudar força nova análise dos livros já analisados. */
        const val VERSION = 2
        /** Abaixo disso o texto não recebe nível (a amostra não é informativa). */
        const val MIN_WORDS = 40
        private val DIALOGUE = Regex("[“\"«„»‹][^”\"»“«›]{2,}[”\"»“«›]")

        fun levelOf(score: Double): CefrLevel = when {
            score < 1.5 -> CefrLevel.A1
            score < 2.5 -> CefrLevel.A2
            score < 3.5 -> CefrLevel.B1
            score < 4.5 -> CefrLevel.B2
            score < 5.5 -> CefrLevel.C1
            else -> CefrLevel.C2
        }

        fun chapterSpread(scores: List<Double>): Double =
            if (scores.size < 2) 0.0 else scores.average().let { mu -> sqrt(scores.sumOf { (it - mu) * (it - mu) } / scores.size) }

        /**
         * MTLD (McCarthy & Jarvis, 2010): comprimento médio de segmentos em que a razão tipo/ocorrência
         * se mantém acima de 0,72; média das passagens direta e inversa. Robusto ao tamanho do texto.
         */
        fun mtld(tokens: List<String>, threshold: Double = 0.72): Double {
            if (tokens.size < 10) return 0.0
            fun pass(seq: List<String>): Double {
                var factors = 0.0
                val types = HashSet<String>()
                var count = 0
                for (t in seq) {
                    count++; types += t
                    if (types.size.toDouble() / count <= threshold) { factors += 1; types.clear(); count = 0 }
                }
                if (count > 0) {
                    val ttr = types.size.toDouble() / count
                    factors += (1 - ttr) / (1 - threshold)
                }
                return if (factors == 0.0) seq.size.toDouble() else seq.size / factors
            }
            return (pass(tokens) + pass(tokens.asReversed())) / 2
        }
    }
}

/** Vetor de características usado pelo modelo (a mesma ordem é usada na calibração). */
object Features {
    val NAMES = listOf(
        "logMeanSentLen", "logP90SentLen", "meanWordLen", "fracOver1k", "fracOver3k", "fracOver8k", "fracOver20k",
        "fracUnknown", "logV90", "mtld", "subPerSent", "commasPerSent", "passivePer1k", "conditionalPer1k",
        "literaryPer1k", "archaicPer1k", "particlesPer1k", "idiomsPer1k", "similesPer1k", "dialogueFrac", "properFrac",
    )

    /** Grupos para explicar a nota (índices em [NAMES]). */
    val GROUPS = mapOf(
        "vocabulary" to listOf(2, 3, 4, 5, 6, 7, 8, 9),
        "syntax" to listOf(0, 1, 10, 11),
        "grammar" to listOf(12, 13, 14, 16),
        "style" to listOf(15, 17, 18, 19, 20),
    )

    fun of(m: TextMetrics): DoubleArray {
        val w = max(m.words, 1).toDouble()
        val s = max(m.sentences, 1).toDouble()
        val per1k = { n: Int -> n * 1000.0 / w }
        return doubleArrayOf(
            ln(m.meanSentenceLength + 1),
            ln(m.p90SentenceLength + 1.0),
            m.letters / w,
            m.fractionAbove(1_000),
            m.fractionAbove(3_000),
            m.fractionAbove(8_000),
            m.fractionAbove(20_000),
            m.unknownTokens / w,
            ln(max(m.vocabularyFor(0.90), 1).toDouble()),
            min(m.mtld, 300.0) / 100,
            m.subordinators / s,
            m.commas / s,
            per1k(m.passives),
            per1k(m.conditionals),
            per1k(m.literaryForms),
            per1k(m.archaisms),
            per1k(m.particles),
            per1k(m.idioms),
            per1k(m.similes),
            if (m.totalChars == 0) 0.0 else m.dialogueChars.toDouble() / m.totalChars,
            m.properNouns / (w + m.properNouns),
        )
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
