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

@Dao
interface ReviewDao {
    @Query("SELECT COUNT(*) FROM review_cards WHERE due_at <= :now")
    fun observeDueCount(now: Long): Flow<Int>

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

    @Upsert
    suspend fun upsert(entity: DifficultyAnalysisEntity)
}
