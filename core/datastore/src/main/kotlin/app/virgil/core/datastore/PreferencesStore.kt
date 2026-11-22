package app.virgil.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.virgil.core.model.AppPreferences
import app.virgil.core.model.ReadingPreferences
import app.virgil.core.model.ThemeMode
import app.virgil.core.model.WidgetRefresh
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "virgil_prefs")

/**
 * Preferências do usuário (DataStore). Leitura reativa via [preferences]; cada escrita é
 * atômica. Valores inválidos (ex.: enum renomeado entre versões) caem no padrão em vez de quebrar.
 */
class PreferencesStore(context: Context) {
    private val store = context.applicationContext.dataStore

    private object K {
        val theme = stringPreferencesKey("theme")
        val font = stringPreferencesKey("reading_font")
        val fontSize = floatPreferencesKey("reading_font_size")
        val lineHeight = floatPreferencesKey("reading_line_height")
        val paragraphSpacing = floatPreferencesKey("reading_paragraph_spacing")
        val margin = intPreferencesKey("reading_margin")
        val align = stringPreferencesKey("reading_align")
        val hyphenate = booleanPreferencesKey("reading_hyphenate")
        val autoSave = booleanPreferencesKey("auto_save_lookups")
        val widgetRefresh = stringPreferencesKey("widget_refresh")
        val dailyReviewLimit = intPreferencesKey("daily_review_limit")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
    }

    val preferences: Flow<AppPreferences> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            val d = AppPreferences()
            val r = d.reading
            AppPreferences(
                theme = p[K.theme].toEnum(d.theme),
                reading = ReadingPreferences(
                    font = p[K.font].toEnum(r.font),
                    fontSizeSp = p[K.fontSize] ?: r.fontSizeSp,
                    lineHeight = p[K.lineHeight] ?: r.lineHeight,
                    paragraphSpacingEm = p[K.paragraphSpacing] ?: r.paragraphSpacingEm,
                    horizontalMarginDp = p[K.margin] ?: r.horizontalMarginDp,
                    align = p[K.align].toEnum(r.align),
                    hyphenate = p[K.hyphenate] ?: r.hyphenate,
                ),
                autoSaveLookups = p[K.autoSave] ?: d.autoSaveLookups,
                widgetRefresh = p[K.widgetRefresh].toEnum(d.widgetRefresh),
                dailyReviewLimit = p[K.dailyReviewLimit] ?: d.dailyReviewLimit,
                onboardingDone = p[K.onboardingDone] ?: d.onboardingDone,
            )
        }

    suspend fun setTheme(mode: ThemeMode) = store.edit { it[K.theme] = mode.name }
    suspend fun setAutoSave(enabled: Boolean) = store.edit { it[K.autoSave] = enabled }
    suspend fun setWidgetRefresh(value: WidgetRefresh) = store.edit { it[K.widgetRefresh] = value.name }
    suspend fun setDailyReviewLimit(value: Int) = store.edit { it[K.dailyReviewLimit] = value.coerceIn(5, 500) }
    suspend fun setOnboardingDone() = store.edit { it[K.onboardingDone] = true }

    suspend fun setReading(r: ReadingPreferences) = store.edit {
        it[K.font] = r.font.name
        it[K.fontSize] = r.fontSizeSp.coerceIn(12f, 36f)
        it[K.lineHeight] = r.lineHeight.coerceIn(1.1f, 2.2f)
        it[K.paragraphSpacing] = r.paragraphSpacingEm.coerceIn(0f, 2f)
        it[K.margin] = r.horizontalMarginDp.coerceIn(8, 64)
        it[K.align] = r.align.name
        it[K.hyphenate] = r.hyphenate
    }
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

