package app.virgil.engine.analysis

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.virgil.core.database.VirgilDatabase
import app.virgil.core.model.BookFormat
import app.virgil.core.model.CefrLevel
import app.virgil.engine.books.BookFiles
import app.virgil.engine.books.importer.BookImporter
import app.virgil.engine.books.importer.ImportOutcome
import app.virgil.engine.books.importer.StagedFile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Importação → análise → persistência → reabertura do banco (como após reiniciar o app). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AnalysisPipelineTest {
    @get:Rule val tmp = TemporaryFolder()
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    private fun open(name: String) = Room.databaseBuilder(ctx, VirgilDatabase::class.java, name).allowMainThreadQueries().build()

    private fun importTxt(db: VirgilDatabase, files: BookFiles, text: String, hash: String): Long {
        val f = tmp.newFile("$hash.txt"); f.writeText(text)
        val r = runBlocking { BookImporter(db, files, tmp.newFolder("stage-$hash")).import(StagedFile(f, "$hash.txt", hash, BookFormat.TXT)) }
        return (r as ImportOutcome.Imported).bookId
    }

    private val simpleChapter = "Tom has a small dog. The dog is brown and white. Its name is Max. Every morning Tom and Max go to the park. " +
        "The park is near their house. There are many trees in the park. Tom likes to sit under a big tree. Max likes to run. " +
        "One day it is very cold. Tom puts on his coat and his hat. Max does not want to go out. He wants to stay in the warm kitchen.\n\n"
    private val hardChapter = "For having lived in Westminster, how many years now, over twenty, one feels even in the midst of the traffic, or waking at night, " +
        "a particular hush, or solemnity; an indescribable pause; a suspense, but that might be her heart, affected, they said, by influenza, before Big Ben strikes. " +
        "The leaden circles dissolved in the air, and the tranquil waterway leading to the uttermost ends of the earth flowed sombre under an overcast sky, " +
        "whilst the haze rested on the low shores that ran out to sea in vanishing flatness, brooding motionless over the biggest, and the greatest, town on earth.\n\n"

    @Test fun analisaPersisteERecuperaAposReabrir() = runBlocking {
        val name = "pipeline.db"
        ctx.deleteDatabase(name)
        val files = BookFiles(tmp.newFolder("books"))
        val text = buildString {
            append("CHAPTER I\n\n"); repeat(6) { append(simpleChapter) }
            append("CHAPTER II\n\n"); repeat(6) { append(simpleChapter) }
            append("CHAPTER III\n\n"); repeat(5) { append(hardChapter) }
        }
        var db = open(name)
        val id = importTxt(db, files, text, "book")
        AnalysisWorker.analyze(db, files, id)
        db.close()

        db = open(name) // "reinício do app"
        val d = BookInsights(db).details(id).first()!!
        assertThat(d.state.name).isEqualTo("Done")
        assertThat(d.range).isNotNull()
        assertThat(d.findings).isNotEmpty()
        assertThat(d.metrics!!.summary).isNotEmpty()
        val levels = d.chapters.map { it.range }
        assertThat(levels.all { it != null }).isTrue()
        println("Livro: ${d.range!!.label}; capítulos: ${levels.map { it!!.label }}")
        // O capítulo literário deve ser estimado acima dos capítulos simples.
        assertThat(levels[2]!!.high).isGreaterThan(levels[0]!!.high)
        assertThat(levels[0]!!.low).isAtMost(CefrLevel.A2)
        db.close()
    }

    @Test fun falhaRegistraMotivoESeraRefeitaEmNovaVersao() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ctx, VirgilDatabase::class.java).allowMainThreadQueries().build()
        val files = BookFiles(tmp.newFolder("books2"))
        val id = importTxt(db, files, "The end.\n\nIt was short.", "short")
        AnalysisWorker.analyze(db, files, id)
        val book = db.books().get(id)!!
        assertThat(book.analysisState).isEqualTo("Failed")
        assertThat(book.analysisError).contains("texto curto demais")
        assertThat(db.books().observeNeedingAnalysis(DifficultyAnalyzer.VERSION).first()).doesNotContain(id)
        assertThat(db.books().observeNeedingAnalysis(DifficultyAnalyzer.VERSION + 1).first()).contains(id)
        db.close()
    }
}
