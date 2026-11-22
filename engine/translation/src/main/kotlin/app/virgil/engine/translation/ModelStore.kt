package app.virgil.engine.translation

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import app.virgil.core.database.VirgilDatabase
import app.virgil.core.database.entity.InstalledModelEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Estado de instalação de um modelo do catálogo, como a interface o apresenta. */
sealed interface ModelState {
    data object NotInstalled : ModelState
    data class Downloading(val downloaded: Long, val total: Long) : ModelState
    data object Verifying : ModelState
    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

/**
 * Arquivos de modelo no armazenamento privado (files/models), com estado persistido em Room.
 * Garante que só um modelo fique instalado e que todo arquivo usado tenha o SHA-256 esperado.
 */
class ModelStore(private val context: Context, private val db: VirgilDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    val dir = File(context.filesDir, "models").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Modelo pronto para uso (verificado), ou null. */
    val installed: StateFlow<ModelSpec?> = db.models().observeAll()
        .map { rows -> rows.firstOrNull { it.status == STATUS_READY }?.let { ModelCatalog.byId(it.modelId) }?.takeIf { fileFor(it).isFile } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val rows = db.models().observeAll()

    fun fileFor(spec: ModelSpec) = File(dir, spec.fileName)
    fun partFileFor(spec: ModelSpec) = File(dir, spec.fileName + ".part")

    fun totalRamBytes(): Long {
        val am = context.getSystemService(ActivityManager::class.java)
        return ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem
    }

    fun freeBytes(): Long = StatFs(dir.absolutePath).availableBytes

    suspend fun setState(spec: ModelSpec, status: String, downloaded: Long) = withContext(Dispatchers.IO) {
        val prev = db.models().get(spec.id)
        db.models().upsert(
            InstalledModelEntity(
                modelId = spec.id, fileName = spec.fileName, sizeBytes = spec.sizeBytes, sha256 = spec.sha256, status = status,
                downloadedBytes = downloaded, installedAt = if (status == STATUS_READY) clock() else prev?.installedAt, lastUsedAt = prev?.lastUsedAt,
            ),
        )
    }

    /** Marca como pronto e remove qualquer outro modelo (um único modelo instalado). */
    suspend fun markReady(spec: ModelSpec) = withContext(Dispatchers.IO) {
        ModelCatalog.all.filter { it.id != spec.id }.forEach { remove(it) }
        setState(spec, STATUS_READY, spec.sizeBytes)
    }

    suspend fun remove(spec: ModelSpec) = withContext(Dispatchers.IO) {
        fileFor(spec).delete()
        partFileFor(spec).delete()
        db.models().delete(spec.id)
    }

    /**
     * Instala um .gguf escolhido pelo usuário (instalação sem internet, a partir de um arquivo
     * copiado para o aparelho). Só aceita arquivos idênticos aos do catálogo (SHA-256).
     */
    suspend fun importFromUri(uri: Uri, onProgress: (Long) -> Unit = {}): Result<ModelSpec> = withContext(Dispatchers.IO) {
        val tmp = File(dir, "import.tmp")
        try {
            val sha = context.contentResolver.openInputStream(uri)?.use { copyHashing(it, tmp, onProgress) }
                ?: return@withContext Result.failure(IllegalArgumentException("Não foi possível abrir o arquivo."))
            val spec = ModelCatalog.bySha(sha) ?: run {
                tmp.delete()
                return@withContext Result.failure(IllegalArgumentException("Este arquivo não corresponde a nenhum modelo suportado (verificação SHA-256)."))
            }
            val target = fileFor(spec)
            target.delete()
            if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
            markReady(spec)
            Result.success(spec)
        } catch (e: Exception) {
            tmp.delete()
            Result.failure(e)
        }
    }

    companion object {
        const val STATUS_DOWNLOADING = "Downloading"
        const val STATUS_VERIFYING = "Verifying"
        const val STATUS_READY = "Ready"
        const val STATUS_FAILED = "Failed"

        fun copyHashing(input: InputStream, out: File, onProgress: (Long) -> Unit): String {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(1 shl 20)
            var total = 0L
            out.outputStream().use { o ->
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n); o.write(buf, 0, n); total += n
                    onProgress(total)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        fun sha256(file: File, onProgress: (Long) -> Unit = {}): String {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(1 shl 20)
            var total = 0L
            file.inputStream().use { input ->
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n); total += n; onProgress(total)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
