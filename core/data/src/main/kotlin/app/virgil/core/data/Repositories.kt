package app.virgil.core.data

import app.virgil.core.database.VirgilDatabase
import app.virgil.core.database.dao.ReviewItemRow
import app.virgil.core.database.entity.LanguageProfileEntity
import app.virgil.core.database.entity.ReviewCardEntity
import app.virgil.core.database.entity.ReviewLogEntity
import app.virgil.engine.srs.CardState
import app.virgil.engine.srs.Fsrs
import app.virgil.engine.srs.Phase
import app.virgil.engine.srs.Rating
import kotlinx.coroutines.flow.first
import app.virgil.core.database.entity.VocabularyEntity
import app.virgil.core.datastore.PreferencesStore
import app.virgil.core.model.AppPreferences
import app.virgil.core.model.Book
import app.virgil.core.model.CefrLevel
import app.virgil.core.model.Language
import app.virgil.core.model.LearningState
import app.virgil.core.model.SelectionKind
import app.virgil.core.model.VocabularyEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class LibraryRepository(private val db: VirgilDatabase, private val booksRoot: java.io.File) {
    fun shelf(): Flow<List<Book>> = db.books().observeShelf().map { rows -> rows.map { it.toBook(booksRoot) } }
}

data class VocabularyFilter(
    val language: Language? = null,
    val bookId: Long? = null,
    val state: LearningState? = null,
    val since: Long? = null,
    val query: String? = null,
)

class VocabularyRepository(private val db: VirgilDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    fun entries(filter: VocabularyFilter): Flow<List<VocabularyEntry>> =
        db.vocabulary().observe(
            language = filter.language?.tag,
            bookId = filter.bookId,
            state = filter.state?.name,
            since = filter.since,
            query = filter.query?.takeIf { it.isNotBlank() }?.let(::termKey),
        ).map { rows -> rows.map { it.toEntry() } }

    fun entry(id: Long): Flow<VocabularyEntry?> = db.vocabulary().observeOne(id).map { it?.toEntry() }

    suspend fun addManual(term: String, translation: String, language: Language, note: String?): Long {
        val now = clock()
        val kind = if (term.trim().contains(' ')) SelectionKind.Expression else SelectionKind.Word
        return db.vocabulary().recordLookup(
            VocabularyEntity(
                term = term.trim(), termKey = termKey(term), lemma = null, language = language.tag,
                kind = kind.name, translation = translation.trim(), contextSentence = null, contextTranslation = null,
                bookId = null, chapterIndex = null, charOffset = null, createdAt = now, updatedAt = now,
                note = note?.takeIf { it.isNotBlank() }, manual = true,
            ),
        )
    }

    /** Registra uma consulta feita no leitor (cria a entrada ou incrementa o contador). */
    suspend fun saveLookup(
        term: String,
        language: Language,
        kind: SelectionKind,
        translation: String?,
        sentence: String?,
        sentenceTranslation: String?,
        bookId: Long?,
        chapterIndex: Int?,
        charOffset: Int?,
    ): Long {
        val now = clock()
        return db.vocabulary().recordLookup(
            VocabularyEntity(
                term = term.trim(), termKey = termKey(term), lemma = null, language = language.tag, kind = kind.name,
                translation = translation?.trim().orEmpty(), contextSentence = sentence?.trim(), contextTranslation = sentenceTranslation,
                bookId = bookId, chapterIndex = chapterIndex, charOffset = charOffset, createdAt = now, updatedAt = now, note = null,
            ),
        )
    }

    suspend fun update(id: Long, translation: String, lemma: String?, note: String?) {
        val e = db.vocabulary().get(id) ?: return
        db.vocabulary().update(
            e.copy(translation = translation.trim(), lemma = lemma?.takeIf { it.isNotBlank() }, note = note?.takeIf { it.isNotBlank() }, updatedAt = clock()),
        )
    }

    suspend fun setState(id: Long, state: LearningState) = db.vocabulary().setState(id, state.name, clock())
    suspend fun delete(id: Long) = db.vocabulary().delete(id)
}

data class LanguageProfile(val language: Language, val level: CefrLevel?)

class ProfileRepository(private val db: VirgilDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    fun profiles(): Flow<List<LanguageProfile>> = db.profiles().observeAll().map { rows ->
        val byLang = rows.associate { it.language to CefrLevel.parse(it.level) }
        Language.Learnable.map { LanguageProfile(it, byLang[it.tag]) }
    }

    suspend fun setLevel(language: Language, level: CefrLevel?) {
        if (level == null) db.profiles().delete(language.tag)
        else db.profiles().upsert(LanguageProfileEntity(language.tag, level.label, clock()))
    }
}

data class ProgressSummary(
    val books: Int,
    val finished: Int,
    val readingMs: Long,
    val lookups: Int,
    val vocabulary: Int,
    val reviews: Int,
    val byLanguage: List<LanguageProgress>,
)

data class LanguageProgress(val language: Language, val words: Int, val known: Int)

/** Série diária para os gráficos: rótulos curtos e valores, do mais antigo ao mais recente. */
data class DailySeries(val labels: List<String>, val values: List<Float>)

data class BookProgress(val title: String, val progress: Float, val finished: Boolean)

class ProgressRepository(private val db: VirgilDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val days = 14

    private fun series(rows: List<app.virgil.core.database.dao.DailyReading>, scale: (Long) -> Float): DailySeries {
        val tz = java.util.TimeZone.getDefault()
        val now = clock()
        val today = (now + tz.getOffset(now)) / 86_400_000L
        val byDay = rows.associate { it.day to it.ms }
        val fmt = java.text.SimpleDateFormat("d/M", java.util.Locale.forLanguageTag("pt-BR"))
        val range = (today - days + 1)..today
        return DailySeries(
            labels = range.map { d -> if (d == today) "hoje" else fmt.format(java.util.Date(d * 86_400_000L - tz.getOffset(d * 86_400_000L))) },
            values = range.map { d -> scale(byDay[d] ?: 0L) },
        )
    }

    private fun since(): Long = ReviewRepository.startOfDay(clock()) - (days - 1) * 86_400_000L
    private fun offset(): Long = java.util.TimeZone.getDefault().getOffset(clock()).toLong()

    /** Minutos de leitura por dia nas últimas duas semanas. */
    fun readingMinutes(): Flow<DailySeries> = db.stats().observeDailyReading(since(), offset()).map { series(it) { ms -> ms / 60_000f } }

    /** Revisões por dia nas últimas duas semanas. */
    fun reviewsPerDay(): Flow<DailySeries> = db.reviews().observeDailyReviews(since(), offset()).map { series(it) { n -> n.toFloat() } }

    fun booksInProgress(): Flow<List<BookProgress>> = db.books().observeShelf().map { rows ->
        rows.filter { it.progress > 0f }.take(6).map { BookProgress(it.title, it.progress, it.finished_at != null) }
    }

    fun summary(): Flow<ProgressSummary> = combine(
        db.stats().observeBookCount(),
        db.stats().observeFinishedCount(),
        db.stats().observeTotalReadingMs(),
        db.vocabulary().observeLookupCount(),
        combine(db.vocabulary().observeCount(), db.reviews().observeReviewCount(), db.vocabulary().observeByLanguage()) { v, r, l -> Triple(v, r, l) },
    ) { books, finished, ms, lookups, (vocab, reviews, byLang) ->
        ProgressSummary(
            books = books, finished = finished, readingMs = ms, lookups = lookups, vocabulary = vocab, reviews = reviews,
            byLanguage = byLang.mapNotNull { row -> Language.fromTag(row.language)?.let { LanguageProgress(it, row.total, row.known) } },
        )
    }
}

data class ReviewItem(
    val id: Long,
    val term: String,
    val language: Language,
    val translation: String,
    val context: String?,
    val contextTranslation: String?,
    val bookTitle: String?,
    val chapterIndex: Int?,
    val card: CardState,
)

/**
 * Revisão espaçada (FSRS, ADR-0005). A sessão reúne os cartões vencidos e até [newPerDay] palavras
 * novas por dia. Só entram palavras com tradução. Palavras marcadas como conhecidas saem da fila.
 */
class ReviewRepository(
    private val db: VirgilDatabase,
    private val fsrs: Fsrs = Fsrs(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val newPerDay: Int = 15,
) {
    fun dueCount(now: Long): Flow<Int> = combine(
        db.reviews().observeDueCount(now),
        db.reviews().observeNewAvailable(),
        db.reviews().observeNewStudiedSince(startOfDay(now)),
    ) { due, available, today -> due + minOf(available, (newPerDay - today).coerceAtLeast(0)) }

    suspend fun sessionQueue(limit: Int = 100): List<ReviewItem> {
        val now = clock()
        val today = db.reviews().observeNewStudiedSince(startOfDay(now)).first()
        val due = db.reviews().dueItems(now, limit)
        val fresh = db.reviews().newItems((newPerDay - today).coerceIn(0, (limit - due.size).coerceAtLeast(0)))
        // Intercala novidades entre as revisões, para a sessão não terminar só com palavras novas.
        val out = ArrayList<ReviewItem>()
        val d = due.map { it.toItem() }.iterator()
        val n = fresh.map { it.toItem() }.iterator()
        while (d.hasNext() || n.hasNext()) {
            repeat(3) { if (d.hasNext()) out += d.next() }
            if (n.hasNext()) out += n.next()
        }
        return out
    }

    fun preview(item: ReviewItem): Map<Rating, String> = fsrs.preview(item.card, clock()).mapValues { Fsrs.formatInterval(it.value) }

    /** Registra a resposta. Devolve o item atualizado se ele deve reaparecer nesta sessão ("Esqueci"). */
    suspend fun rate(item: ReviewItem, rating: Rating, durationMs: Long?): ReviewItem? {
        val now = clock()
        val o = fsrs.review(item.card, rating, now)
        val c = o.card
        db.reviews().record(
            ReviewCardEntity(item.id, c.stability, c.difficulty, c.dueAt, c.lastReviewAt, c.reps, c.lapses, c.phase.name),
            ReviewLogEntity(vocabularyId = item.id, rating = rating.value, reviewedAt = now, elapsedDays = o.elapsedDays, scheduledDays = o.scheduledDays, stabilityAfter = c.stability, difficultyAfter = c.difficulty, durationMs = durationMs),
        )
        // Estado de aprendizagem acompanha a memória: estabilidade alta = palavra aprendida.
        val state = when {
            c.stability >= KNOWN_STABILITY_DAYS -> LearningState.Known
            else -> LearningState.Learning
        }
        db.vocabulary().setState(item.id, state.name, now)
        return if (rating == Rating.Again) item.copy(card = c) else null
    }

    suspend fun markKnown(id: Long) = db.vocabulary().setState(id, LearningState.Known.name, clock())

    fun reviewsSince(since: Long): Flow<Int> = db.reviews().observeReviewsSince(since)

    private fun ReviewItemRow.toItem() = ReviewItem(
        id = id, term = term, language = Language.fromTag(language) ?: Language.EN_GB, translation = translation,
        context = context_sentence, contextTranslation = context_translation, bookTitle = book_title, chapterIndex = chapter_index,
        card = stability?.let { s -> CardState(s, difficulty ?: 5.0, due_at ?: 0, last_review_at, reps ?: 0, lapses ?: 0, runCatching { Phase.valueOf(phase ?: "Review") }.getOrDefault(Phase.Review)) } ?: CardState(),
    )

    companion object {
        /** Estabilidade (dias) a partir da qual a palavra é considerada aprendida. */
        const val KNOWN_STABILITY_DAYS = 90.0

        fun startOfDay(now: Long): Long {
            val tz = java.util.TimeZone.getDefault()
            val offset = tz.getOffset(now)
            return (now + offset) / Fsrs.DAY_MS * Fsrs.DAY_MS - offset
        }
    }
}

class PreferencesRepository(private val store: PreferencesStore) {
    val preferences: Flow<AppPreferences> = store.preferences
    suspend fun update(block: suspend PreferencesStore.() -> Unit) = store.block()
}
