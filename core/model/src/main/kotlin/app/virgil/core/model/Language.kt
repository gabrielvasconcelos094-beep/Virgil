package app.virgil.core.model

/**
 * Idiomas suportados. O código segue BCP-47 com variante regional, porque o Virgil
 * respeita variantes (inglês britânico, espanhol europeu, português brasileiro).
 *
 * Acrescentar um idioma = acrescentar uma entrada aqui + recursos linguísticos em
 * engine/analysis. O motor de tradução recebe o nome do idioma, não um enum fixo.
 */
enum class Language(
    val tag: String,
    val iso639: String,
    val displayNamePt: String,
    val englishName: String,
    val canBeSource: Boolean = true,
) {
    PT_BR("pt-BR", "pt", "Português (Brasil)", "Brazilian Portuguese"),
    EN_GB("en-GB", "en", "Inglês (britânico)", "British English"),
    FR("fr", "fr", "Francês", "French"),
    DE("de", "de", "Alemão", "German"),
    IT("it", "it", "Italiano", "Italian"),
    ES("es-ES", "es", "Espanhol (Espanha)", "European Spanish");

    companion object {
        /** Idioma de destino das traduções e da interface. */
        val Target = PT_BR

        /** Idiomas estudáveis (todos exceto o idioma nativo do usuário). */
        val Learnable = entries.filter { it != PT_BR }

        /** Resolve um código de idioma de metadados (ex.: "en", "en-US", "fr-CA", "spa"). */
        fun fromTag(raw: String?): Language? {
            if (raw.isNullOrBlank()) return null
            val primary = raw.trim().lowercase().replace('_', '-').substringBefore('-')
            return when (primary) {
                "pt", "por" -> PT_BR
                "en", "eng" -> EN_GB
                "fr", "fra", "fre" -> FR
                "de", "deu", "ger" -> DE
                "it", "ita" -> IT
                "es", "spa" -> ES
                else -> null
            }
        }
    }
}
