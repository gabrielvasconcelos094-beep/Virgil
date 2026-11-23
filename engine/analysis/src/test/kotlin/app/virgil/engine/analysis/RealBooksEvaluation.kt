package app.virgil.engine.analysis

import app.virgil.core.model.Language
import app.virgil.core.text.LanguageDetector
import app.virgil.engine.books.model.ChapterDoc
import app.virgil.engine.books.parse.TxtParser
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Cadeia real do app (TxtParser → idioma → análise por capítulo e do livro) sobre livros completos do
 * Project Gutenberg (domínio público). Executa só com VIRGIL_BOOKS apontando para os .txt.
 */
class RealBooksEvaluation {
    @Test fun livrosReais() {
        val dir = System.getenv("VIRGIL_BOOKS")?.let(::File)
        assumeTrue(dir?.isDirectory == true)
        val out = StringBuilder("arquivo\tidioma_esperado\tidioma_detectado\ttitulo\tpalavras\tcapitulos\tcapitulos_com_nivel\tnivel\tfaixa\tconfianca\tdispersao_capitulos\tmin_cap\tmax_cap\tms\terro\n")
        val levels = HashMap<String, MutableList<String>>()
        var failures = 0; var langErrors = 0; var total = 0
        dir!!.listFiles()!!.filter { it.extension == "txt" }.sortedBy { it.name }.forEach { f ->
            total++
            val expected = Language.fromTag(f.name.substringBefore('_'))!!
            val t0 = System.currentTimeMillis()
            try {
                val docs = ArrayList<ChapterDoc>()
                val book = TxtParser().parse(f, f.nameWithoutExtension) { docs += it }
                val sample = docs.asSequence().flatMap { it.blocks }.map { it.text }.joinToString("\n").take(40_000)
                val lang = LanguageDetector.resolve(sample, Language.fromTag(book.meta.language))
                if (lang != expected) langErrors++
                val analyzer = DifficultyAnalyzer(lang ?: expected)
                val (chapters, result) = analyzer.analyzeBook(docs.map { d -> d.blocks.map { it.text }.filter { it.isNotBlank() } })
                val chLevels = chapters.mapNotNull { it.scores?.combined }
                val ms = System.currentTimeMillis() - t0
                val level = result?.let { DifficultyAnalyzer.levelOf(it.score).label } ?: "-"
                levels.getOrPut(expected.iso639) { mutableListOf() } += level
                out.append(listOf(f.name, expected.iso639, lang?.iso639 ?: "-", (book.meta.title ?: "?").take(60), result?.metrics?.words ?: 0,
                    docs.size, chLevels.size, level, result?.range?.label ?: "-", "%.2f".format(result?.range?.confidence ?: 0f),
                    "%.2f".format(DifficultyAnalyzer.chapterSpread(chLevels)), chLevels.minOrNull()?.let { DifficultyAnalyzer.levelOf(it).label } ?: "-",
                    chLevels.maxOrNull()?.let { DifficultyAnalyzer.levelOf(it).label } ?: "-", ms, "").joinToString("\t")).append('\n')
            } catch (e: Throwable) {
                failures++
                out.append("${f.name}\t${expected.iso639}\t-\t-\t0\t0\t0\t-\t-\t0\t0\t-\t-\t0\t${e.javaClass.simpleName}: ${e.message}\n")
            }
        }
        File(System.getenv("VIRGIL_BOOKS_OUT") ?: "build/real-books.tsv").writeText(out.toString())
        println("Livros: $total · falhas: $failures · idioma divergente: $langErrors")
        levels.toSortedMap().forEach { (l, v) -> println("  $l: " + v.groupingBy { it }.eachCount().toSortedMap().entries.joinToString(" ") { "${it.key}=${it.value}" }) }
    }
}
