package app.virgil.engine.llama

import com.google.common.truth.Truth.assertThat
import org.junit.AfterClass
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Testa a ponte JNI com um modelo real, na JVM do computador de desenvolvimento.
 * Executa apenas quando as variáveis estão definidas (não roda no CI comum):
 *   VIRGIL_LLAMA_HOST_LIB=/caminho/libvirgil_llama.so   (cmake -S engine/llama/src/main/cpp -B build)
 *   VIRGIL_TEST_MODEL=/caminho/translategemma-4b-it-Q4_K_M.gguf
 */
class LlamaHostTest {
    companion object {
        private var session: LlamaSession? = null

        @BeforeClass @JvmStatic fun load() {
            val lib = System.getProperty("virgil.llama.lib") ?: return
            val model = System.getProperty("virgil.test.model") ?: return
            LlamaSession.initialize(nativeLibDir = null, libraryPath = lib)
            session = LlamaSession.load(File(model), LoadOptions(contextTokens = 2048, threads = 4))
        }

        @AfterClass @JvmStatic fun close() { session?.close() }

        private fun gemma(user: String) = "<start_of_turn>user\n$user<end_of_turn>\n<start_of_turn>model\n"
        private const val PREFIX = "In the following sentence, the English word or expression "
    }

    private fun s(): LlamaSession { assumeTrue("modelo de teste não configurado", session != null); return session!! }

    private fun wordPrompt(sentence: String, word: String) = gemma(
        "Sentence: $sentence\n" + PREFIX + "\"$word\" is used in a specific sense. " +
            "Give the Brazilian Portuguese translation of \"$word\" as used in this sentence. Output only the translation (one to three words), nothing else.",
    )

    @Test fun `traduz palavra em contexto com acentos intactos`() {
        val r = s().generate(wordPrompt("She is a bright student.", "bright"), maxTokens = 12)
        println("bright (student) -> '${r.text.trim()}' em ${r.totalMillis} ms, prompt=${r.promptTokens}, gerados=${r.generatedTokens}")
        assertThat(r.status).isEqualTo(GenerationStatus.Ok)
        assertThat(r.text.trim()).isNotEmpty()
        val p = s().generate(gemma("Translate into Brazilian Portuguese. Output only the translation:\n\nThe cat is on the table."), maxTokens = 40)
        println("frase -> '${p.text.trim()}'")
        assertThat(p.text).containsMatch("[áàâãéêíóôõúç]|gato")
    }

    @Test fun `segunda consulta na mesma frase reaproveita o prefixo do KV`() {
        val sentence = "It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife."
        val first = s().generate(wordPrompt(sentence, "fortune"), maxTokens = 8)
        val second = s().generate(wordPrompt(sentence, "want"), maxTokens = 8)
        println("fortune -> '${first.text.trim()}' (${first.totalMillis} ms, reaproveitados ${first.reusedTokens}/${first.promptTokens})")
        println("want -> '${second.text.trim()}' (${second.totalMillis} ms, reaproveitados ${second.reusedTokens}/${second.promptTokens})")
        assertThat(second.reusedTokens).isGreaterThan(second.promptTokens / 2)
        assertThat(second.promptMillis).isLessThan(first.promptMillis + 1)
    }

    @Test fun `callback interrompe a geracao`() {
        var pieces = 0
        val r = s().generate(gemma("Translate into Brazilian Portuguese:\n\nFor having lived in Westminster—how many years now? over twenty,—one feels even in the midst of the traffic a particular hush."), maxTokens = 200) {
            pieces++; pieces < 3
        }
        assertThat(r.generatedTokens).isAtMost(4)
        // Depois de interromper, a sessão continua utilizável.
        assertThat(s().generate(wordPrompt("His future looks bright.", "bright"), maxTokens = 8).status).isEqualTo(GenerationStatus.Ok)
    }

    @Test fun `cancel de outra thread encerra rapidamente`() {
        val session = s()
        val t = Thread { Thread.sleep(400); session.cancel() }
        t.start()
        val r = session.generate(gemma("Write the Brazilian Portuguese translation, then repeat it ten times:\n\nThe tide is full, the moon lies fair upon the straits."), maxTokens = 400)
        t.join()
        println("cancelado após ${r.totalMillis} ms com ${r.generatedTokens} tokens: ${r.status}")
        assertThat(r.status).isEqualTo(GenerationStatus.Cancelled)
        assertThat(r.totalMillis).isLessThan(3_000L)
    }
}
