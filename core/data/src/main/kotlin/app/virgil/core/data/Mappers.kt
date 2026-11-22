package app.virgil.core.data

import app.virgil.core.database.dao.ShelfRow
import app.virgil.core.database.dao.VocabularyRow
import app.virgil.core.model.AnalysisState
import app.virgil.core.model.Book
import app.virgil.core.model.BookFormat
import app.virgil.core.model.CefrLevel
import app.virgil.core.model.CefrRange
import app.virgil.core.model.Language
import app.virgil.core.model.LearningState
import app.virgil.core.model.SelectionKind
import app.virgil.core.model.VocabularyEntry

internal fun ShelfRow.toBook(): Book {
    val low = CefrLevel.parse(level_low)
    val high = CefrLevel.parse(level_high)
    return Book(
        id = id,
        title = title,
        author = author,
        language = Language.fromTag(language),
        languageVariant = language_variant,
        format = runCatching { BookFormat.valueOf(format) }.getOrDefault(BookFormat.EPUB),
        coverPath = cover_path,
        chapterCount = chapter_count,
        totalChars = total_chars,
        addedAt = added_at,
        lastOpenedAt = last_opened_at,
        progress = progress,
        finishedAt = finished_at,
        difficulty = if (low != null && high != null && low <= high) CefrRange(low, high, confidence ?: 0f) else null,
        analysisState = runCatching { AnalysisState.valueOf(analysis_state) }.getOrDefault(AnalysisState.Pending),
    )
}

internal fun VocabularyRow.toEntry(): VocabularyEntry = VocabularyEntry(
    id = id,
    term = term,
    lemma = lemma,
    language = Language.fromTag(language) ?: Language.EN_GB,
    kind = runCatching { SelectionKind.valueOf(kind) }.getOrDefault(SelectionKind.Word),
    translation = translation,
    contextSentence = context_sentence,
    contextTranslation = context_translation,
    bookId = book_id,
    bookTitle = book_title,
    chapterIndex = chapter_index,
    createdAt = created_at,
    lookupCount = lookup_count,
    state = runCatching { LearningState.valueOf(state) }.getOrDefault(LearningState.New),
    note = note,
)

/** Chave de unicidade de um termo: minúsculas, apóstrofos tipográficos unificados, sem pontuação nas bordas. */
fun termKey(term: String): String =
    term.trim()
        .replace('’', '\'')
        .trim { !it.isLetterOrDigit() && it != '\'' }
        .lowercase()
