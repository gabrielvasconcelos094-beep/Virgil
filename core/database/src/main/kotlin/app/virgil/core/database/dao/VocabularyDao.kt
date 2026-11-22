package app.virgil.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.virgil.core.database.entity.LookupHistoryEntity
import app.virgil.core.database.entity.VocabularyEntity
import kotlinx.coroutines.flow.Flow

data class VocabularyRow(
    val id: Long,
    val term: String,
    val lemma: String?,
    val language: String,
    val kind: String,
    val translation: String,
    val context_sentence: String?,
    val context_translation: String?,
    val book_id: Long?,
    val book_title: String?,
    val chapter_index: Int?,
    val created_at: Long,
    val lookup_count: Int,
    val state: String,
    val note: String?,
)

@Dao
interface VocabularyDao {
    /**
     * Lista filtrável. Parâmetros nulos desativam o filtro correspondente.
     * [query] busca no termo e na tradução (LIKE, sem FTS por enquanto — suficiente até dezenas de milhares de entradas).
     */
    @Query(
        """
        SELECT v.id, v.term, v.lemma, v.language, v.kind, v.translation, v.context_sentence, v.context_translation,
               v.book_id, b.title AS book_title, v.chapter_index, v.created_at, v.lookup_count, v.state, v.note
        FROM vocabulary v LEFT JOIN books b ON b.id = v.book_id
        WHERE (:language IS NULL OR v.language = :language)
          AND (:bookId IS NULL OR v.book_id = :bookId)
          AND (:state IS NULL OR v.state = :state)
          AND (:since IS NULL OR v.created_at >= :since)
          AND (:query IS NULL OR v.term_key LIKE '%' || :query || '%' OR v.translation LIKE '%' || :query || '%')
        ORDER BY v.created_at DESC
        """,
    )
    fun observe(language: String?, bookId: Long?, state: String?, since: Long?, query: String?): Flow<List<VocabularyRow>>

    @Query(
        """
        SELECT v.id, v.term, v.lemma, v.language, v.kind, v.translation, v.context_sentence, v.context_translation,
               v.book_id, b.title AS book_title, v.chapter_index, v.created_at, v.lookup_count, v.state, v.note
        FROM vocabulary v LEFT JOIN books b ON b.id = v.book_id WHERE v.id = :id
        """,
    )
    fun observeOne(id: Long): Flow<VocabularyRow?>

    @Query("SELECT * FROM vocabulary WHERE id = :id")
    suspend fun get(id: Long): VocabularyEntity?

    @Query("SELECT * FROM vocabulary WHERE language = :language AND term_key = :termKey")
    suspend fun find(language: String, termKey: String): VocabularyEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: VocabularyEntity): Long

    @Update
    suspend fun update(entry: VocabularyEntity)

    @Query("DELETE FROM vocabulary WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE vocabulary SET state = :state, updated_at = :at WHERE id = :id")
    suspend fun setState(id: Long, state: String, at: Long)

    /** Salva uma consulta: cria a entrada ou incrementa o contador de uma já existente. */
    @Transaction
    suspend fun recordLookup(entry: VocabularyEntity): Long {
        val existing = find(entry.language, entry.termKey)
        return if (existing == null) {
            insert(entry)
        } else {
            update(
                existing.copy(
                    lookupCount = existing.lookupCount + 1,
                    updatedAt = entry.updatedAt,
                    // Mantém a primeira tradução/contexto (são os do encontro original), exceto se vazios.
                    contextSentence = existing.contextSentence ?: entry.contextSentence,
                    contextTranslation = existing.contextTranslation ?: entry.contextTranslation,
                ),
            )
            existing.id
        }
    }

    @Query("SELECT COUNT(*) FROM vocabulary")
    fun observeCount(): Flow<Int>

    @Query("SELECT language, COUNT(*) AS total, SUM(CASE WHEN state = 'Known' THEN 1 ELSE 0 END) AS known FROM vocabulary GROUP BY language")
    fun observeByLanguage(): Flow<List<LanguageCount>>

    /** Palavra para o widget: escolha pseudoaleatória estável por [seed] entre as não ignoradas. */
    @Query(
        """
        SELECT v.id, v.term, v.lemma, v.language, v.kind, v.translation, v.context_sentence, v.context_translation,
               v.book_id, b.title AS book_title, v.chapter_index, v.created_at, v.lookup_count, v.state, v.note
        FROM vocabulary v LEFT JOIN books b ON b.id = v.book_id
        WHERE v.state != 'Ignored'
        ORDER BY (v.id * 2654435761 + :seed) % 1000003
        LIMIT 1
        """,
    )
    suspend fun pickForWidget(seed: Long): VocabularyRow?

    @Insert
    suspend fun insertHistory(entry: LookupHistoryEntity)

    @Query("SELECT COUNT(*) FROM lookup_history")
    fun observeLookupCount(): Flow<Int>
}

data class LanguageCount(val language: String, val total: Int, val known: Int)
