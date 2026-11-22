package app.virgil.engine.books

import android.content.Context
import app.virgil.engine.books.model.ChapterDoc
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Layout no armazenamento privado do app (não acessível a outros apps, excluído do backup):
 *
 *   files/books/<dir>/original.epub|original.txt   arquivo importado (permite reprocessar)
 *   files/books/<dir>/cover.<ext>                  capa extraída
 *   files/books/<dir>/images/NNNN.<ext>            imagens do texto
 *   files/books/<dir>/chapters/NNNN.json           capítulos no modelo de blocos
 */
class BookFiles(val root: File) {
    constructor(context: Context) : this(File(context.filesDir, "books"))

    fun dir(storageDir: String) = File(root, storageDir)
    fun chapterPath(index: Int) = "chapters/%04d.json".format(index)
    fun resolve(storageDir: String, relative: String) = File(dir(storageDir), relative)

    fun readChapter(storageDir: String, relativePath: String): ChapterDoc =
        json.decodeFromString(ChapterDoc.serializer(), resolve(storageDir, relativePath).readText())

    fun writeChapter(dir: File, doc: ChapterDoc) {
        val f = File(dir, chapterPath(doc.index))
        f.parentFile?.mkdirs()
        // Escrita atômica: arquivo temporário + rename, para nunca deixar capítulo truncado.
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(json.encodeToString(ChapterDoc.serializer(), doc))
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    fun delete(storageDir: String) {
        if (storageDir.isNotBlank() && !storageDir.contains("..")) dir(storageDir).deleteRecursively()
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    }
}
