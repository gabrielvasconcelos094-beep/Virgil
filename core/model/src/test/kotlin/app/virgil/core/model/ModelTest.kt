package app.virgil.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LanguageTest {
    @Test fun `resolve codigos de metadados comuns`() {
        assertThat(Language.fromTag("en")).isEqualTo(Language.EN_GB)
        assertThat(Language.fromTag("en-US")).isEqualTo(Language.EN_GB)
        assertThat(Language.fromTag("EN_gb")).isEqualTo(Language.EN_GB)
        assertThat(Language.fromTag("fr-CA")).isEqualTo(Language.FR)
        assertThat(Language.fromTag("ger")).isEqualTo(Language.DE)
        assertThat(Language.fromTag("spa")).isEqualTo(Language.ES)
        assertThat(Language.fromTag("pt-PT")).isEqualTo(Language.PT_BR)
        assertThat(Language.fromTag(" it ")).isEqualTo(Language.IT)
    }

    @Test fun `idioma desconhecido ou vazio retorna nulo`() {
        assertThat(Language.fromTag("ja")).isNull()
        assertThat(Language.fromTag("")).isNull()
        assertThat(Language.fromTag(null)).isNull()
    }

    @Test fun `portugues nao e idioma de estudo`() {
        assertThat(Language.Learnable).doesNotContain(Language.PT_BR)
        assertThat(Language.Learnable).hasSize(5)
    }
}

class CefrTest {
    @Test fun `rotulo de faixa`() {
        assertThat(CefrRange(CefrLevel.B2, CefrLevel.C1, 0.6f).label).isEqualTo("B2–C1")
        assertThat(CefrRange(CefrLevel.B1, CefrLevel.B1, 0.6f).label).isEqualTo("B1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `faixa invertida e rejeitada`() {
        CefrRange(CefrLevel.C1, CefrLevel.B1, 0.5f)
    }

    @Test fun `comparacao entre leitor e livro`() {
        val book = CefrRange(CefrLevel.B2, CefrLevel.C1, 0.7f)
        assertThat(ReadingFit.of(CefrLevel.C1, book)).isEqualTo(ReadingFit.Comfortable)
        assertThat(ReadingFit.of(CefrLevel.C2, book)).isEqualTo(ReadingFit.Comfortable)
        assertThat(ReadingFit.of(CefrLevel.B2, book)).isEqualTo(ReadingFit.ModerateChallenge)
        assertThat(ReadingFit.of(CefrLevel.B1, book)).isEqualTo(ReadingFit.Advanced)
    }

    @Test fun `parse de nivel`() {
        assertThat(CefrLevel.parse("b2")).isEqualTo(CefrLevel.B2)
        assertThat(CefrLevel.parse("D1")).isNull()
    }
}
