package app.virgil.engine.analysis

import app.virgil.core.model.CefrLevel
import app.virgil.core.model.Language
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Avaliação contra textos de leitura com nível CEFR conhecido (coleção UniversalCEFR; dados NC, não
 * incluídos no repositório). Executa só se VIRGIL_CEFR_CORPUS apontar para o diretório com os JSON.
 */
class CefrCorpusEvaluation {
    private val dir = System.getenv("VIRGIL_CEFR_CORPUS")?.let(::File)

    data class Sample(val language: Language, val gold: CefrLevel, val text: String, val source: String)

    private fun load(): List<Sample> {
        val files = mapOf(
            "cambridge_exams.json" to Language.EN_GB, "elg-cefr-en.json" to Language.EN_GB, "elg-cefr-de.json" to Language.DE,
            "hablacultura.json" to Language.ES, "kwiziq-es.json" to Language.ES, "kwiziq-fr.json" to Language.FR,
        )
        return files.flatMap { (name, lang) ->
            val f = File(dir, name).takeIf { it.isFile } ?: return@flatMap emptyList()
            (Json.parseToJsonElement(f.readText()) as JsonArray).mapNotNull { el ->
                val o = el.jsonObject
                val label = o["cefr_level"]?.jsonPrimitive?.content?.trimEnd('+') ?: return@mapNotNull null
                val level = CefrLevel.parse(label) ?: return@mapNotNull null
                Sample(lang, level, o["text"]?.jsonPrimitive?.content ?: return@mapNotNull null, name)
            }
        }
    }

    /** Exporta as características de cada texto rotulado (entrada de tools/cefr-calibration/fit.py). */
    @Test fun exportarCaracteristicas() {
        assumeTrue(dir?.isDirectory == true)
        val out = File(System.getenv("VIRGIL_CEFR_OUT") ?: "build/cefr-features.tsv")
        out.parentFile?.mkdirs()
        val analyzers = HashMap<Language, DifficultyAnalyzer>()
        val sb = StringBuilder("source\tlang\tgold\twords\t" + Features.NAMES.joinToString("\t") + "\n")
        var n = 0
        for (s in load()) {
            val a = analyzers.getOrPut(s.language) { DifficultyAnalyzer(s.language) }
            val m = a.measure(s.text.split("\n").filter { it.isNotBlank() })
            if (m.words < DifficultyAnalyzer.MIN_WORDS || m.sentences == 0) continue
            sb.append(listOf(s.source, s.language.iso639, s.gold.ordinal, m.words).joinToString("\t")).append('\t')
                .append(Features.of(m).joinToString("\t") { "%.6f".format(java.util.Locale.ROOT, it) }).append('\n')
            n++
        }
        out.writeText(sb.toString())
        println("Características exportadas: $n textos → $out")
    }

    /** Avalia o modelo embarcado (cefr-model.json) sobre todo o corpus rotulado. */
    @Test fun avaliar() {
        assumeTrue(dir?.isDirectory == true)
        val analyzers = HashMap<Language, DifficultyAnalyzer>()
        var exact = 0; var adjacent = 0; var n = 0
        val confusion = Array(6) { IntArray(6) }
        for (s in load()) {
            val a = analyzers.getOrPut(s.language) { DifficultyAnalyzer(s.language) }
            val sc = a.score(a.measure(s.text.split("\n").filter { it.isNotBlank() })) ?: continue
            val pred = DifficultyAnalyzer.levelOf(sc.combined)
            n++; if (pred == s.gold) exact++; if (kotlin.math.abs(pred.ordinal - s.gold.ordinal) <= 1) adjacent++
            confusion[s.gold.ordinal][pred.ordinal]++
        }
        println("Modelo embarcado (dados de treino — ver fit.py para a validação cruzada): $n textos; exato ${"%.1f".format(exact * 100.0 / n)}%, ±1 nível ${"%.1f".format(adjacent * 100.0 / n)}%")
        confusion.forEachIndexed { i, r -> println("  ${CefrLevel.entries[i].label}: ${r.joinToString(" ") { "%4d".format(it) }}") }
    }

    /**
     * Invariância ao tamanho: concatena textos do mesmo nível e da mesma fonte em "livros" de
     * capítulos de ~1 500 palavras e analisa como o app analisa um livro (analyzeBook).
     */
    @Test fun livrosSinteticosPorNivel() {
        assumeTrue(dir?.isDirectory == true)
        var hits = 0; var adj = 0; var n = 0
        load().groupBy { Triple(it.source, it.language, it.gold) }.toSortedMap(compareBy({ it.first }, { it.third })).forEach { (key, samples) ->
            val paragraphs = samples.flatMap { it.text.split("\n").filter { p -> p.isNotBlank() } }
            val words = paragraphs.sumOf { it.split(" ").size }
            if (words < 4_000) return@forEach
            val chapters = ArrayList<List<String>>(); var cur = ArrayList<String>(); var cw = 0
            for (p in paragraphs) { cur += p; cw += p.split(" ").size; if (cw >= 1_500) { chapters += cur; cur = ArrayList(); cw = 0 } }
            if (cur.isNotEmpty()) chapters += cur
            val (_, book) = DifficultyAnalyzer(key.second).analyzeBook(chapters)
            val pred = DifficultyAnalyzer.levelOf(book!!.score)
            n++; if (pred == key.third) hits++; if (kotlin.math.abs(pred.ordinal - key.third.ordinal) <= 1) adj++
            println("  ${key.first} ${key.third.label}: $words palavras, ${chapters.size} capítulos → ${pred.label} (${book.range.label}, conf. ${"%.2f".format(book.range.confidence)})")
        }
        println("Livros sintéticos: $n · nível exato $hits · ±1 $adj")
    }
}
