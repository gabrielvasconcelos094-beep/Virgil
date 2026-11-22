package app.virgil.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Esquema local do Virgil. Princípios:
 *  - Arquivos de livros, capas e modelos ficam no armazenamento privado do app;
 *    o banco guarda apenas caminhos relativos e metadados (nada de BLOBs grandes).
 *  - Textos de capítulos NÃO ficam no banco: são extraídos para arquivos por capítulo,
 *    lidos sob demanda pelo leitor (evita cursores gigantes e limite de 2 MB por linha do CursorWindow).
 *  - Timestamps em epoch millis (UTC).
 */

@Entity(tableName = "books", indices = [Index("language"), Index("last_opened_at"), Index(value = ["content_hash"], unique = true)])
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String?,
    /** Tag BCP-47 do idioma detectado/declarado (ex.: "en-GB"). */
    val language: String?,
    /** Evidência da variante (ex.: "ortografia britânica: colour, centre"). */
    @ColumnInfo(name = "language_variant") val languageVariant: String?,
    val format: String,
    /** Diretório do livro relativo a filesDir/books. */
    @ColumnInfo(name = "storage_dir") val storageDir: String,
    @ColumnInfo(name = "cover_path") val coverPath: String?,
    @ColumnInfo(name = "content_hash") val contentHash: String,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    val publisher: String?,
    val description: String?,
    @ColumnInfo(name = "published_date") val publishedDate: String?,
    @ColumnInfo(name = "chapter_count") val chapterCount: Int,
    @ColumnInfo(name = "total_chars") val totalChars: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long?,
    @ColumnInfo(name = "finished_at") val finishedAt: Long?,
    /** Progresso global 0..1, desnormalizado para a estante. */
    val progress: Float = 0f,
    @ColumnInfo(name = "analysis_state") val analysisState: String = "Pending",
)

@Entity(
    tableName = "chapters",
    primaryKeys = ["book_id", "chapter_index"],
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["book_id"], onDelete = ForeignKey.CASCADE)],
)
data class ChapterEntity(
    @ColumnInfo(name = "book_id") val bookId: Long,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int,
    val title: String?,
    /** Arquivo do texto estruturado do capítulo, relativo ao diretório do livro. */
    @ColumnInfo(name = "content_path") val contentPath: String,
    /** Deslocamento do início do capítulo no texto total — permite progresso global exato. */
    @ColumnInfo(name = "start_char") val startChar: Long,
    @ColumnInfo(name = "char_count") val charCount: Int,
    @ColumnInfo(name = "word_count") val wordCount: Int,
)

/** Entrada do sumário (TOC). Pode apontar para o meio de um capítulo e ter hierarquia. */
@Entity(
    tableName = "toc_entries",
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["book_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("book_id")],
)
data class TocEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: Long,
    val ordinal: Int,
    val depth: Int,
    val title: String,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int,
    @ColumnInfo(name = "char_offset") val charOffset: Int,
)

@Entity(
    tableName = "reading_positions",
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["book_id"], onDelete = ForeignKey.CASCADE)],
)
data class ReadingPositionEntity(
    @PrimaryKey @ColumnInfo(name = "book_id") val bookId: Long,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int,
    @ColumnInfo(name = "char_offset") val charOffset: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["book_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("book_id")],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: Long,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int,
    @ColumnInfo(name = "char_offset") val charOffset: Int,
    /** Trecho curto para identificar o marcador na lista. */
    val excerpt: String,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * Vocabulário pessoal: palavras e expressões (kind = Word | Expression).
 * Unicidade por (idioma, termo normalizado): consultas repetidas incrementam lookup_count.
 */
@Entity(
    tableName = "vocabulary",
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["book_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["language", "term_key"], unique = true), Index("book_id"), Index("state"), Index("created_at")],
)
data class VocabularyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val term: String,
    /** Forma normalizada (minúsculas, sem pontuação nas bordas) usada para unicidade e busca. */
    @ColumnInfo(name = "term_key") val termKey: String,
    val lemma: String?,
    val language: String,
    val kind: String,
    val translation: String,
    @ColumnInfo(name = "context_sentence") val contextSentence: String?,
    @ColumnInfo(name = "context_translation") val contextTranslation: String?,
    @ColumnInfo(name = "book_id") val bookId: Long?,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int?,
    @ColumnInfo(name = "char_offset") val charOffset: Int?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "lookup_count") val lookupCount: Int = 1,
    val state: String = "New",
    val note: String?,
    /** true quando adicionada manualmente pelo usuário (não por consulta no leitor). */
    val manual: Boolean = false,
)

/** Histórico de consultas no leitor (inclui frases e parágrafos, que não entram no vocabulário). */
@Entity(
    tableName = "lookup_history",
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["book_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("book_id"), Index("created_at")],
)
data class LookupHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: Long?,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int?,
    val language: String,
    val kind: String,
    @ColumnInfo(name = "source_text") val sourceText: String,
    val result: String?,
    @ColumnInfo(name = "from_cache") val fromCache: Boolean,
    @ColumnInfo(name = "latency_ms") val latencyMs: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * Cache de traduções. Chave = hash(modelo, operação, idioma, texto normalizado, contexto).
 * O id do modelo entra na chave: trocar de modelo não reaproveita resultados de outro.
 */
@Entity(tableName = "translation_cache", indices = [Index("last_used_at")])
data class TranslationCacheEntity(
    @PrimaryKey @ColumnInfo(name = "cache_key") val cacheKey: String,
    @ColumnInfo(name = "model_id") val modelId: String,
    val kind: String,
    val language: String,
    @ColumnInfo(name = "source_text") val sourceText: String,
    val result: String,
    /** Resultado secundário (ex.: tradução da frase obtida junto com a da palavra). */
    @ColumnInfo(name = "secondary_result") val secondaryResult: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long,
    @ColumnInfo(name = "hit_count") val hitCount: Int = 0,
)

/** Estado de agendamento (FSRS) de uma entrada de vocabulário. */
@Entity(
    tableName = "review_cards",
    foreignKeys = [ForeignKey(entity = VocabularyEntity::class, parentColumns = ["id"], childColumns = ["vocabulary_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("due_at")],
)
data class ReviewCardEntity(
    @PrimaryKey @ColumnInfo(name = "vocabulary_id") val vocabularyId: Long,
    val stability: Double,
    val difficulty: Double,
    @ColumnInfo(name = "due_at") val dueAt: Long,
    @ColumnInfo(name = "last_review_at") val lastReviewAt: Long?,
    val reps: Int,
    val lapses: Int,
    /** New | Learning | Review | Relearning */
    val phase: String,
)

@Entity(
    tableName = "review_logs",
    foreignKeys = [ForeignKey(entity = VocabularyEntity::class, parentColumns = ["id"], childColumns = ["vocabulary_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vocabulary_id"), Index("reviewed_at")],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "vocabulary_id") val vocabularyId: Long,
    /** 1 Esqueci · 2 Difícil · 3 Bom · 4 Fácil */
    val rating: Int,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: Long,
    @ColumnInfo(name = "elapsed_days") val elapsedDays: Double,
    @ColumnInfo(name = "scheduled_days") val scheduledDays: Double,
    @ColumnInfo(name = "stability_after") val stabilityAfter: Double,
    @ColumnInfo(name = "difficulty_after") val difficultyAfter: Double,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
)

/** Sessões de leitura: base das estatísticas de tempo e ritmo. */
@Entity(
    tableName = "reading_sessions",
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["book_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("book_id"), Index("started_at")],
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    /** Tempo efetivo (pausas longas e app em segundo plano descontados). */
    @ColumnInfo(name = "active_ms") val activeMs: Long,
    @ColumnInfo(name = "chars_advanced") val charsAdvanced: Long,
    val lookups: Int,
)

@Entity(tableName = "installed_models")
data class InstalledModelEntity(
    @PrimaryKey @ColumnInfo(name = "model_id") val modelId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    val sha256: String,
    /** Downloading | Verifying | Ready | Corrupt */
    val status: String,
    @ColumnInfo(name = "downloaded_bytes") val downloadedBytes: Long,
    @ColumnInfo(name = "installed_at") val installedAt: Long?,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long?,
)

/** Nível declarado pelo usuário em cada idioma (autoavaliação, não certificação). */
@Entity(tableName = "language_profiles")
data class LanguageProfileEntity(
    @PrimaryKey val language: String,
    val level: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Resultado da análise de dificuldade do livro inteiro. */
@Entity(
    tableName = "difficulty_analyses",
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["book_id"], onDelete = ForeignKey.CASCADE)],
)
data class DifficultyAnalysisEntity(
    @PrimaryKey @ColumnInfo(name = "book_id") val bookId: Long,
    @ColumnInfo(name = "level_low") val levelLow: String,
    @ColumnInfo(name = "level_high") val levelHigh: String,
    val confidence: Float,
    /** Métricas brutas serializadas em JSON (vocabulário, sintaxe, gramática, estilo). */
    @ColumnInfo(name = "metrics_json") val metricsJson: String,
    /** Observações em português geradas a partir das métricas (determinísticas). */
    @ColumnInfo(name = "findings_json") val findingsJson: String,
    @ColumnInfo(name = "analyzer_version") val analyzerVersion: Int,
    @ColumnInfo(name = "analyzed_at") val analyzedAt: Long,
)

@Entity(
    tableName = "chapter_difficulty",
    primaryKeys = ["book_id", "chapter_index"],
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["book_id"], onDelete = ForeignKey.CASCADE)],
)
data class ChapterDifficultyEntity(
    @ColumnInfo(name = "book_id") val bookId: Long,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int,
    @ColumnInfo(name = "level_low") val levelLow: String,
    @ColumnInfo(name = "level_high") val levelHigh: String,
    val score: Float,
    @ColumnInfo(name = "metrics_json") val metricsJson: String,
)
