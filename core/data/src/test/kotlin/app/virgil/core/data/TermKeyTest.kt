package app.virgil.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TermKeyTest {
    @Test fun `normaliza caixa e pontuacao nas bordas`() {
        assertThat(termKey("Scarcely,")).isEqualTo("scarcely")
        assertThat(termKey("«Bonjour»")).isEqualTo("bonjour")
        assertThat(termKey("  gave up. ")).isEqualTo("gave up")
    }

    @Test fun `preserva apostrofos internos e unifica o tipografico`() {
        assertThat(termKey("l’homme")).isEqualTo("l'homme")
        assertThat(termKey("don't")).isEqualTo("don't")
    }

    @Test fun `preserva diacriticos`() {
        assertThat(termKey("Mädchen")).isEqualTo("mädchen")
        assertThat(termKey("passé")).isEqualTo("passé")
    }
}
