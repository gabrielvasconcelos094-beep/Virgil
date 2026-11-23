package app.virgil.engine.speech

import app.virgil.core.model.Language
import kotlinx.coroutines.flow.StateFlow

/** Disponibilidade de voz para um idioma no mecanismo atual. */
sealed interface VoiceAvailability {
    data object Ready : VoiceAvailability
    /** O mecanismo conhece o idioma, mas os dados de voz não estão instalados no aparelho. */
    data object MissingData : VoiceAvailability
    data object NotSupported : VoiceAvailability
    /** O mecanismo de voz ainda está iniciando (ou não existe no aparelho). */
    data object Unavailable : VoiceAvailability
}

data class VoiceOption(
    val id: String,
    val label: String,
    val language: Language,
    val offline: Boolean,
    val quality: Int,
)

enum class SpeakState { Idle, Speaking }

/**
 * Pronúncia no idioma original do texto. Independe do modelo de tradução: funciona mesmo sem ele.
 * Implementações: [AndroidSpeechEngine] (padrão, sem download) e, opcionalmente, um mecanismo neural.
 */
interface SpeechEngine {
    val ready: StateFlow<Boolean>
    val speaking: StateFlow<String?>

    fun availability(language: Language): VoiceAvailability
    fun voices(language: Language): List<VoiceOption>

    /** Fala [text] no idioma [language], interrompendo o que estiver tocando. [id] identifica o pedido. */
    fun speak(text: String, language: Language, rate: Float = 1f, voiceId: String? = null, id: String = text)

    fun stop()
    fun shutdown()
}
