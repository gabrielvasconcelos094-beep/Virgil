package app.virgil.core.model

/** Granularidade de uma consulta. */
enum class SelectionKind { Word, Expression, Sentence, Paragraph }

/** Estado de aprendizagem de uma entrada de vocabulário. */
enum class LearningState(val labelPt: String) {
    New("Nova"),
    Learning("Aprendendo"),
    Known("Conhecida"),
    Ignored("Ignorada"),
}

data class VocabularyEntry(
    val id: Long,
    val term: String,
    val lemma: String?,
    val language: Language,
    val kind: SelectionKind,
    val translation: String,
    val contextSentence: String?,
    val contextTranslation: String?,
    val bookId: Long?,
    val bookTitle: String?,
    val chapterIndex: Int?,
    val createdAt: Long,
    val lookupCount: Int,
    val state: LearningState,
    val note: String?,
)
