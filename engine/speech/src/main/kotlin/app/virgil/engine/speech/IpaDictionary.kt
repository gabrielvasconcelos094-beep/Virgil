package app.virgil.engine.speech

import app.virgil.core.model.Language
import java.io.InputStream
import java.util.zip.GZIPInputStream

/** Transcrição fonética encontrada para uma palavra. [ipa] já vem entre barras (/…/). */
data class Pronunciation(val ipa: String, val word: String, val note: String? = null)

/**
 * Dicionário de pronúncia offline de um idioma, gerado por tools/ipa-lexicon/build.py a partir do
 * Wiktionary (CC BY-SA 4.0). Só contém transcrições escritas por editores: o Virgil não gera IPA a
 * partir da ortografia; palavra ausente → nenhuma transcrição (o áudio e a tradução continuam).
 *
 * Formato (UTF-8, gzip, linhas ordenadas por chave na ordem de [String.compareTo]):
 *   chave \t ipa                         (uma pronúncia)
 *   chave \t ipa \t etiqueta=ipa;…       (homógrafo: pronúncia padrão + alternativas por classe)
 * Etiquetas: n (substantivo), v (verbo), adj, past (passado/particípio).
 *
 * A estrutura em memória é o próprio texto + um vetor de inícios de linha (≈ 2 bytes por caractere),
 * com busca binária: carregar o inglês completo custa poucos MB e dezenas de milissegundos.
 */
class IpaDictionary private constructor(
    val language: Language,
    private val text: String,
    private val lines: IntArray,
) {
    val size: Int get() = lines.size

    /**
     * Pronúncia de [word] no contexto [before] (palavras anteriores na frase, mais próxima por último),
     * usado só para escolher entre homógrafos.
     */
    fun lookup(word: String, before: List<String> = emptyList()): Pronunciation? {
        val clean = word.trim().trim('\'', '’', '-', '.').replace('’', '\'')
        if (clean.isEmpty()) return null
        for (key in candidates(clean)) {
            val line = find(key) ?: continue
            val parts = line.split('\t')
            val default = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: continue
            if (parts.size < 3) return Pronunciation(default, key)
            val alternatives = parts[2].split(';').mapNotNull { a -> a.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            val tag = Homographs.preferred(language, before.map { it.lowercase(locale) }).firstOrNull { it in alternatives }
            val chosen = tag?.let { alternatives[it] }
            return if (chosen != null && chosen != default) Pronunciation(chosen, key, note = Homographs.label(tag)) else Pronunciation(default, key)
        }
        return null
    }

    private val locale = java.util.Locale.forLanguageTag(language.tag)

    /** Forma exata, minúscula e capitalizada (substantivos alemães, início de frase). */
    private fun candidates(w: String): List<String> {
        val lower = w.lowercase(locale)
        val cap = lower.replaceFirstChar { it.titlecase(locale) }
        return listOf(w, lower, cap).distinct()
    }

    private fun keyAt(i: Int): String {
        val s = lines[i]
        val tab = text.indexOf('\t', s)
        return text.substring(s, tab)
    }

    private fun find(key: String): String? {
        var lo = 0
        var hi = lines.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = keyAt(mid).compareTo(key)
            when {
                c < 0 -> lo = mid + 1
                c > 0 -> hi = mid - 1
                else -> {
                    val s = lines[mid]
                    val e = text.indexOf('\n', s).let { if (it < 0) text.length else it }
                    return text.substring(s, e)
                }
            }
        }
        return null
    }

    companion object {
        /** Arquivo-fonte no repositório (gzip). O AGP descompacta .gz ao juntar os assets: no APK fica [assetPath]. */
        fun sourcePath(language: Language) = "ipa/${language.iso639}.tsv.gz"
        fun assetPath(language: Language) = "ipa/${language.iso639}.tsv"

        /** Aceita o texto puro ou comprimido em gzip (detectado pelo cabeçalho 1F 8B). */
        fun load(language: Language, input: InputStream): IpaDictionary {
            val buffered = java.io.BufferedInputStream(input)
            buffered.mark(2)
            val gz = buffered.read() == 0x1f && buffered.read() == 0x8b
            buffered.reset()
            val stream = if (gz) GZIPInputStream(buffered) else buffered
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val starts = ArrayList<Int>(1 shl 16)
            var i = 0
            while (i < text.length) {
                val e = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                if (e > i && text[i] != '#' && text.indexOf('\t', i).let { it in i until e }) starts += i
                i = e + 1
            }
            return IpaDictionary(language, text, starts.toIntArray())
        }
    }
}

/**
 * Escolha entre homógrafos pela palavra anterior. Regras pequenas e explicáveis, só para o inglês
 * (onde homógrafos com pronúncias diferentes são comuns: "lead", "tear", "wind", "read", "live"…).
 * Quando o contexto não decide, fica a pronúncia mais comum, sem rótulo.
 */
internal object Homographs {
    private val determiners = setOf(
        "the", "a", "an", "this", "that", "these", "those", "my", "your", "his", "her", "its", "our", "their",
        "no", "every", "each", "some", "any", "one's", "whose",
    )
    private val toVerb = setOf(
        "to", "will", "would", "shall", "should", "can", "could", "may", "might", "must", "i", "you", "we", "they",
        "don't", "didn't", "doesn't", "won't", "let's", "please", "not",
    )
    private val have = setOf("have", "has", "had", "having", "i've", "we've", "you've", "they've", "i'd", "we'd", "they'd")
    private val be = setOf("been", "was", "were", "be", "being", "is", "are", "am", "seems", "seemed", "very", "so", "too", "quite", "rather", "more", "most")

    /** Classes preferidas, em ordem, para a palavra que vem depois de [before]. Vazio = sem pista. */
    fun preferred(language: Language, before: List<String>): List<String> {
        if (language != Language.EN_GB) return emptyList()
        val prev = before.lastOrNull() ?: return emptyList()
        return when (prev) {
            in have -> listOf("past", "v")
            in be -> listOf("adj", "past")      // "is present" (adjetivo) / "was read" (particípio)
            in determiners -> listOf("n")
            in toVerb -> listOf("v")
            else -> emptyList()
        }
    }

    fun label(tag: String): String? = when (tag) {
        "n" -> "substantivo"
        "v" -> "verbo"
        "past" -> "passado/particípio"
        "adj" -> "adjetivo"
        else -> null
    }
}
