package app.virgil.engine.translation

import app.virgil.core.model.Language

/**
 * Prompts validados no benchmark (tools/mt-benchmark). Regras de montagem:
 *  - a parte fixa vem primeiro e a frase antes da palavra, para que consultas seguidas
 *    reaproveitem o prefixo já avaliado no KV cache;
 *  - decodificação gulosa: a mesma consulta sempre produz o mesmo resultado (cacheável).
 */
internal object Prompts {
    private fun lang(l: Language) = when (l) {
        Language.EN_GB -> "English" to "en-GB"
        Language.FR -> "French" to "fr"
        Language.DE -> "German" to "de"
        Language.IT -> "Italian" to "it"
        Language.ES -> "Spanish" to "es-ES"
        Language.PT_BR -> "Portuguese" to "pt-BR"
    }

    fun term(family: PromptFamily, source: Language, sentence: String, term: String): String {
        val (name, code) = lang(source)
        return when (family) {
            PromptFamily.TranslateGemma -> gemma(
                "You are a professional $name ($code) to Portuguese (pt-BR) translator.\nSentence: $sentence\n" +
                    "In the sentence above, the $name word or expression \"$term\" is used in a specific sense. " +
                    "Give the Brazilian Portuguese translation of \"$term\" as used in this sentence. " +
                    "Output only the translation (one to three words), nothing else.",
            )
            PromptFamily.HyMT -> hymt(
                "Sentence: $sentence\nIn the sentence above, the $name word or expression \"$term\" is used in a specific sense. " +
                    "Give the Brazilian Portuguese translation of \"$term\" as used in this sentence. " +
                    "Output only the translation (one to three words), nothing else.",
            )
        }
    }

    /**
     * Variante com a instrução antes da frase: a parte fixa (≈ 60 tokens) fica sempre no KV cache, e
     * a frase também, quando se consultam várias palavras dela em seguida.
     */
    fun termPrefixFirst(family: PromptFamily, source: Language, sentence: String, term: String): String {
        val (name, code) = lang(source)
        val instruction = "Give the Brazilian Portuguese translation of the $name word or expression shown after the sentence, " +
            "as it is used in that sentence. Output only the translation (one to three words), nothing else."
        return when (family) {
            PromptFamily.TranslateGemma -> gemma(
                "You are a professional $name ($code) to Portuguese (pt-BR) translator. $instruction\nSentence: $sentence\nWord or expression: \"$term\"",
            )
            PromptFamily.HyMT -> hymt("$instruction\nSentence: $sentence\nWord or expression: \"$term\"")
        }
    }

    fun text(family: PromptFamily, source: Language, text: String): String {
        val (name, code) = lang(source)
        return when (family) {
            PromptFamily.TranslateGemma -> gemma(
                "You are a professional $name ($code) to Portuguese (pt-BR) translator. Your goal is to accurately convey the meaning " +
                    "and nuances of the original $name text while adhering to Portuguese grammar, vocabulary, and cultural sensitivities.\n" +
                    "Produce only the Portuguese translation, without any additional explanations or commentary. " +
                    "Please translate the following $name text into Portuguese:\n\n\n" + text,
            )
            PromptFamily.HyMT -> hymt(
                "Translate the following text into Brazilian Portuguese. Note that you should only output the translated result " +
                    "without any additional explanation:\n\n" + text,
            )
        }
    }

    /**
     * Contexto enviado com uma palavra: a frase inteira, ou — em frases muito longas (comuns em
     * Proust, Saramago…) — uma janela de [radius] palavras de cada lado do termo. Cada token a menos
     * no prompt economiza ~20 ms no diagnóstico do host; acima de ~60 palavras a frase não ajuda a
     * desambiguar.
     */
    fun contextWindow(sentence: String, term: String, radius: Int = 30): String {
        val words = sentence.split(' ').filter { it.isNotEmpty() }
        if (words.size <= radius * 2 + 1) return sentence
        val first = term.split(' ').first()
        val at = words.indexOfFirst { it.contains(first) }.takeIf { it >= 0 } ?: return words.take(radius * 2).joinToString(" ") + " …"
        val from = (at - radius).coerceAtLeast(0)
        val to = (at + radius + term.split(' ').size).coerceAtMost(words.size)
        return (if (from > 0) "… " else "") + words.subList(from, to).joinToString(" ") + (if (to < words.size) " …" else "")
    }

    fun repeatPenalty(family: PromptFamily) = if (family == PromptFamily.HyMT) 1.05f else 1f

    private fun gemma(user: String) = "<start_of_turn>user\n$user<end_of_turn>\n<start_of_turn>model\n"
    private fun hymt(user: String) = "<｜hy_begin▁of▁sentence｜><｜hy_User｜>$user<｜hy_Assistant｜>"
}

/** Limpeza das respostas do modelo. Não inventa nada: só remove ruído de formatação. */
internal object PostProcess {
    private val labels = Regex("^(translation|tradução|traducao|resposta|answer)\\s*:\\s*", RegexOption.IGNORE_CASE)

    fun term(raw: String, sourceTerm: String, language: Language): String {
        var s = raw.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        s = s.replace(labels, "")
        // Aspas, ênfase de markdown e pontuação final podem vir em qualquer ordem ("Mal". / *mal.*).
        val edge = charArrayOf('"', '“', '”', '«', '»', '\'', '*', '`', ' ')
        val tail = edge + charArrayOf('.', ';', ':', ',', '!')
        var prev: String
        do { prev = s; s = s.trimStart(*edge).trimEnd(*tail) } while (s != prev)
        // Respostas que viraram frase inteira: mantém só o começo razoável.
        val words = s.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size > 6) s = words.take(6).joinToString(" ") + "…"
        // Caixa: segue a do termo de origem (substantivos alemães são sempre maiúsculos no original).
        val sourceLower = sourceTerm.firstOrNull()?.isLowerCase() == true
        if (s.isNotEmpty() && s[0].isUpperCase() && (sourceLower || language == Language.DE) && !s.equals(sourceTerm, ignoreCase = false)) {
            s = s[0].lowercase() + s.substring(1)
        }
        return s
    }

    fun text(raw: String): String = raw.trim().replace(labels, "").trim()
}
