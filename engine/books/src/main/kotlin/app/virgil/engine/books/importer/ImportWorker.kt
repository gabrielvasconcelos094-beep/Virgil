package app.virgil.engine.books.importer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.virgil.core.database.VirgilDatabase
import app.virgil.core.model.BookFormat
import app.virgil.engine.books.BookFiles
import java.io.File

/**
 * Processa a importação em segundo plano (WorkManager): sobrevive à rotação de tela e ao app
 * ir para segundo plano. O arquivo já está no armazenamento privado quando o trabalho começa.
 */
class ImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val path = inputData.getString(KEY_PATH) ?: return Result.failure(error("Arquivo ausente."))
        val staged = StagedFile(
            file = File(path),
            displayName = inputData.getString(KEY_NAME).orEmpty(),
            sha256 = inputData.getString(KEY_HASH).orEmpty(),
            format = BookFormat.valueOf(inputData.getString(KEY_FORMAT) ?: BookFormat.EPUB.name),
        )
        if (!staged.file.exists()) return Result.failure(error("O arquivo temporário não existe mais."))
        val importer = importerFor(applicationContext)
        return when (val r = importer.import(staged) { p -> setProgressAsync(workDataOf(KEY_PROGRESS to p, KEY_NAME to staged.displayName)) }) {
            is ImportOutcome.Imported -> Result.success(workDataOf(KEY_BOOK_ID to r.bookId, KEY_TITLE to r.title))
            is ImportOutcome.Duplicate -> Result.success(workDataOf(KEY_BOOK_ID to r.bookId, KEY_TITLE to r.title, KEY_DUPLICATE to true))
            is ImportOutcome.Failed -> Result.failure(error(r.reason))
        }
    }

    private fun error(msg: String): Data = workDataOf(KEY_ERROR to msg, KEY_NAME to inputData.getString(KEY_NAME))

    companion object {
        const val TAG = "virgil-import"
        const val KEY_PATH = "path"
        const val KEY_NAME = "name"
        const val KEY_HASH = "hash"
        const val KEY_FORMAT = "format"
        const val KEY_PROGRESS = "progress"
        const val KEY_BOOK_ID = "book_id"
        const val KEY_TITLE = "title"
        const val KEY_DUPLICATE = "duplicate"
        const val KEY_ERROR = "error"

        fun importerFor(context: Context) = BookImporter(
            db = VirgilDatabase.getInstance(context),
            files = BookFiles(context),
            stagingDir = File(context.cacheDir, "import"),
        )

        fun enqueue(context: Context, staged: StagedFile) {
            val request = OneTimeWorkRequestBuilder<ImportWorker>()
                .addTag(TAG)
                .setInputData(
                    workDataOf(
                        KEY_PATH to staged.file.absolutePath,
                        KEY_NAME to staged.displayName,
                        KEY_HASH to staged.sha256,
                        KEY_FORMAT to staged.format.name,
                    ),
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
