package app.virgil.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.virgil.core.database.entity.ChapterDifficultyEntity
import app.virgil.core.database.entity.DifficultyAnalysisEntity
import app.virgil.core.database.entity.InstalledModelEntity
import app.virgil.core.database.entity.LanguageProfileEntity
import app.virgil.core.database.entity.ReadingSessionEntity
import app.virgil.core.database.entity.ReviewCardEntity
import app.virgil.core.database.entity.ReviewLogEntity
import app.virgil.core.database.entity.TranslationCacheEntity
import kotlinx.coroutines.flow.Flow

/** Item de revisão: entrada de vocabulário + estado do cartão (nulo para palavras ainda não revisadas). */
data class ReviewItemRow(
    val id: Long,
    val term: String,
    val language: String,
    val translation: String,
    val context_sentence: String?,
    val context_translation: String?,
    val book_title: String?,
    val chapter_index: Int?,
    val stability: Double?,
    val difficulty: Double?,
    val due_at: Long?,
    val last_review_at: Long?,
    val reps: Int?,
    val lapses: Int?,
    val phase: String?,
)

@Dao
interface ReviewDao {
    @Query(
        """
        SELECT COUNT(*) FROM review_cards c JOIN vocabulary v ON v.id = c.vocabulary_id
        WHERE c.due_at <= :now AND v.state NOT IN ('Known', 'Ignored') AND v.translation != ''
        """,
    )
    fun observeDueCount(now: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM vocabulary v LEFT JOIN review_cards c ON c.vocabulary_id = v.id
        WHERE c.vocabulary_id IS NULL AND v.state IN ('New', 'Learning') AND v.translation != ''
        """,
    )
    fun observeNewAvailable(): Flow<Int>

    /** Palavras revisadas pela primeira vez a partir de [since] (limite diário de novidades). */
    @Query(
        """
        SELECT COUNT(DISTINCT l.vocabulary_id) FROM review_logs l WHERE l.reviewed_at >= :since
          AND NOT EXISTS (SELECT 1 FROM review_logs p WHERE p.vocabulary_id = l.vocabulary_id AND p.reviewed_at < :since)
        """,
    )
    fun observeNewStudiedSince(since: Long): Flow<Int>

    @Query(
        """
        SELECT v.id, v.term, v.language, v.translation, v.context_sentence, v.context_translation, b.title AS book_title, v.chapter_index,
               c.stability, c.difficulty, c.due_at, c.last_review_at, c.reps, c.lapses, c.phase
        FROM review_cards c JOIN vocabulary v ON v.id = c.vocabulary_id LEFT JOIN books b ON b.id = v.book_id
        WHERE c.due_at <= :now AND v.state NOT IN ('Known', 'Ignored') AND v.translation != ''
        ORDER BY c.due_at LIMIT :limit
        """,
    )
    suspend fun dueItems(now: Long, limit: Int): List<ReviewItemRow>

    @Query(
        """
        SELECT v.id, v.term, v.language, v.translation, v.context_sentence, v.context_translation, b.title AS book_title, v.chapter_index,
               NULL AS stability, NULL AS difficulty, NULL AS due_at, NULL AS last_review_at, NULL AS reps, NULL AS lapses, NULL AS phase
        FROM vocabulary v LEFT JOIN review_cards c ON c.vocabulary_id = v.id LEFT JOIN books b ON b.id = v.book_id
        WHERE c.vocabulary_id IS NULL AND v.state IN ('New', 'Learning') AND v.translation != ''
        ORDER BY v.created_at LIMIT :limit
        """,
    )
    suspend fun newItems(limit: Int): List<ReviewItemRow>

    /** Revisões por dia (para o gráfico de progresso). */
    @Query("SELECT (reviewed_at + :tzOffsetMs) / 86400000 AS day, COUNT(*) AS ms FROM review_logs WHERE reviewed_at >= :since GROUP BY day ORDER BY day")
    fun observeDailyReviews(since: Long, tzOffsetMs: Long): Flow<List<DailyReading>>

    @Query("SELECT * FROM review_cards WHERE due_at <= :now ORDER BY due_at LIMIT :limit")
    suspend fun due(now: Long, limit: Int): List<ReviewCardEntity>

    @Query("SELECT * FROM review_cards WHERE vocabulary_id = :id")
    suspend fun card(id: Long): ReviewCardEntity?

    @Upsert
    suspend fun upsert(card: ReviewCardEntity)

    @Insert
    suspend fun log(entry: ReviewLogEntity)

    @Transaction
    suspend fun record(card: ReviewCardEntity, entry: ReviewLogEntity) {
        upsert(card)
        log(entry)
    }

    @Query("SELECT COUNT(*) FROM review_logs")
    fun observeReviewCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM review_logs WHERE reviewed_at >= :since")
    fun observeReviewsSince(since: Long): Flow<Int>
}

@Dao
interface StatsDao {
    @Insert
    suspend fun insertSession(session: ReadingSessionEntity)

    @Query("SELECT COALESCE(SUM(active_ms), 0) FROM reading_sessions")
    fun observeTotalReadingMs(): Flow<Long>

    /** Tempo de leitura por dia (epoch-day local calculado pelo chamador via offset). */
    @Query(
        """
        SELECT (started_at + :tzOffsetMs) / 86400000 AS day, SUM(active_ms) AS ms
        FROM reading_sessions WHERE started_at >= :since GROUP BY day ORDER BY day
        """,
    )
    fun observeDailyReading(since: Long, tzOffsetMs: Long): Flow<List<DailyReading>>

    @Query("SELECT COUNT(*) FROM books")
    fun observeBookCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM books WHERE finished_at IS NOT NULL")
    fun observeFinishedCount(): Flow<Int>
}

data class DailyReading(val day: Long, val ms: Long)

@Dao
interface TranslationCacheDao {
    @Query("SELECT * FROM translation_cache WHERE cache_key = :key")
    suspend fun get(key: String): TranslationCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: TranslationCacheEntity)

    @Query("UPDATE translation_cache SET last_used_at = :at, hit_count = hit_count + 1 WHERE cache_key = :key")
    suspend fun touch(key: String, at: Long)

    /** Mantém o cache limitado (LRU aproximado). */
    @Query("DELETE FROM translation_cache WHERE cache_key IN (SELECT cache_key FROM translation_cache ORDER BY last_used_at DESC LIMIT -1 OFFSET :keep)")
    suspend fun trim(keep: Int)

    @Query("DELETE FROM translation_cache")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM translation_cache")
    fun observeSize(): Flow<Int>
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM installed_models")
    fun observeAll(): Flow<List<InstalledModelEntity>>

    @Query("SELECT * FROM installed_models WHERE model_id = :id")
    suspend fun get(id: String): InstalledModelEntity?

    @Upsert
    suspend fun upsert(model: InstalledModelEntity)

    @Query("DELETE FROM installed_models WHERE model_id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM language_profiles")
    fun observeAll(): Flow<List<LanguageProfileEntity>>

    @Upsert
    suspend fun upsert(profile: LanguageProfileEntity)

    @Query("DELETE FROM language_profiles WHERE language = :language")
    suspend fun delete(language: String)
}

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM difficulty_analyses WHERE book_id = :bookId")
    fun observe(bookId: Long): Flow<DifficultyAnalysisEntity?>

    @Query("SELECT * FROM chapter_difficulty WHERE book_id = :bookId ORDER BY chapter_index")
    fun observeChapters(bookId: Long): Flow<List<ChapterDifficultyEntity>>

    @Upsert
    suspend fun upsertChapter(entity: ChapterDifficultyEntity)

    @Query("DELETE FROM chapter_difficulty WHERE book_id = :bookId")
    suspend fun deleteChapters(bookId: Long)

    @Upsert
    suspend fun upsert(entity: DifficultyAnalysisEntity)
}
