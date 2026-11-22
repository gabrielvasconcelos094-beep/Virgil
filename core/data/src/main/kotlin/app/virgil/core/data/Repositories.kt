package app.virgil.core.data

import app.virgil.core.database.VirgilDatabase
import app.virgil.core.database.entity.LanguageProfileEntity
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

class LibraryRepository(private val db: VirgilDatabase) {
    fun shelf(): Flow<List<Book>> = db.books().observeShelf().map { rows -> rows.map { it.toBook() } }
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

class ProgressRepository(private val db: VirgilDatabase) {
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

class ReviewRepository(private val db: VirgilDatabase) {
    fun dueCount(now: Long): Flow<Int> = db.reviews().observeDueCount(now)
}

class PreferencesRepository(private val store: PreferencesStore) {
    val preferences: Flow<AppPreferences> = store.preferences
    suspend fun update(block: suspend PreferencesStore.() -> Unit) = store.block()
}
