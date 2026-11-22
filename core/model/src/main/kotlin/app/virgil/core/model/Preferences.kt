package app.virgil.core.model

enum class ThemeMode { System, Light, Dark }

enum class ReadingFont(val labelPt: String) {
    Literata("Literata"),
    SourceSerif("Source Serif"),
    EbGaramond("EB Garamond"),
    Inter("Inter (sem serifa)"),
}

enum class TextAlign { Start, Justify }

/** Tipografia do leitor. Valores em sp/dp para independência de densidade. */
data class ReadingPreferences(
    val font: ReadingFont = ReadingFont.Literata,
    val fontSizeSp: Float = 19f,
    val lineHeight: Float = 1.55f,
    val paragraphSpacingEm: Float = 0.6f,
    val horizontalMarginDp: Int = 24,
    val align: TextAlign = TextAlign.Start,
    val hyphenate: Boolean = true,
)

enum class WidgetRefresh(val minutes: Long, val labelPt: String) {
    Hourly(60, "A cada hora"),
    Every3h(180, "A cada 3 horas"),
    Every6h(360, "A cada 6 horas"),
    Daily(1440, "Uma vez por dia"),
}

enum class WidgetTheme(val labelPt: String) { System("Seguir o sistema"), Light("Claro"), Dark("Escuro") }

data class AppPreferences(
    val theme: ThemeMode = ThemeMode.System,
    val reading: ReadingPreferences = ReadingPreferences(),
    val autoSaveLookups: Boolean = true,
    val widgetRefresh: WidgetRefresh = WidgetRefresh.Every3h,
    val widgetTheme: WidgetTheme = WidgetTheme.System,
    val widgetShowExample: Boolean = true,
    val dailyReviewLimit: Int = 40,
    val onboardingDone: Boolean = false,
)
