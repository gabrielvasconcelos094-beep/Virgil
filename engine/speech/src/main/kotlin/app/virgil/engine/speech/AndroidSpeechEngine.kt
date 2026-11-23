package app.virgil.engine.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import app.virgil.core.model.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Mecanismo de voz do próprio Android (android.speech.tts.TextToSpeech).
 *
 * - Usa o mecanismo padrão do aparelho (normalmente "Serviços de fala do Google").
 * - Seleciona, por idioma, uma voz da variante certa (en-GB, es-ES, pt-BR…), preferindo vozes
 *   que não exigem rede e de maior qualidade; o Virgil nunca envia texto à internet por conta
 *   própria, mas uma voz "de rede" do sistema enviaria — por isso elas só são usadas se o usuário
 *   escolher explicitamente.
 * - Não fornece transcrição fonética (a API não expõe fonemas); o IPA vem de [app.virgil.engine.speech.IpaDictionary].
 */
class AndroidSpeechEngine(context: Context) : SpeechEngine {
    private val _ready = MutableStateFlow(false)
    override val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private val _speaking = MutableStateFlow<String?>(null)
    override val speaking: StateFlow<String?> = _speaking.asStateFlow()
    private var initFailed = false
    private var pending: (() -> Unit)? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            _ready.value = true
            pending?.invoke(); pending = null
        } else {
            initFailed = true
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _speaking.value = utteranceId }
            override fun onDone(utteranceId: String?) { if (_speaking.value == utteranceId) _speaking.value = null }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { if (_speaking.value == utteranceId) _speaking.value = null }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { if (_speaking.value == utteranceId) _speaking.value = null }
        })
    }

    override fun availability(language: Language): VoiceAvailability {
        if (initFailed) return VoiceAvailability.Unavailable
        if (!_ready.value) return VoiceAvailability.Unavailable
        return when (tts.isLanguageAvailable(locale(language))) {
            TextToSpeech.LANG_COUNTRY_AVAILABLE, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE, TextToSpeech.LANG_AVAILABLE ->
                if (bestVoice(language, null) != null || tts.voices.isNullOrEmpty()) VoiceAvailability.Ready else VoiceAvailability.MissingData
            TextToSpeech.LANG_MISSING_DATA -> VoiceAvailability.MissingData
            else -> VoiceAvailability.NotSupported
        }
    }

    override fun voices(language: Language): List<VoiceOption> {
        if (!_ready.value) return emptyList()
        return candidates(language).map { v ->
            VoiceOption(v.name, label(v), language, offline = !v.isNetworkConnectionRequired && !v.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED), quality = v.quality)
        }
    }

    override fun speak(text: String, language: Language, rate: Float, voiceId: String?, id: String) {
        val action = {
            val voice = bestVoice(language, voiceId)
            if (voice != null) tts.voice = voice else tts.language = locale(language)
            tts.setSpeechRate(rate.coerceIn(0.3f, 2f))
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
            Unit
        }
        if (_ready.value) action() else pending = action // primeira fala antes da inicialização terminar
    }

    override fun stop() { tts.stop(); _speaking.value = null }
    override fun shutdown() { tts.shutdown() }

    private fun candidates(language: Language): List<Voice> {
        val target = locale(language)
        return tts.voices.orEmpty().filter { v ->
            v.locale.language == target.language && (target.country.isEmpty() || v.locale.country == target.country)
        }
    }

    /** Voz escolhida pelo usuário, ou a melhor voz offline da variante certa. */
    private fun bestVoice(language: Language, voiceId: String?): Voice? {
        val list = candidates(language)
        voiceId?.let { id -> list.firstOrNull { it.name == id }?.let { return it } }
        return list.filter { !it.isNetworkConnectionRequired && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .maxByOrNull { it.quality - it.latency / 100 }
    }

    private fun label(v: Voice): String {
        val q = when {
            v.quality >= Voice.QUALITY_VERY_HIGH -> "qualidade muito alta"
            v.quality >= Voice.QUALITY_HIGH -> "qualidade alta"
            else -> "qualidade normal"
        }
        val net = if (v.isNetworkConnectionRequired) " · requer internet" else ""
        return "${v.name.substringAfterLast('-').ifBlank { v.name }} · $q$net"
    }

    companion object {
        /** Variante de cada idioma do Virgil (britânico para o inglês, Espanha para o espanhol). */
        fun locale(language: Language): Locale = when (language) {
            Language.EN_GB -> Locale.UK
            Language.FR -> Locale.FRANCE
            Language.DE -> Locale.GERMANY
            Language.IT -> Locale.ITALY
            Language.ES -> Locale.forLanguageTag("es-ES")
            Language.PT_BR -> Locale.forLanguageTag("pt-BR")
        }
    }
}
