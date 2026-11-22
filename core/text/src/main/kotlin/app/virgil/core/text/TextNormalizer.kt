package app.virgil.core.text

import java.text.Normalizer

/** Normalização aplicada ao texto dos livros na importação. O leitor e a análise veem exatamente este texto. */
object TextNormalizer {
    private val invisible = Regex("[\\u00AD\\u200B\\u200C\\u200D\\u2060\\uFEFF]")
    private val spaces = Regex("[\\t\\u000B\\u000C\\u00A0\\u2000-\\u200A\\u202F\\u205F\\u3000 ]+")

    /** NFC, remove hifens suaves e caracteres de largura zero, colapsa espaços (sem tocar em quebras de linha). */
    fun clean(raw: String): String {
        val nfc = Normalizer.normalize(raw, Normalizer.Form.NFC)
        return nfc.replace(invisible, "").replace(spaces, " ")
    }

    /** Remove espaços nas bordas de cada linha e linhas vazias redundantes. */
    fun tidyLines(s: String): String = s.lines().joinToString("\n") { it.trim() }.trim()
}
