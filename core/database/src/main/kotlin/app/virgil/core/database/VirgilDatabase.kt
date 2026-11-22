package app.virgil.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.virgil.core.database.dao.AnalysisDao
import app.virgil.core.database.dao.BookDao
import app.virgil.core.database.dao.ModelDao
import app.virgil.core.database.dao.ProfileDao
import app.virgil.core.database.dao.ReviewDao
import app.virgil.core.database.dao.StatsDao
import app.virgil.core.database.dao.TranslationCacheDao
import app.virgil.core.database.dao.VocabularyDao
import app.virgil.core.database.entity.BookEntity
import app.virgil.core.database.entity.BookmarkEntity
import app.virgil.core.database.entity.ChapterDifficultyEntity
import app.virgil.core.database.entity.ChapterEntity
import app.virgil.core.database.entity.DifficultyAnalysisEntity
import app.virgil.core.database.entity.InstalledModelEntity
import app.virgil.core.database.entity.LanguageProfileEntity
import app.virgil.core.database.entity.LookupHistoryEntity
import app.virgil.core.database.entity.ReadingPositionEntity
import app.virgil.core.database.entity.ReadingSessionEntity
import app.virgil.core.database.entity.ReviewCardEntity
import app.virgil.core.database.entity.ReviewLogEntity
import app.virgil.core.database.entity.TocEntryEntity
import app.virgil.core.database.entity.TranslationCacheEntity
import app.virgil.core.database.entity.VocabularyEntity

@Database(
    version = 1,
    exportSchema = true,
    entities = [
        BookEntity::class, ChapterEntity::class, TocEntryEntity::class, ReadingPositionEntity::class,
        BookmarkEntity::class, VocabularyEntity::class, LookupHistoryEntity::class, TranslationCacheEntity::class,
        ReviewCardEntity::class, ReviewLogEntity::class, ReadingSessionEntity::class, InstalledModelEntity::class,
        LanguageProfileEntity::class, DifficultyAnalysisEntity::class, ChapterDifficultyEntity::class,
    ],
    // Novas versões: declarar AutoMigration(from, to) aqui quando possível, ou Migration manual em Migrations.kt.
    autoMigrations = [],
)
abstract class VirgilDatabase : RoomDatabase() {
    abstract fun books(): BookDao
    abstract fun vocabulary(): VocabularyDao
    abstract fun reviews(): ReviewDao
    abstract fun stats(): StatsDao
    abstract fun translationCache(): TranslationCacheDao
    abstract fun models(): ModelDao
    abstract fun profiles(): ProfileDao
    abstract fun analysis(): AnalysisDao

    companion object {
        const val NAME = "virgil.db"

        fun build(context: Context): VirgilDatabase =
            Room.databaseBuilder(context.applicationContext, VirgilDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                // Nunca destruir dados do usuário silenciosamente: sem fallbackToDestructiveMigration.
                .build()
    }
}
