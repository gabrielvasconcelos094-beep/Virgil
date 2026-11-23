package app.virgil.engine.speech

import app.virgil.core.model.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/** Testa os léxicos reais embarcados (src/main/assets/ipa). */
class IpaDictionaryTest {
    private fun dict(l: Language): IpaDictionary {
        val f = File("src/main/assets/${IpaDictionary.sourcePath(l)}")
        val t0 = System.nanoTime()
        val d = f.inputStream().use { IpaDictionary.load(l, it) }
        println("léxico ${l.tag}: ${d.size} palavras, ${f.length() / 1024} KB, carregado em ${(System.nanoTime() - t0) / 1_000_000} ms")
        return d
    }

    private val en by lazy { dict(Language.EN_GB) }

    @Test fun inglesBritanico() {
        assertEquals("/ˈwɔː.tə/", en.lookup("water")!!.ipa)        // não rótico (RP), não /ˈwɔtɚ/
        assertEquals("/ˈʃɛd͡ʒuːl/", en.lookup("schedule")!!.ipa)  // britânico
        assertEquals("/bɑːθ/", en.lookup("bath")!!.ipa)
        assertEquals("/ɹɪˈplaɪd/", en.lookup("replied")!!.ipa)
        assertEquals("/ðə/", en.lookup("The")!!.ipa)               // maiúscula de início de frase
    }

    @Test fun homografosPeloContexto() {
        assertEquals("/ɹɛd/", en.lookup("read", listOf("she", "had")).ipa())
        assertEquals("/ɹiːd/", en.lookup("read", listOf("I", "will")).ipa())
        assertEquals("/ɹiːd/", en.lookup("read").ipa())                    // sem pista: a mais comum
        assertEquals("/ˈpɹɛz.ənt/", en.lookup("present", listOf("was")).ipa())
        assertEquals("/pɹɪˈzɛnt/", en.lookup("present", listOf("to")).ipa())
        assertEquals("/juːz/", en.lookup("use", listOf("to")).ipa())
        assertEquals("/juːs/", en.lookup("use", listOf("the")).ipa())
        assertEquals("/ɹɪˈkɔːd/", en.lookup("record", listOf("to")).ipa())
        assertEquals("verbo", en.lookup("record", listOf("to"))!!.note)
    }

    @Test fun variantesDosOutrosIdiomas() {
        assertEquals("/ˈθjelo/", dict(Language.ES).lookup("cielo").ipa())      // distinção castelhana
        assertEquals("/aˈθeɾ/", dict(Language.ES).lookup("hacer").ipa())
        val pt = dict(Language.PT_BR)
        assertEquals("/siˈda.d͡ʒi/", pt.lookup("cidade").ipa())               // Brasil, não /siˈda.dɨ/
        val de = dict(Language.DE)
        assertEquals("[haʊ̯s]", de.lookup("Haus").ipa())                       // só há a fonética: colchetes mantidos
        assertEquals("/vɛk/", de.lookup("weg").ipa())
        assertEquals("[veːk]", de.lookup("Weg").ipa())                         // maiúscula distingue no alemão
        assertTrue(dict(Language.FR).lookup("maison").ipa()!!.startsWith("/mɛ.zɔ̃/"))
        assertTrue(dict(Language.IT).lookup("casa").ipa()!!.startsWith("/ˈka"))
    }

    @Test fun ausenteNaoInventa() {
        assertNull(en.lookup("Netherfieldishness"))
        assertNull(en.lookup(""))
        assertNull(en.lookup("..."))
    }

    /** Todas as linhas bem formadas e todas as chaves encontráveis pela busca binária (ordem correta). */
    @Test fun formatoEOrdenacao() {
        for (l in Language.entries) {
            val d = dict(l)
            val keys = GZIPInputStream(File("src/main/assets/${IpaDictionary.sourcePath(l)}").inputStream()).bufferedReader().readLines()
                .filter { !it.startsWith("#") && it.isNotBlank() }
                .map { line ->
                    val parts = line.split('\t')
                    assertTrue(line, parts.size in 2..3)
                    assertTrue(line, parts[1].first() in "/[" && parts[1].last() in "/]")
                    parts[0]
                }
            assertEquals(keys.size, d.size)
            keys.filterIndexed { i, k -> i % 97 == 0 && k.trim('\'', '’', '-', '.') == k }.forEach { k -> assertEquals(k, d.lookup(k)?.word) }
        }
    }

    private fun Pronunciation?.ipa() = this?.ipa
}
