package app.virgil.engine.translation

import app.virgil.core.model.Language
import app.virgil.core.model.SelectionKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PromptsTest {
    @Test fun `prompt de palavra coloca a frase antes da palavra (reaproveitamento do KV)`() {
        val a = Prompts.term(PromptFamily.TranslateGemma, Language.EN_GB, "She is a bright student.", "bright")
        val b = Prompts.term(PromptFamily.TranslateGemma, Language.EN_GB, "She is a bright student.", "student")
        val common = a.commonPrefixWith(b)
        assertThat(common).contains("She is a bright student.")
        assertThat(a).startsWith("<start_of_turn>user\nYou are a professional English (en-GB) to Portuguese (pt-BR) translator.")
        assertThat(a).endsWith("<end_of_turn>\n<start_of_turn>model\n")
    }

    @Test fun `variantes regionais respeitadas no prompt`() {
        assertThat(Prompts.text(PromptFamily.TranslateGemma, Language.ES, "Hola")).contains("Spanish (es-ES)")
        assertThat(Prompts.text(PromptFamily.HyMT, Language.FR, "Bonjour")).contains("Brazilian Portuguese")
    }

    @Test fun `limpeza da resposta de palavra`() {
        assertThat(PostProcess.term("\"Mal\".\n", "scarcely", Language.EN_GB)).isEqualTo("mal")
        assertThat(PostProcess.term("Translation: promissor", "bright", Language.EN_GB)).isEqualTo("promissor")
        assertThat(PostProcess.term("Banco", "Bank", Language.DE)).isEqualTo("banco")
        assertThat(PostProcess.term("Bennet", "Bennet", Language.EN_GB)).isEqualTo("Bennet")
        assertThat(PostProcess.term("ela mal podia acreditar no que tinha visto", "scarcely", Language.EN_GB)).isEqualTo("ela mal podia acreditar no que…")
    }

    @Test fun `recomendacao por memoria`() {
        val gb = 1024L * 1024 * 1024
        assertThat(ModelCatalog.recommendedFor(4 * gb)).isEqualTo(ModelCatalog.HyMT2)
        assertThat(ModelCatalog.recommendedFor((5.6 * gb).toLong())).isEqualTo(ModelCatalog.TranslateGemma4B)
        assertThat(ModelCatalog.recommendedFor(12 * gb)).isEqualTo(ModelCatalog.TranslateGemma4B)
    }

    @Test fun `chave de cache depende do modelo e do contexto da palavra`() {
        val r = TranslationRequest(SelectionKind.Word, Language.EN_GB, "bright", "She is a bright student.")
        val k1 = LocalTranslationEngine.cacheKey(ModelCatalog.TranslateGemma4B, r)
        assertThat(LocalTranslationEngine.cacheKey(ModelCatalog.TranslateGemma4B, r)).isEqualTo(k1)
        assertThat(LocalTranslationEngine.cacheKey(ModelCatalog.HyMT2, r)).isNotEqualTo(k1)
        assertThat(LocalTranslationEngine.cacheKey(ModelCatalog.TranslateGemma4B, r.copy(context = "His future looks bright."))).isNotEqualTo(k1)
    }

    @Test fun janelaDeContexto() {
        val short = "She could scarcely believe what she had witnessed."
        org.junit.Assert.assertEquals(short, Prompts.contextWindow(short, "scarcely"))
        val long = (1..200).joinToString(" ") { "w$it" }
        val win = Prompts.contextWindow(long, "w100", radius = 5)
        org.junit.Assert.assertEquals("… w95 w96 w97 w98 w99 w100 w101 w102 w103 w104 w105 …", win)
    }
    @Test fun chaveDeCacheNormalizada() {
        val spec = ModelCatalog.TranslateGemma4B
        val a = LocalTranslationEngine.cacheKey(spec, TranslationRequest(app.virgil.core.model.SelectionKind.Word, app.virgil.core.model.Language.EN_GB, "don’t", "I  don’t   know."))
        val b = LocalTranslationEngine.cacheKey(spec, TranslationRequest(app.virgil.core.model.SelectionKind.Word, app.virgil.core.model.Language.EN_GB, "don't", "I don't know."))
        org.junit.Assert.assertEquals(a, b)
        org.junit.Assert.assertNotEquals(LocalTranslationEngine.cacheKey(spec, TranslationRequest(app.virgil.core.model.SelectionKind.Word, app.virgil.core.model.Language.DE, "Bank", "Die Bank.")) , LocalTranslationEngine.cacheKey(spec, TranslationRequest(app.virgil.core.model.SelectionKind.Word, app.virgil.core.model.Language.DE, "bank", "Die Bank.")))
    }

}
