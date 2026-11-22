package app.virgil.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.virgil.core.database.entity.BookEntity
import app.virgil.core.database.entity.BookmarkEntity
import app.virgil.core.database.entity.ChapterEntity
import app.virgil.core.database.entity.ReadingPositionEntity
import app.virgil.core.database.entity.TocEntryEntity
import kotlinx.coroutines.flow.Flow

/** Linha da estante: livro + faixa de dificuldade (se já analisado). */
data class ShelfRow(
    val id: Long,
    val title: String,
    val author: String?,
    val language: String?,
    val format: String,
    val cover_path: String?,
    val progress: Float,
    val added_at: Long,
    val last_opened_at: Long?,
    val finished_at: Long?,
    val chapter_count: Int,
    val total_chars: Long,
    val language_variant: String?,
    val analysis_state: String,
    val level_low: String?,
    val level_high: String?,
    val confidence: Float?,
    val storage_dir: String,
)

@Dao
interface BookDao {
    @Query(
        """
        SELECT b.id, b.title, b.author, b.language, b.format, b.cover_path, b.progress, b.added_at,
               b.last_opened_at, b.finished_at, b.chapter_count, b.total_chars, b.language_variant, b.analysis_state,
               d.level_low, d.level_high, d.confidence, b.storage_dir
        FROM books b LEFT JOIN difficulty_analyses d ON d.book_id = b.id
        ORDER BY COALESCE(b.last_opened_at, b.added_at) DESC
        """,
    )
    fun observeShelf(): Flow<List<ShelfRow>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE content_hash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: BookEntity): Long

    @Query("UPDATE books SET last_opened_at = :at WHERE id = :id")
    suspend fun markOpened(id: Long, at: Long)

    @Query("UPDATE books SET progress = :progress, finished_at = CASE WHEN :progress >= 0.995 AND finished_at IS NULL THEN :at ELSE finished_at END WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Float, at: Long)

    @Query("UPDATE books SET analysis_state = :state WHERE id = :id")
    suspend fun setAnalysisState(id: Long, state: String)

    @Query("UPDATE books SET language = :language, language_variant = :variant WHERE id = :id")
    suspend fun setLanguage(id: Long, language: String?, variant: String?)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Query("SELECT * FROM chapters WHERE book_id = :bookId ORDER BY chapter_index")
    suspend fun chapters(bookId: Long): List<ChapterEntity>

    @Insert
    suspend fun insertToc(entries: List<TocEntryEntity>)

    @Query("SELECT * FROM toc_entries WHERE book_id = :bookId ORDER BY ordinal")
    suspend fun toc(bookId: Long): List<TocEntryEntity>

    @Upsert
    suspend fun savePosition(position: ReadingPositionEntity)

    @Query("SELECT * FROM reading_positions WHERE book_id = :bookId")
    suspend fun position(bookId: Long): ReadingPositionEntity?

    @Insert
    suspend fun addBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    @Query("SELECT * FROM bookmarks WHERE book_id = :bookId ORDER BY chapter_index, char_offset")
    fun observeBookmarks(bookId: Long): Flow<List<BookmarkEntity>>

    /** Importação atômica: livro, capítulos e sumário entram juntos ou nada entra. */
    @Transaction
    suspend fun insertImported(book: BookEntity, chapters: List<ChapterEntity>, toc: List<TocEntryEntity>): Long {
        val id = insert(book)
        insertChapters(chapters.map { it.copy(bookId = id) })
        insertToc(toc.map { it.copy(bookId = id) })
        return id
    }
}
