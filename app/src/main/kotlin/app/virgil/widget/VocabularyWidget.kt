package app.virgil.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.virgil.MainActivity
import app.virgil.core.database.VirgilDatabase
import app.virgil.core.database.dao.VocabularyRow
import app.virgil.core.datastore.PreferencesStore
import app.virgil.core.model.Language
import app.virgil.core.model.WidgetTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Widget "palavra do vocabulário". Lê apenas o banco local (funciona offline); a palavra muda a
 * cada período configurado (WidgetUpdateWorker). Tocar abre a ficha da palavra no app.
 */
class VocabularyWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(140.dp, 60.dp), DpSize(220.dp, 110.dp), DpSize(300.dp, 160.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = PreferencesStore(context).preferences.first()
        val seed = System.currentTimeMillis() / (prefs.widgetRefresh.minutes * 60_000)
        val row = VirgilDatabase.getInstance(context).vocabulary().pickForWidget(seed)
        provideContent { WidgetContent(row, prefs.widgetTheme, prefs.widgetShowExample) }
    }

    companion object {
        const val EXTRA_ENTRY = "virgil.entryId"
    }
}

private data class Palette(val bg: ColorProvider, val term: ColorProvider, val translation: ColorProvider, val secondary: ColorProvider)

private fun palette(theme: WidgetTheme): Palette {
    // Paper & Forest / Midnight Library
    val lightBg = Color(0xFFFBF8F1); val darkBg = Color(0xFF232B26)
    val lightTerm = Color(0xFF292D28); val darkTerm = Color(0xFFEAE7DE)
    val lightTr = Color(0xFF30483C); val darkTr = Color(0xFF9BAF91)
    val lightSec = Color(0xFF6B6A61); val darkSec = Color(0xFFAAA99F)
    fun pick(day: Color, night: Color) = when (theme) {
        WidgetTheme.System -> androidx.glance.color.ColorProvider(day = day, night = night)
        WidgetTheme.Light -> androidx.glance.color.ColorProvider(day = day, night = day)
        WidgetTheme.Dark -> androidx.glance.color.ColorProvider(day = night, night = night)
    }
    return Palette(pick(lightBg, darkBg), pick(lightTerm, darkTerm), pick(lightTr, darkTr), pick(lightSec, darkSec))
}

@Composable
internal fun WidgetContent(row: VocabularyRow?, theme: WidgetTheme, showExample: Boolean) {
    val c = palette(theme)
    val size = LocalSize.current
    val open = if (row != null) {
        actionStartActivity(Intent().setClassName("app.virgil", MainActivity::class.java.name).putExtra(VocabularyWidget.EXTRA_ENTRY, row.id).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    } else {
        actionStartActivity(Intent().setClassName("app.virgil", MainActivity::class.java.name).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    Column(
        GlanceModifier.fillMaxSize().background(c.bg).cornerRadius(16.dp).padding(horizontal = 14.dp, vertical = 10.dp).clickable(open),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row == null) {
            Text("Virgil", style = TextStyle(color = c.translation, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            Text("As palavras que você consultar nos livros aparecerão aqui.", style = TextStyle(color = c.secondary, fontSize = 12.sp), maxLines = 3)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.term, style = TextStyle(color = c.term, fontSize = if (size.width < 200.dp) 18.sp else 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif), maxLines = 1)
            if (size.width >= 200.dp) {
                Spacer(GlanceModifier.defaultWeight())
                Text((Language.fromTag(row.language)?.iso639 ?: row.language).uppercase(), style = TextStyle(color = c.secondary, fontSize = 11.sp, fontWeight = FontWeight.Medium))
            }
        }
        Text(row.translation.ifBlank { "sem tradução" }, style = TextStyle(color = c.translation, fontSize = 15.sp), maxLines = 1)
        val example = row.context_sentence
        if (showExample && example != null && size.height >= 110.dp) {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                example,
                style = TextStyle(color = c.secondary, fontSize = 12.sp, fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif),
                maxLines = if (size.height >= 160.dp) 4 else 2,
            )
        }
    }
}

class VocabularyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VocabularyWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { WidgetUpdateWorker.schedule(context) } finally { pending.finish() }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdateWorker.cancel(context)
    }
}

