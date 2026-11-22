package app.virgil.engine.books.importer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import app.virgil.core.database.VirgilDatabase
import app.virgil.core.database.entity.BookEntity
import app.virgil.core.database.entity.ChapterEntity
import app.virgil.core.database.entity.TocEntryEntity
import app.virgil.core.model.BookFormat
import app.virgil.core.model.Language
import app.virgil.core.text.LanguageDetector
import app.virgil.engine.books.BookFiles
import app.virgil.engine.books.parse.ChapterSink
import app.virgil.engine.books.parse.EpubParser
import app.virgil.engine.books.parse.ParseException
import app.virgil.engine.books.parse.TxtParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Arquivo copiado para o cache privado, pronto para ser processado em segundo plano. */
data class StagedFile(val file: File, val displayName: String, val sha256: String, val format: BookFormat)

sealed interface ImportOutcome {
    data class Imported(val bookId: Long, val title: String) : ImportOutcome
    data class Duplicate(val bookId: Long, val title: String) : ImportOutcome
    data class Failed(val reason: String) : ImportOutcome
}

class BookImporter(
    private val db: VirgilDatabase,
    private val files: BookFiles,
    private val stagingDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * Copia o documento escolhido no seletor do Android para o armazenamento privado.
     * Feito imediatamente (a permissão de leitura do URI é temporária), calculando o SHA-256 no caminho.
     */
    suspend fun stage(resolver: ContentResolver, uri: Uri): StagedFile = withContext(Dispatchers.IO) {
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "livro"
        stagingDir.mkdirs()
        val out = File(stagingDir, UUID.randomUUID().toString())
        val digest = MessageDigest.getInstance("SHA-256")
        val head = ByteArray(4)
        var headLen = 0
        resolver.openInputStream(uri)?.use { input ->
            out.outputStream().buffered().use { o ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (headLen < 4) { val k = minOf(4 - headLen, n); System.arraycopy(buf, 0, head, headLen, k); headLen += k }
                    digest.update(buf, 0, n)
                    o.write(buf, 0, n)
                    total += n
                    if (total > MAX_FILE) { out.delete(); throw ParseException("Arquivo grande demais (máximo de 200 MB).") }
                }
            }
        } ?: throw ParseException("Não foi possível abrir o arquivo escolhido.")
        StagedFile(out, name, digest.digest().joinToString("") { "%02x".format(it) }, detectFormat(head, headLen, name, out))
    }

    suspend fun import(staged: StagedFile, onProgress: (Float) -> Unit = {}): ImportOutcome = withContext(Dispatchers.IO) {
        db.books().findByHash(staged.sha256)?.let {
            staged.file.delete()
            return@withContext ImportOutcome.Duplicate(it.id, it.title)
        }
        val storageDir = UUID.randomUUID().toString()
        val dir = files.dir(storageDir).apply { mkdirs() }
        try {
            val original = File(dir, if (staged.format == BookFormat.EPUB) "original.epub" else "original.txt")
            if (!staged.file.renameTo(original)) { staged.file.copyTo(original, overwrite = true); staged.file.delete() }

            // Amostra de texto para identificar o idioma, coletada enquanto os capítulos são gravados.
            val sample = StringBuilder()
            val sink = ChapterSink { doc ->
                files.writeChapter(dir, doc)
                if (sample.length < 40_000) doc.blocks.forEach { b -> if (sample.length < 40_000) sample.append(b.text).append('\n') }
            }
            val fallbackTitle = staged.displayName.substringBeforeLast('.').replace('_', ' ').trim()
            val parsed = when (staged.format) {
                BookFormat.EPUB -> EpubParser().parse(original, dir, sink, onProgress)
                BookFormat.TXT -> TxtParser().parse(original, fallbackTitle, sink).also { onProgress(1f) }
            }
            val language = LanguageDetector.resolve(sample.toString(), Language.fromTag(parsed.meta.language))
            val now = clock()
            val title = parsed.meta.title?.takeIf { it.isNotBlank() } ?: fallbackTitle.ifBlank { "Sem título" }
            val book = BookEntity(
                title = title.take(300),
                author = parsed.meta.author?.take(300),
                language = language?.tag,
                languageVariant = null,
                format = staged.format.name,
                storageDir = storageDir,
                coverPath = parsed.cover?.name,
                contentHash = staged.sha256,
                fileSize = original.length(),
                publisher = parsed.meta.publisher,
                description = parsed.meta.description,
                publishedDate = parsed.meta.date,
                chapterCount = parsed.chapters.size,
                totalChars = parsed.totalChars,
                addedAt = now,
                lastOpenedAt = null,
                finishedAt = null,
            )
            val chapters = parsed.chapters.map {
                ChapterEntity(0, it.index, it.title, files.chapterPath(it.index), it.startChar, it.charCount, it.wordCount)
            }
            val toc = parsed.toc.mapIndexed { i, t -> TocEntryEntity(bookId = 0, ordinal = i, depth = t.depth, title = t.title.take(300), chapterIndex = t.chapterIndex, charOffset = t.charOffset) }
            val id = db.books().insertImported(book, chapters, toc)
            ImportOutcome.Imported(id, title)
        } catch (e: ParseException) {
            dir.deleteRecursively(); staged.file.delete()
            ImportOutcome.Failed(e.message ?: "Não foi possível importar o arquivo.")
        } catch (e: java.util.zip.ZipException) {
            dir.deleteRecursively(); staged.file.delete()
            ImportOutcome.Failed("O arquivo EPUB está corrompido ou incompleto.")
        } catch (e: OutOfMemoryError) {
            dir.deleteRecursively(); staged.file.delete()
            ImportOutcome.Failed("O livro é grande demais para a memória disponível neste aparelho.")
        } catch (e: Exception) {
            dir.deleteRecursively(); staged.file.delete()
            ImportOutcome.Failed("Não foi possível importar o arquivo (${e.javaClass.simpleName}).")
        }
    }

    private fun detectFormat(head: ByteArray, len: Int, name: String, file: File): BookFormat {
        val isZip = len >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() && head[2] == 3.toByte() && head[3] == 4.toByte()
        if (isZip) return BookFormat.EPUB
        if (name.endsWith(".epub", ignoreCase = true)) throw ParseException("O arquivo tem extensão .epub, mas não é um EPUB válido.")
        // Texto: sem bytes nulos no início (exceto UTF-16 com BOM).
        val probe = file.inputStream().use { it.readNBytes(8192) }
        val utf16 = probe.size >= 2 && ((probe[0] == 0xFF.toByte() && probe[1] == 0xFE.toByte()) || (probe[0] == 0xFE.toByte() && probe[1] == 0xFF.toByte()))
        if (!utf16 && probe.any { it == 0.toByte() }) throw ParseException("Formato não suportado. O Virgil importa arquivos EPUB e TXT.")
        return BookFormat.TXT
    }

    companion object {
        const val MAX_FILE = 200L * 1024 * 1024
    }
}
