package app.virgil.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.virgil.core.designsystem.component.DifficultyStrip
import app.virgil.core.designsystem.component.SectionLabel
import app.virgil.core.designsystem.component.VirgilDivider
import app.virgil.core.designsystem.theme.Spacing
import app.virgil.core.designsystem.theme.Virgil
import app.virgil.core.designsystem.theme.VirgilFonts
import app.virgil.core.model.AnalysisState
import app.virgil.core.model.ReadingFit
import app.virgil.engine.analysis.BookDetails
import app.virgil.engine.analysis.BookInsights

@Composable
fun BookDetailsRoute(bookId: Long, insights: BookInsights, onBack: () -> Unit, onRead: () -> Unit, onSetLevel: () -> Unit) {
    val details by remember(bookId) { insights.details(bookId) }.collectAsStateWithLifecycle(initialValue = null)
    details?.let { BookDetailsScreen(it, onBack, onRead, onSetLevel) }
}

@Composable
fun BookDetailsScreen(d: BookDetails, onBack: () -> Unit, onRead: () -> Unit, onSetLevel: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Spacing.xxl)) {
        Row(Modifier.padding(start = Spacing.xs, top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") }
        }
        Column(Modifier.padding(horizontal = Spacing.screen)) {
            Text(d.title, style = MaterialTheme.typography.headlineMedium, color = Virgil.colors.textPrimary)
            d.author?.let { Text(it, style = MaterialTheme.typography.titleMedium.copy(fontFamily = VirgilFonts.Literata), color = Virgil.colors.textSecondary) }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                listOfNotNull(d.variant ?: d.language?.displayNamePt, "${d.chapterCount} capítulos", if (d.words > 0) "%,d palavras".format(d.words).replace(',', '.') else null, d.publishedDate?.take(4)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary,
            )
            Spacer(Modifier.height(Spacing.lg))
            Button(onClick = onRead, colors = ButtonDefaults.buttonColors(containerColor = Virgil.colors.primary, contentColor = Virgil.colors.onPrimary)) {
                Text(if (d.progress > 0f) "Continuar leitura (${(d.progress * 100).toInt()}%)" else "Começar a ler")
            }
        }

        VirgilDivider(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xl))
        SectionLabel("Dificuldade estimada")
        when {
            d.range != null -> {
                Row(Modifier.padding(horizontal = Spacing.screen), verticalAlignment = Alignment.Bottom) {
                    Text(d.range!!.label, style = MaterialTheme.typography.displaySmall, color = Virgil.colors.primary)
                    Spacer(Modifier.width(Spacing.md))
                    Text("faixa estimada · mais provável: ${app.virgil.engine.analysis.DifficultyAnalyzer.levelOf(d.metrics?.scores?.combined ?: 3.0).label}", style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
                }
                d.metrics?.summary?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textPrimary, modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs))
                }
                FitLine(d, onSetLevel)
                Text(
                    "Estimativa automática calculada sobre o texto completo do livro (${d.metrics?.chaptersScored ?: 0} de ${d.metrics?.chapters ?: d.chapterCount} capítulos com texto suficiente), " +
                        "por um modelo calibrado em cerca de 2 600 textos de leitura com nível conhecido. Em textos de validação, faixas como esta continham o nível real em " +
                        "${(d.range!!.confidence * 100).toInt()}% dos casos. Não é uma avaliação oficial do Quadro Europeu Comum de Referência.",
                    style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                )
            }
            d.state == AnalysisState.Pending || d.state == AnalysisState.Running -> Column(Modifier.padding(horizontal = Spacing.screen)) {
                Text("Analisando o texto… você já pode ler enquanto isso.", style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textSecondary)
                Spacer(Modifier.height(Spacing.sm))
                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Virgil.colors.primary, trackColor = Virgil.colors.divider)
            }
            else -> Text(
                "Não foi possível estimar a dificuldade: ${d.error ?: "motivo desconhecido"}.",
                style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textSecondary, modifier = Modifier.padding(horizontal = Spacing.screen),
            )
        }

        if (d.findings.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            SectionLabel("Características")
            d.findings.forEach { f ->
                Row(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs)) {
                    Text("—", color = Virgil.colors.secondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(f, style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textPrimary)
                }
            }
        }

        d.metrics?.let { m ->
            Spacer(Modifier.height(Spacing.md))
            SectionLabel("Indicadores")
            Indicator("Léxico", m.scores.vocabulary)
            Indicator("Sintaxe", m.scores.syntax)
            Indicator("Gramática", m.scores.grammar)
            Indicator("Expressões e estilo", m.scores.style)
            Text(
                "Cada barra mostra o nível que o modelo estimaria se só aquele aspecto se afastasse da média. " +
                    "Frases médias de ${"%.0f".format(m.meanSentenceLength)} palavras · ${"%.1f".format(m.above8kPercent).replace('.', ',')}% de palavras além das 8 mil mais comuns · " +
                    "diversidade lexical (MTLD) ${"%.0f".format(m.mtld)} · ${"%.1f".format(m.idiomsPer1000 + m.similesPer1000).replace('.', ',')} expressões ou comparações a cada mil palavras.",
                style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs),
            )
        }

        if (d.chapters.any { it.range != null }) {
            Spacer(Modifier.height(Spacing.md))
            SectionLabel("Por capítulo")
            DifficultyStrip(
                d.chapters.map { c -> c.range?.let { r -> app.virgil.core.model.CefrLevel.entries[(r.low.ordinal + r.high.ordinal + 1) / 2] } },
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm), height = 36,
            )
            var expanded by remember { mutableStateOf(false) }
            val visible = if (expanded) d.chapters else d.chapters.take(8)
            visible.forEach { c ->
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${c.index + 1}", style = MaterialTheme.typography.labelMedium, color = Virgil.colors.textSecondary, modifier = Modifier.width(28.dp))
                    Text(c.title ?: "Capítulo ${c.index + 1}", style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(c.range?.label ?: "—", style = MaterialTheme.typography.labelLarge, color = Virgil.colors.secondaryInk)
                }
            }
            if (d.chapters.size > 8) TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(horizontal = Spacing.sm)) {
                Text(if (expanded) "Mostrar menos" else "Ver todos os ${d.chapters.size} capítulos")
            }
        }

        d.description?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(Spacing.md))
            SectionLabel("Descrição")
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textSecondary, modifier = Modifier.padding(horizontal = Spacing.screen))
        }
    }
}

@Composable
private fun FitLine(d: BookDetails, onSetLevel: () -> Unit) {
    val lang = d.language?.displayNamePt?.substringBefore(" (")?.lowercase() ?: "idioma"
    Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
        val fit = d.fit
        val reader = d.readerLevel
        if (fit == null || reader == null) {
            TextButton(onClick = onSetLevel, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("Informe seu nível de $lang para comparar") }
        } else {
            Text("Seu $lang: ${reader.label} · livro: ${d.range?.label}", style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textPrimary)
            Text(
                when (fit) {
                    ReadingFit.Comfortable -> "${fit.labelPt}: o texto está no seu nível ou abaixo dele."
                    ReadingFit.ModerateChallenge -> "${fit.labelPt}: um pouco acima do seu nível; espere consultas ocasionais."
                    ReadingFit.HighChallenge -> "${fit.labelPt}: bem acima do seu nível; espere consultas frequentes. O Virgil está aqui exatamente para isso."
                    ReadingFit.FarAbove -> "${fit.labelPt}: a leitura será lenta e exigirá muitas consultas — possível, se for o livro que você quer ler."
                },
                style = MaterialTheme.typography.bodySmall, color = Virgil.colors.accentInk,
            )
        }
    }
}

@Composable
private fun Indicator(label: String, score: Double) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textPrimary, modifier = Modifier.width(120.dp))
        LinearProgressIndicator(
            progress = { ((score - 1) / 5).toFloat().coerceIn(0.02f, 1f) },
            modifier = Modifier.weight(1f).height(3.dp), color = Virgil.colors.secondary, trackColor = Virgil.colors.divider, drawStopIndicator = {},
        )
        Text(app.virgil.engine.analysis.DifficultyAnalyzer.levelOf(score).label, style = MaterialTheme.typography.labelMedium, color = Virgil.colors.textSecondary, modifier = Modifier.width(24.dp))
    }
}
