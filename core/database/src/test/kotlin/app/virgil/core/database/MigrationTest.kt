package app.virgil.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Cria um banco exatamente como a versão 1 (a partir do esquema exportado) e abre com a versão atual. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MigrationTest {
    @Test fun `v1 para v2 preserva os dados e adiciona analysis_error`() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        ctx.deleteDatabase(name)
        val schema = Json.parseToJsonElement(File("schemas/app.virgil.core.database.VirgilDatabase/1.json").readText()).jsonObject["database"]!!.jsonObject
        SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            schema["entities"]!!.jsonArray.forEach { e ->
                val o = e.jsonObject
                val table = o["tableName"]!!.jsonPrimitive.content
                db.execSQL(o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                o["indices"]?.jsonArray?.forEach { i -> db.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
            }
            schema["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.execSQL(
                "INSERT INTO books (title, author, language, language_variant, format, storage_dir, cover_path, content_hash, file_size, publisher, description, published_date, chapter_count, total_chars, added_at, last_opened_at, finished_at, progress, analysis_state) " +
                    "VALUES ('Mrs Dalloway', 'Virginia Woolf', 'en-GB', NULL, 'EPUB', 'x', NULL, 'h', 1, NULL, NULL, NULL, 1, 10, 0, NULL, NULL, 0.4, 'Failed')",
            )
            db.version = 1
        }
        val room = Room.databaseBuilder(ctx, VirgilDatabase::class.java, name).allowMainThreadQueries().build()
        val book = room.books().get(1)!!
        assertThat(book.title).isEqualTo("Mrs Dalloway")
        assertThat(book.progress).isEqualTo(0.4f)
        assertThat(book.analysisError).isNull()
        // Livros que falharam na versão antiga (sem motivo registrado) voltam para a fila de análise.
        assertThat(room.books().observeNeedingAnalysis(2).first()).containsExactly(1L)
        room.close()
    }
}
