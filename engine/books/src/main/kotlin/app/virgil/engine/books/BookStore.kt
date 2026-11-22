package app.virgil.engine.books

import android.util.LruCache
import app.virgil.core.database.VirgilDatabase
import app.virgil.core.database.entity.BookEntity
import app.virgil.core.database.entity.BookmarkEntity
import app.virgil.core.database.entity.ChapterEntity
import app.virgil.core.database.entity.ReadingPositionEntity
import app.virgil.core.database.entity.ReadingSessionEntity
import app.virgil.core.database.entity.TocEntryEntity
import app.virgil.core.model.Language
import app.virgil.engine.books.model.ChapterDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Livro aberto no leitor: metadados, capítulos, sumário e última posição. */
data class OpenedBook(
    val book: BookEntity,
    val language: Language?,
    val chapters: List<ChapterEntity>,
    val toc: List<TocEntryEntity>,
    val position: ReadingPositionEntity?,
) {
    val totalChars: Long get() = book.totalChars.coerceAtLeast(1)

    fun globalProgress(chapterIndex: Int, offset: Int): Float {
        val ch = chapters.getOrNull(chapterIndex) ?: return 0f
        return ((ch.startChar + offset).toFloat() / totalChars).coerceIn(0f, 1f)
    }
}

/**
 * Acesso aos livros para o leitor e a biblioteca. Capítulos são lidos do disco sob demanda,
 * com um cache pequeno (o atual e os vizinhos) para que trocar de capítulo seja imediato.
 */
class BookStore(private val db: VirgilDatabase, private val files: BookFiles, private val clock: () -> Long = System::currentTimeMillis) {
    private val cache = LruCache<String, ChapterDoc>(4)

    suspend fun open(bookId: Long): OpenedBook? = withContext(Dispatchers.IO) {
        val book = db.books().get(bookId) ?: return@withContext null
        db.books().markOpened(bookId, clock())
        OpenedBook(book, Language.fromTag(book.language), db.books().chapters(bookId), db.books().toc(bookId), db.books().position(bookId))
    }

    suspend fun chapter(book: BookEntity, chapter: ChapterEntity): ChapterDoc = withContext(Dispatchers.IO) {
        val key = "${book.id}:${chapter.chapterIndex}"
        cache.get(key) ?: files.readChapter(book.storageDir, chapter.contentPath).also { cache.put(key, it) }
    }

    fun imageFile(book: BookEntity, relative: String) = files.resolve(book.storageDir, relative)

    suspend fun savePosition(opened: OpenedBook, chapterIndex: Int, offset: Int) = withContext(Dispatchers.IO) {
        val now = clock()
        db.books().savePosition(ReadingPositionEntity(opened.book.id, chapterIndex, offset, now))
        db.books().updateProgress(opened.book.id, opened.globalProgress(chapterIndex, offset), now)
    }

    suspend fun markFinished(bookId: Long) = withContext(Dispatchers.IO) { db.books().updateProgress(bookId, 1f, clock()) }

    fun bookmarks(bookId: Long): Flow<List<BookmarkEntity>> = db.books().observeBookmarks(bookId)

    suspend fun addBookmark(bookId: Long, chapterIndex: Int, offset: Int, excerpt: String) = withContext(Dispatchers.IO) {
        db.books().addBookmark(BookmarkEntity(bookId = bookId, chapterIndex = chapterIndex, charOffset = offset, excerpt = excerpt.take(160), note = null, createdAt = clock()))
    }

    suspend fun deleteBookmark(id: Long) = withContext(Dispatchers.IO) { db.books().deleteBookmark(id) }

    suspend fun recordSession(bookId: Long, startedAt: Long, activeMs: Long, charsAdvanced: Long, lookups: Int) = withContext(Dispatchers.IO) {
        if (activeMs < 10_000) return@withContext // toques acidentais não contam como leitura
        db.stats().insertSession(ReadingSessionEntity(bookId = bookId, startedAt = startedAt, endedAt = clock(), activeMs = activeMs, charsAdvanced = charsAdvanced, lookups = lookups))
    }

    /** Remove o livro do banco (capítulos, posição, marcadores em cascata) e seus arquivos. */
    suspend fun delete(bookId: Long) = withContext(Dispatchers.IO) {
        val book = db.books().get(bookId) ?: return@withContext
        db.books().delete(bookId)
        files.delete(book.storageDir)
        cache.evictAll()
    }
}
