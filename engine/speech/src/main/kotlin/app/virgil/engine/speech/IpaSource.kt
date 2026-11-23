package app.virgil.engine.speech

import android.content.Context
import android.util.Log
import app.virgil.core.model.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream

/** Fonte de transcrições fonéticas. */
fun interface IpaSource {
    suspend fun lookup(language: Language, word: String, before: List<String>): Pronunciation?
}

/**
 * Carrega o léxico de cada idioma uma vez, sob demanda, fora da thread principal, e o mantém em
 * memória (2–3 MB por idioma). [preload] permite carregar ao abrir o leitor, para a primeira consulta
 * não esperar a descompressão (~50–150 ms).
 */
open class LexiconIpaSource(private val open: (String) -> InputStream?) : IpaSource {
    private val mutex = Mutex()
    private val loaded = HashMap<Language, IpaDictionary?>()

    suspend fun dictionary(language: Language): IpaDictionary? = mutex.withLock {
        if (language in loaded) return@withLock loaded[language]
        val d = withContext(Dispatchers.IO) {
            runCatching { (open(IpaDictionary.assetPath(language)) ?: open(IpaDictionary.sourcePath(language)))?.use { IpaDictionary.load(language, it) } }
                .onFailure { Log.w("Virgil", "léxico IPA de ${language.tag} indisponível", it) }
                .getOrNull()
        }
        loaded[language] = d
        d
    }

    suspend fun preload(language: Language) { dictionary(language) }

    override suspend fun lookup(language: Language, word: String, before: List<String>): Pronunciation? =
        dictionary(language)?.let { d -> withContext(Dispatchers.Default) { d.lookup(word, before) } }
}

class AssetIpaSource(context: Context) : LexiconIpaSource({ path ->
    runCatching { context.applicationContext.assets.open(path) }.getOrNull()
})
