package app.virgil.core.text

import app.virgil.core.model.Language
import java.text.BreakIterator
import java.util.Locale

/**
 * Segmentação de palavras e frases por idioma.
 *
 * Usa java.text.BreakIterator (no Android é implementado pelo ICU) com correções para
 * literatura europeia:
 *  - elisões do francês e do italiano ("l'homme" → "homme" ou "l'", conforme o toque);
 *  - abreviações que não encerram frase ("Mrs.", "M.", "Dr.", "Sra.", "Hr.", "ecc.");
 *  - iniciais ("J. K.") não quebram frase.
 */
class TextSegmenter(private val language: Language) {
    val locale: Locale = Locale.forLanguageTag(language.tag)

    /** Palavra (intervalo semiaberto start..<end) que contém [offset], ou null se for espaço/pontuação. */
    fun wordAt(text: String, offset: Int): IntRange? {
        if (text.isEmpty() || offset !in text.indices) return null
        val it = BreakIterator.getWordInstance(locale).apply { setText(text) }
        val end = it.following(offset).takeIf { e -> e != BreakIterator.DONE } ?: text.length
        val start = it.previous().takeIf { s -> s != BreakIterator.DONE } ?: 0
        if (start >= end || text.substring(start, end).none { c -> c.isLetterOrDigit() }) return null
        return splitElision(text, joinApostrophes(text, start until end), offset)
    }

    /** Todas as palavras do texto, na ordem. */
    fun words(text: String): List<IntRange> {
        val it = BreakIterator.getWordInstance(locale).apply { setText(text) }
        val out = ArrayList<IntRange>()
        var start = it.first()
        var end = it.next()
        var consumedUntil = 0
        while (end != BreakIterator.DONE) {
            if (start >= consumedUntil && text.substring(start, end).any { c -> c.isLetter() }) {
                val r = joinApostrophes(text, start until end)
                consumedUntil = r.last + 1
                out += elisionParts(text, r)
            }
            start = end
            end = it.next()
        }
        return out
    }

    /**
     * Une segmentos separados por apóstrofo entre letras ("l’homme", "don’t", "aujourd'hui").
     * Necessário porque nem toda implementação de BreakIterator trata o apóstrofo tipográfico (U+2019)
     * como parte da palavra; o resultado fica igual na JVM e no Android (ICU).
     */
    private fun joinApostrophes(text: String, r: IntRange): IntRange {
        fun isApo(c: Char) = c == '\'' || c == '’'
        var s = r.first
        var e = r.last
        while (e + 2 < text.length && isApo(text[e + 1]) && text[e + 2].isLetter()) {
            e += 2
            while (e + 1 < text.length && text[e + 1].isLetter()) e++
        }
        while (s - 2 >= 0 && isApo(text[s - 1]) && text[s - 2].isLetter()) {
            s -= 2
            while (s - 1 >= 0 && text[s - 1].isLetter()) s--
        }
        return s..e
    }

    /** Frases do texto como intervalos, sem espaços nas bordas. */
    fun sentences(text: String): List<IntRange> {
        val it = BreakIterator.getSentenceInstance(locale).apply { setText(text) }
        val raw = ArrayList<IntRange>()
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            raw += start until end
            start = end
            end = it.next()
        }
        // Junta quebras falsas: abreviações e iniciais; aspas de fechamento coladas ("du?“");
        // continuação em minúscula de diálogo ("» dit-il", "—preguntó él", "” cried she", "... and");
        // interjeição curta dentro de aspas abertas ("“Oh! Single…”").
        val merged = ArrayList<IntRange>()
        for (r in raw) {
            val prev = merged.lastOrNull()
            if (prev != null && shouldJoin(text, prev, r)) {
                merged[merged.size - 1] = prev.first..r.last
            } else {
                merged += r
            }
        }
        return merged.mapNotNull { trim(text, it) }
    }

    private fun shouldJoin(text: String, prev: IntRange, next: IntRange): Boolean {
        val p = text.substring(prev.first, prev.last + 1)
        if (endsWithAbbreviation(p)) return true
        val n = text.substring(next.first, next.last + 1)
        // A quebra caiu antes de uma aspa colada ao fim da frase anterior: a aspa fecha a anterior.
        if (n.isNotEmpty() && n[0] in QUOTES && p.isNotEmpty() && !p.last().isWhitespace()) return true
        // Próxima "frase" começa (depois de aspas, travessões e espaços) com minúscula: é continuação.
        val firstLetter = n.firstOrNull { it.isLetterOrDigit() }
        if (firstLetter != null && firstLetter.isLowerCase()) return true
        // Interjeição curta logo após abrir aspas ("“Oh!", "«Ah !") ainda sem fechar.
        val words = p.trim().split(Regex("\\s+")).size
        if (words <= 2 && openQuotes(p) > 0) return true
        return false
    }

    /** Aspas abertas e não fechadas (aproximação: no alemão „…“ conta como aberta, o que só afeta frases de ≤ 2 palavras). */
    private fun openQuotes(s: String): Int =
        s.count { it == '“' || it == '«' || it == '„' } - s.count { it == '”' || it == '»' }

    /** Frase que contém o intervalo [start, end). Se o intervalo atravessar frases, devolve a união. */
    fun sentenceAround(text: String, start: Int, end: Int = start + 1): IntRange {
        val all = sentences(text)
        val first = all.firstOrNull { start <= it.last } ?: return 0 until text.length
        val last = all.lastOrNull { (end - 1) >= it.first } ?: first
        return first.first..maxOf(first.last, last.last)
    }

    private fun trim(text: String, r: IntRange): IntRange? {
        var s = r.first
        var e = r.last
        while (s <= e && text[s].isWhitespace()) s++
        while (e >= s && text[e].isWhitespace()) e--
        return if (s > e) null else s..e
    }

    private fun endsWithAbbreviation(sentence: String): Boolean {
        val t = sentence.trimEnd()
        if (!t.endsWith('.')) return false
        val lastToken = t.dropLast(1).takeLastWhile { !it.isWhitespace() && it != '(' && it != '«' && it != '"' && it != '“' }
        if (lastToken.length == 1 && lastToken[0].isUpperCase()) return true // inicial: "J."
        return lastToken.lowercase(locale) in abbreviations(language)
    }

    private fun splitElision(text: String, r: IntRange, offset: Int): IntRange {
        val parts = elisionParts(text, r)
        return parts.firstOrNull { offset in it } ?: parts.last()
    }

    /** Divide "l'homme" em "l'" + "homme" (FR/IT); outros idiomas mantêm a palavra inteira. */
    private fun elisionParts(text: String, r: IntRange): List<IntRange> {
        if (language != Language.FR && language != Language.IT) return listOf(r)
        val w = text.substring(r.first, r.last + 1)
        val apo = w.indexOfFirst { it == '\'' || it == '’' }
        if (apo <= 0 || apo == w.length - 1) return listOf(r)
        val prefix = w.substring(0, apo).lowercase(locale)
        if (prefix !in elidedPrefixes(language)) return listOf(r)
        val cut = r.first + apo + 1
        return listOf(r.first until cut, cut..r.last)
    }

    companion object {
        private val ELIDED_FR = setOf("l", "d", "j", "m", "n", "s", "t", "c", "qu", "jusqu", "lorsqu", "puisqu", "quoiqu", "presqu")
        private val ELIDED_IT = setOf(
            "l", "un", "d", "dell", "all", "nell", "dall", "sull", "coll", "quest", "quell", "c", "v", "m", "t", "s", "n", "bell", "sant",
        )

        private fun elidedPrefixes(l: Language) = if (l == Language.FR) ELIDED_FR else ELIDED_IT

        private val QUOTES = setOf('”', '“', '»', '«', '’', '"', '\'')

        private val COMMON = setOf("etc", "p", "pp", "cf", "vol", "nr", "no", "n", "ca")

        fun abbreviations(l: Language): Set<String> = COMMON + when (l) {
            Language.EN_GB -> setOf("mr", "mrs", "ms", "dr", "st", "mt", "sr", "jr", "rev", "col", "capt", "gen", "lt", "prof", "esq", "messrs", "viz", "i.e", "e.g", "vs")
            Language.FR -> setOf("m", "mm", "mme", "mmes", "mlle", "mlles", "dr", "me", "mgr", "st", "ste", "cf", "env", "p.ex")
            Language.DE -> setOf("hr", "hrn", "fr", "frl", "dr", "prof", "st", "z.b", "u.a", "usw", "bzw", "d.h", "ggf", "vgl", "s")
            Language.IT -> setOf("sig", "sigg", "sig.na", "sig.ra", "dott", "prof", "ecc", "s", "ss", "on", "avv", "ing", "mons")
            Language.ES -> setOf("sr", "sra", "srta", "sres", "d", "dña", "dr", "dra", "ud", "uds", "vd", "vds", "sto", "sta", "etc", "pág")
            Language.PT_BR -> setOf("sr", "sra", "srta", "dr", "dra", "d", "prof", "profa", "exa", "v.ex", "pág")
        }
    }
}
