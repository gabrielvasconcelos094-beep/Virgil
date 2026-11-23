package app.virgil.engine.translation

import app.virgil.core.model.Language
import app.virgil.engine.llama.GenerationResult
import app.virgil.engine.llama.LlamaSession
import app.virgil.engine.llama.LoadOptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Diagnóstico de latência com o modelo real (na JVM do computador; os tempos absolutos do celular
 * serão outros, mas a decomposição — carga, tokens reavaliados, decodificação — vale igual).
 * Compara o prompt de palavra atual (frase antes da instrução) com a variante "instrução antes",
 * que deixa a parte fixa sempre no KV cache. Mede também a qualidade das duas no conjunto de
 * palavras do benchmark (tools/mt-benchmark/testset.json).
 * Requer VIRGIL_LLAMA_HOST_LIB e VIRGIL_TEST_MODEL; grava o relatório em VIRGIL_PROFILE_OUT, se definido.
 */
class LatencyProfileHostTest {
    private val lib = System.getProperty("virgil.llama.lib")
    private val model = System.getProperty("virgil.test.model")
    private val out = System.getenv("VIRGIL_PROFILE_OUT")

    private data class Word(val lang: Language, val sentence: String, val word: String, val expected: List<String>)

    private fun words(): List<Word> {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "tools/mt-benchmark/testset.json").isFile }
        val j = Json.parseToJsonElement(File(root, "tools/mt-benchmark/testset.json").readText()).jsonObject
        return j.getValue("words").jsonArray.map { e ->
            val o = e.jsonObject
            Word(
                Language.fromTag(o.getValue("lang").jsonPrimitive.content)!!, o.getValue("sentence").jsonPrimitive.content,
                o.getValue("word").jsonPrimitive.content, o.getValue("expected").jsonArray.map { it.jsonPrimitive.content },
            )
        }
    }

    private val report = StringBuilder()
    private fun log(s: String) { println(s); report.appendLine(s) }

    private fun row(label: String, r: GenerationResult, extra: String = "") =
        log("| $label | ${r.promptTokens} | ${r.reusedTokens} | ${r.promptTokens - r.reusedTokens} | ${r.promptMillis} | ${r.generatedTokens} | ${r.totalMillis} | $extra |")

    @Test fun perfil() {
        assumeTrue("modelo de teste não configurado", lib != null && model != null)
        LlamaSession.initialize(null, lib)
        val threads = (System.getenv("VIRGIL_THREADS") ?: "4").toInt()
        val t0 = System.nanoTime()
        val s = LlamaSession.load(File(model!!), LoadOptions(contextTokens = 2048, threads = threads))
        val loadMs = (System.nanoTime() - t0) / 1_000_000
        val family = if (model.contains("Hy-MT", ignoreCase = true)) PromptFamily.HyMT else PromptFamily.TranslateGemma
        log("## Perfil de latência — ${File(model).name}, $threads threads, CPU do host (x86-64)")
        log("")
        log("Carga do modelo (mmap, sem repack): **$loadMs ms**")
        log("")
        log("| consulta | tokens do prompt | reaproveitados (KV) | avaliados | ms prompt | tokens gerados | ms total | saída |")
        log("|---|---|---|---|---|---|---|---|")
        val ws = words()
        // 1) primeira consulta "a frio" (KV vazio) e consultas seguidas, prompt atual
        val current = ArrayList<Pair<Word, String>>()
        ws.forEachIndexed { i, w ->
            val r = s.generate(Prompts.term(family, w.lang, w.sentence, w.word), 12, Prompts.repeatPenalty(family)) { t -> !(t.contains('\n') && t.substringBefore('\n').isNotBlank()) }
            val o = PostProcess.term(r.text, w.word, w.lang)
            current += w to o
            if (i < 4) row("atual · ${w.word}", r, o)
        }
        val tCur = timeAll(s, ws, family) { w -> Prompts.term(family, w.lang, w.sentence, w.word) }
        val tAlt = timeAll(s, ws, family) { w -> Prompts.termPrefixFirst(family, w.lang, w.sentence, w.word) }
        // 2) duas palavras da mesma frase em sequência (caso comum na leitura)
        val sentence = "The old man was thin and gaunt with deep wrinkles in the back of his neck."
        s.generate(Prompts.term(family, Language.EN_GB, sentence, "gaunt"), 12)
        row("atual · 2ª palavra da mesma frase", s.generate(Prompts.term(family, Language.EN_GB, sentence, "wrinkles"), 12))
        s.generate(Prompts.termPrefixFirst(family, Language.EN_GB, sentence, "gaunt"), 12)
        row("instrução antes · 2ª palavra da mesma frase", s.generate(Prompts.termPrefixFirst(family, Language.EN_GB, sentence, "wrinkles"), 12))
        // 3) frase e parágrafo (com tempo até o primeiro texto)
        for ((label, text) in listOf(
            "frase (20 palavras)" to "It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.",
            "parágrafo (60 palavras)" to "However little known the feelings or views of such a man may be on his first entering a neighbourhood, this truth is so well fixed in the minds of the surrounding families, that he is considered the rightful property of some one or other of their daughters. My dear Mr. Bennet, said his lady to him one day, have you heard that Netherfield Park is let at last?",
        )) {
            var first = -1L
            val st = System.nanoTime()
            val r = s.generate(Prompts.text(family, Language.EN_GB, text), (s.countTokens(text) * 2 + 24).coerceIn(32, 900), Prompts.repeatPenalty(family)) { t ->
                if (first < 0 && t.isNotBlank()) first = (System.nanoTime() - st) / 1_000_000; true
            }
            row(label, r, "primeiro texto em $first ms; ${"%.1f".format(r.generatedTokens * 1000.0 / (r.totalMillis - r.promptMillis).coerceAtLeast(1))} tokens/s")
        }
        // 4) qualidade das duas variantes do prompt de palavra
        val alt = ws.map { w ->
            val r = s.generate(Prompts.termPrefixFirst(family, w.lang, w.sentence, w.word), 12, Prompts.repeatPenalty(family)) { t -> !(t.contains('\n') && t.substringBefore('\n').isNotBlank()) }
            PostProcess.term(r.text, w.word, w.lang)
        }
        fun ok(o: String, w: Word) = w.expected.any { e -> o.lowercase().contains(e.lowercase()) }
        val okCur = current.count { (w, o) -> ok(o, w) }
        val okAlt = ws.indices.count { ok(alt[it], ws[it]) }
        log("")
        log("Média por palavra (${ws.size} palavras do benchmark, frases diferentes, modelo já carregado): atual **${tCur} ms**, instrução antes **${tAlt} ms**")
        log("Acertos (tradução contém uma das referências): atual **$okCur/${ws.size}**, instrução antes **$okAlt/${ws.size}**")
        log("")
        log("| palavra | frase | atual | instrução antes |")
        log("|---|---|---|---|")
        ws.forEachIndexed { i, w -> log("| ${w.word} | ${w.sentence.take(60)} | ${current[i].second} | ${alt[i]} |") }
        s.close()
        out?.let { File(it).writeText(report.toString()) }
    }

    private fun timeAll(s: LlamaSession, ws: List<Word>, family: PromptFamily, prompt: (Word) -> String): Long {
        val times = ws.map { w ->
            s.generate(prompt(w), 12, Prompts.repeatPenalty(family)) { t -> !(t.contains('\n') && t.substringBefore('\n').isNotBlank()) }.totalMillis
        }
        return times.sum() / times.size
    }
}
