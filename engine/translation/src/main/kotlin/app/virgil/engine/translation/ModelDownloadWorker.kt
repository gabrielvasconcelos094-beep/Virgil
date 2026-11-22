package app.virgil.engine.translation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.virgil.core.database.VirgilDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Baixa o modelo em segundo plano (serviço em primeiro plano com notificação, exigido pelo Android
 * para transferências longas). Retoma de onde parou (HTTP Range) e só instala após conferir o SHA-256.
 * É a única operação do Virgil que usa a internet.
 */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val spec = ModelCatalog.byId(inputData.getString(KEY_MODEL)) ?: return@withContext Result.failure()
        val store = ModelStore(applicationContext, VirgilDatabase.getInstance(applicationContext))
        val part = store.partFileFor(spec)
        val needed = spec.sizeBytes - part.length() + 64L * 1024 * 1024
        if (store.freeBytes() < needed) {
            store.setState(spec, ModelStore.STATUS_FAILED, part.length())
            return@withContext Result.failure(workDataOf(KEY_ERROR to "Espaço insuficiente: são necessários ${needed / (1024 * 1024)} MB livres."))
        }
        runCatching { setForeground(foregroundInfo(spec, part.length())) }
        try {
            store.setState(spec, ModelStore.STATUS_DOWNLOADING, part.length())
            download(spec, part, store)
            if (isStopped) return@withContext Result.retry()
            store.setState(spec, ModelStore.STATUS_VERIFYING, spec.sizeBytes)
            setProgress(workDataOf(KEY_PHASE to "verify"))
            val sha = ModelStore.sha256(part)
            if (!sha.equals(spec.sha256, ignoreCase = true)) {
                part.delete()
                store.setState(spec, ModelStore.STATUS_FAILED, 0)
                return@withContext Result.failure(workDataOf(KEY_ERROR to "O arquivo baixado não passou na verificação de integridade e foi descartado. Tente novamente."))
            }
            val target = store.fileFor(spec)
            target.delete()
            if (!part.renameTo(target)) throw IOException("Não foi possível mover o arquivo do modelo.")
            store.markReady(spec)
            Result.success()
        } catch (e: IOException) {
            store.setState(spec, ModelStore.STATUS_DOWNLOADING, part.length())
            // Falha de rede: o WorkManager tenta de novo, retomando do ponto em que parou.
            if (runAttemptCount < 8) Result.retry()
            else Result.failure(workDataOf(KEY_ERROR to "Não foi possível concluir o download (${e.message ?: "erro de rede"})."))
        }
    }

    private suspend fun download(spec: ModelSpec, part: java.io.File, store: ModelStore) {
        var offset = part.length()
        if (offset >= spec.sizeBytes) return
        val conn = (URL(spec.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Virgil-Android")
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }
        try {
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK && offset > 0) offset = 0 // servidor ignorou o Range: recomeça
            else if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) throw IOException("HTTP $code")
            RandomAccessFile(part, "rw").use { raf ->
                raf.setLength(offset)
                raf.seek(offset)
                conn.inputStream.use { input ->
                    val buf = ByteArray(256 * 1024)
                    var lastReport = 0L
                    while (!isStopped) {
                        val n = input.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        offset += n
                        if (offset - lastReport >= 8L * 1024 * 1024) {
                            lastReport = offset
                            setProgress(workDataOf(KEY_DOWNLOADED to offset, KEY_TOTAL to spec.sizeBytes))
                            runCatching { setForeground(foregroundInfo(spec, offset)) }
                            store.setState(spec, ModelStore.STATUS_DOWNLOADING, offset)
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (!isStopped && offset < spec.sizeBytes) throw IOException("conexão interrompida")
    }

    private fun foregroundInfo(spec: ModelSpec, downloaded: Long): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Download do modelo de tradução", NotificationManager.IMPORTANCE_LOW))
        }
        val pct = (downloaded * 100 / spec.sizeBytes).toInt()
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Baixando ${spec.displayName}")
            .setContentText("$pct% · ${downloaded / (1024 * 1024)} de ${spec.sizeBytes / (1024 * 1024)} MB")
            .setProgress(100, pct, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(NOTIFICATION_ID, n)
    }

    companion object {
        private const val UNIQUE = "virgil-model-download"
        private const val CHANNEL = "model_download"
        private const val NOTIFICATION_ID = 4101
        const val KEY_MODEL = "model"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"
        const val KEY_PHASE = "phase"
        const val KEY_ERROR = "error"

        fun start(context: Context, spec: ModelSpec, allowMetered: Boolean) {
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(workDataOf(KEY_MODEL to spec.id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (allowMetered) NetworkType.CONNECTED else NetworkType.UNMETERED).setRequiresStorageNotLow(true).build())
                .addTag(UNIQUE)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)

        /** Progresso do download atual: (modelo, baixados, total, erro, fase). */
        fun progress(context: Context): Flow<DownloadProgress?> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(UNIQUE).map { infos ->
                val info = infos.lastOrNull() ?: return@map null
                DownloadProgress(
                    running = info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED,
                    waitingForNetwork = info.state == WorkInfo.State.ENQUEUED,
                    downloaded = info.progress.getLong(KEY_DOWNLOADED, -1),
                    verifying = info.progress.getString(KEY_PHASE) == "verify",
                    error = info.outputData.getString(KEY_ERROR)?.takeIf { info.state == WorkInfo.State.FAILED },
                )
            }
    }
}

data class DownloadProgress(val running: Boolean, val waitingForNetwork: Boolean, val downloaded: Long, val verifying: Boolean, val error: String?)
