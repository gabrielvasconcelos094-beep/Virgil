package app.virgil.engine.books.importer

import android.content.Context
import android.net.Uri
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.virgil.engine.books.parse.ParseException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import java.util.UUID

/** Importação vista pela interface. */
sealed interface ImportJob {
    val id: UUID
    val name: String
    data class Running(override val id: UUID, override val name: String, val progress: Float) : ImportJob
    data class Duplicate(override val id: UUID, override val name: String, val title: String) : ImportJob
    data class Failed(override val id: UUID, override val name: String, val message: String) : ImportJob
}

/**
 * Ponte entre o seletor de arquivos e o [ImportWorker]. Importações concluídas com sucesso
 * somem da lista (o livro aparece na estante); duplicatas e falhas ficam até serem dispensadas.
 */
class ImportController(private val context: Context) {
    private val workManager = WorkManager.getInstance(context)
    private val dismissed = MutableStateFlow<Set<UUID>>(emptySet())

    val jobs: Flow<List<ImportJob>> = combine(workManager.getWorkInfosByTagFlow(ImportWorker.TAG), dismissed) { infos, gone ->
        infos.filter { it.id !in gone }.mapNotNull { info ->
            val name = info.progress.getString(ImportWorker.KEY_NAME) ?: info.outputData.getString(ImportWorker.KEY_NAME) ?: "arquivo"
            when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED ->
                    ImportJob.Running(info.id, name, info.progress.getFloat(ImportWorker.KEY_PROGRESS, 0f))
                WorkInfo.State.SUCCEEDED ->
                    if (info.outputData.getBoolean(ImportWorker.KEY_DUPLICATE, false)) {
                        ImportJob.Duplicate(info.id, name, info.outputData.getString(ImportWorker.KEY_TITLE).orEmpty())
                    } else null
                WorkInfo.State.FAILED ->
                    ImportJob.Failed(info.id, name, info.outputData.getString(ImportWorker.KEY_ERROR) ?: "Não foi possível importar o arquivo.")
                WorkInfo.State.CANCELLED -> null
            }
        }
    }

    /** Copia o arquivo escolhido e agenda o processamento. Devolve uma mensagem de erro, se houver. */
    suspend fun start(uri: Uri): String? = try {
        val staged = ImportWorker.importerFor(context).stage(context.contentResolver, uri)
        ImportWorker.enqueue(context, staged)
        null
    } catch (e: ParseException) {
        e.message
    } catch (e: SecurityException) {
        "Sem permissão para ler o arquivo escolhido."
    } catch (e: java.io.IOException) {
        "Não foi possível ler o arquivo escolhido."
    }

    fun dismiss(id: UUID) { dismissed.value = dismissed.value + id }

    /** Remove do histórico do WorkManager importações já concluídas (chamado ao abrir o app). */
    fun pruneFinished() { workManager.pruneWork() }
}
