package app.virgil.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.virgil.core.data.BookProgress
import app.virgil.core.data.DailySeries
import app.virgil.core.data.ProgressRepository
import app.virgil.core.data.ProgressSummary
import app.virgil.core.designsystem.component.BarChart
import app.virgil.core.designsystem.component.ScreenHeader
import app.virgil.core.designsystem.component.SectionLabel
import app.virgil.core.designsystem.component.ThinProgress
import app.virgil.core.designsystem.component.VirgilDivider
import app.virgil.core.designsystem.theme.Spacing
import app.virgil.core.designsystem.theme.Virgil
import app.virgil.core.designsystem.theme.VirgilFonts
import kotlin.math.roundToInt

@Composable
fun ProgressRoute(repo: ProgressRepository) {
    val summary by remember(repo) { repo.summary() }.collectAsStateWithLifecycle(initialValue = null)
    val reading by remember(repo) { repo.readingMinutes() }.collectAsStateWithLifecycle(initialValue = null)
    val reviews by remember(repo) { repo.reviewsPerDay() }.collectAsStateWithLifecycle(initialValue = null)
    val books by remember(repo) { repo.booksInProgress() }.collectAsStateWithLifecycle(initialValue = emptyList())
    ProgressScreen(summary, reading, reviews, books)
}

@Composable
fun ProgressScreen(summary: ProgressSummary?, reading: DailySeries?, reviews: DailySeries?, books: List<BookProgress>) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Spacing.xxl)) {
        ScreenHeader(title = "Progresso", subtitle = "Seus hábitos de leitura e o vocabulário que cresce com eles")
        val s = summary ?: return@Column
        SectionLabel("Leitura")
        StatRow("Livros" to s.books.toString(), "Concluídos" to s.finished.toString(), "Tempo de leitura" to formatDuration(s.readingMs))
        reading?.let { r ->
            val total = r.values.sum()
            Spacer(Modifier.height(Spacing.lg))
            BarChart(
                values = r.values, labels = r.labels, format = { "${it.roundToInt()} min" },
                summary = "Minutos de leitura nos últimos 14 dias: ${total.roundToInt()} min no total",
                modifier = Modifier.padding(horizontal = Spacing.screen),
            )
        }
        if (books.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.lg))
            books.forEach { b ->
                Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.xs)) {
                    Row {
                        Text(b.title, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = VirgilFonts.Literata), color = Virgil.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(if (b.finished) "lido" else "${(b.progress * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary)
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    ThinProgress(b.progress)
                }
            }
        }

        VirgilDivider(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.lg))
        SectionLabel("Vocabulário")
        StatRow("Consultas" to s.lookups.toString(), "Palavras" to s.vocabulary.toString(), "Aprendidas" to s.byLanguage.sumOf { it.known }.toString())
        reviews?.let { r ->
            Spacer(Modifier.height(Spacing.lg))
            BarChart(
                values = r.values, labels = r.labels, format = { "${it.roundToInt()} revisões" },
                summary = "Revisões nos últimos 14 dias: ${r.values.sum().roundToInt()} (${s.reviews} no total)",
                modifier = Modifier.padding(horizontal = Spacing.screen),
            )
        }
        if (s.byLanguage.isNotEmpty()) {
            VirgilDivider(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.lg))
            SectionLabel("Por idioma")
            s.byLanguage.sortedByDescending { it.words }.forEach { lp ->
                Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
                    Row {
                        Text(lp.language.displayNamePt, style = MaterialTheme.typography.titleSmall, color = Virgil.colors.textPrimary, modifier = Modifier.weight(1f))
                        Text("${lp.known} de ${lp.words} aprendidas", style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary)
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    ThinProgress(if (lp.words == 0) 0f else lp.known.toFloat() / lp.words)
                }
            }
        }
        Text(
            "Uma palavra conta como aprendida quando você a marca como conhecida ou quando a revisão indica que ela ficará na memória por pelo menos três meses.",
            style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.lg),
        )
    }
}

@Composable
private fun StatRow(vararg stats: Pair<String, String>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        stats.forEach { (label, value) ->
            Column(Modifier.weight(1f)) {
                Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = VirgilFonts.Literata), color = Virgil.colors.primary)
                Text(label, style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary)
            }
        }
    }
}

internal fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "0 min"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}
