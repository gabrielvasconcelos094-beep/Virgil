package app.virgil.feature.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.virgil.core.data.ReviewItem
import app.virgil.core.data.ReviewRepository
import app.virgil.core.designsystem.component.EmptyState
import app.virgil.core.designsystem.component.ScreenHeader
import app.virgil.core.designsystem.component.ThinProgress
import app.virgil.core.designsystem.theme.ForeignTermStyle
import app.virgil.core.designsystem.theme.Spacing
import app.virgil.core.designsystem.theme.Virgil
import app.virgil.core.designsystem.theme.VirgilFonts
import app.virgil.core.designsystem.theme.VirgilShapes
import app.virgil.engine.srs.Rating
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ReviewUi {
    data object Idle : ReviewUi
    data class Session(
        val current: ReviewItem,
        val revealed: Boolean,
        val position: Int,
        val total: Int,
        val intervals: Map<Rating, String>,
        val shownAt: Long,
    ) : ReviewUi
    data class Finished(val reviewed: Int) : ReviewUi
}

class ReviewViewModel(private val repo: ReviewRepository, private val clock: () -> Long = System::currentTimeMillis) : ViewModel() {
    private val _ui = MutableStateFlow<ReviewUi>(ReviewUi.Idle)
    val ui: StateFlow<ReviewUi> = _ui.asStateFlow()
    private val queue = ArrayDeque<ReviewItem>()
    private var total = 0
    private var done = 0

    fun start() = viewModelScope.launch {
        queue.clear(); queue.addAll(repo.sessionQueue())
        total = queue.size; done = 0
        next()
    }

    fun reveal() = _ui.update { (it as? ReviewUi.Session)?.copy(revealed = true) ?: it }

    fun rate(rating: Rating) {
        val s = _ui.value as? ReviewUi.Session ?: return
        viewModelScope.launch {
            val again = repo.rate(s.current, rating, clock() - s.shownAt)
            done++
            // "Esqueci": a palavra volta algumas posições adiante na mesma sessão.
            if (again != null) { queue.add(minOf(3, queue.size), again); total++ }
            next()
        }
    }

    fun markKnown() {
        val s = _ui.value as? ReviewUi.Session ?: return
        viewModelScope.launch { repo.markKnown(s.current.id); done++; next() }
    }

    fun close() { queue.clear(); _ui.value = ReviewUi.Idle }

    private fun next() {
        val item = queue.removeFirstOrNull()
        _ui.value = if (item == null) {
            if (total == 0) ReviewUi.Idle else ReviewUi.Finished(done)
        } else ReviewUi.Session(item, false, done + 1, total, repo.preview(item), clock())
    }
}

@Composable
fun ReviewRoute(repo: ReviewRepository) {
    val vm: ReviewViewModel = viewModel(factory = viewModelFactory { initializer { ReviewViewModel(repo) } })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val now = remember(ui) { System.currentTimeMillis() }
    val due by remember(repo, ui) { repo.dueCount(now) }.collectAsStateWithLifecycle(initialValue = 0)
    val today by remember(repo, ui) { repo.reviewsSince(ReviewRepository.startOfDay(now)) }.collectAsStateWithLifecycle(initialValue = 0)
    ReviewScreen(ui, due, today, onStart = { vm.start() }, onReveal = vm::reveal, onRate = vm::rate, onKnown = vm::markKnown, onClose = vm::close)
}

@Composable
fun ReviewScreen(
    ui: ReviewUi,
    due: Int,
    reviewedToday: Int,
    onStart: () -> Unit,
    onReveal: () -> Unit,
    onRate: (Rating) -> Unit,
    onKnown: () -> Unit,
    onClose: () -> Unit,
) {
    when (ui) {
        ReviewUi.Idle -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader(
                title = "Revisar",
                subtitle = when (due) { 0 -> "Nenhuma palavra para hoje"; 1 -> "1 palavra para revisar"; else -> "$due palavras para revisar" },
            )
            if (due > 0) {
                Column(Modifier.padding(horizontal = Spacing.screen)) {
                    Text(
                        "As palavras voltam no momento em que estão prestes a ser esquecidas, sempre com a frase do livro em que você as encontrou.",
                        style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textSecondary,
                    )
                    Spacer(Modifier.height(Spacing.xl))
                    Button(onClick = onStart, colors = ButtonDefaults.buttonColors(containerColor = Virgil.colors.primary, contentColor = Virgil.colors.onPrimary)) {
                        Text("Começar revisão")
                    }
                    if (reviewedToday > 0) {
                        Spacer(Modifier.height(Spacing.md))
                        Text("$reviewedToday revisões hoje", style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary)
                    }
                }
            } else {
                EmptyState(
                    icon = Icons.Outlined.Spa,
                    title = if (reviewedToday > 0) "Revisão em dia" else "Nada pendente",
                    message = if (reviewedToday > 0) "Você revisou $reviewedToday ${if (reviewedToday == 1) "palavra" else "palavras"} hoje. As próximas voltam quando for a hora."
                    else "As palavras que você consultar nos livros voltarão aqui em intervalos espaçados, sempre com a frase em que apareceram.",
                )
            }
        }
        is ReviewUi.Finished -> Column(Modifier.fillMaxSize()) {
            ScreenHeader("Revisar")
            EmptyState(
                icon = Icons.Outlined.Spa, title = "Sessão concluída",
                message = "${ui.reviewed} ${if (ui.reviewed == 1) "revisão" else "revisões"}. Bom momento para voltar ao livro.",
                action = { OutlinedButton(onClick = onClose) { Text("Fechar") } },
            )
        }
        is ReviewUi.Session -> ReviewCard(ui, onReveal, onRate, onKnown, onClose)
    }
}

@Composable
private fun ReviewCard(s: ReviewUi.Session, onReveal: () -> Unit, onRate: (Rating) -> Unit, onKnown: () -> Unit, onClose: () -> Unit) {
    val item = s.current
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.screen)) {
        Row(Modifier.fillMaxWidth().padding(top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text("${s.position} de ${s.total}", style = MaterialTheme.typography.labelLarge, color = Virgil.colors.textSecondary, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Encerrar revisão") }
        }
        ThinProgress((s.position - 1).toFloat() / s.total.coerceAtLeast(1))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = Spacing.xl), verticalArrangement = Arrangement.Center) {
            Text(item.language.displayNamePt.uppercase(), style = MaterialTheme.typography.labelSmall, color = Virgil.colors.secondaryInk)
            Spacer(Modifier.height(Spacing.sm))
            Text(item.term, style = ForeignTermStyle.copy(fontSize = ForeignTermStyle.fontSize * 1.4f, lineHeight = ForeignTermStyle.lineHeight * 1.4f), color = Virgil.colors.textPrimary)
            item.context?.let { ctx ->
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    highlighted(ctx, item.term, Virgil.colors.textPrimary),
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = VirgilFonts.Literata, fontStyle = FontStyle.Italic),
                    color = Virgil.colors.textSecondary,
                )
            }
            AnimatedVisibility(s.revealed, enter = fadeIn(tween(160))) {
                Column(Modifier.padding(top = Spacing.xl)) {
                    Box(Modifier.fillMaxWidth().border(1.dp, Virgil.colors.divider, VirgilShapes.medium).padding(Spacing.lg)) {
                        Column {
                            Text(item.translation, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = VirgilFonts.Inter, fontWeight = FontWeight.Medium), color = Virgil.colors.primary)
                            item.contextTranslation?.let {
                                Spacer(Modifier.height(Spacing.sm))
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textSecondary)
                            }
                            val origin = listOfNotNull(item.bookTitle, item.chapterIndex?.let { "capítulo ${it + 1}" }).joinToString(" · ")
                            if (origin.isNotEmpty()) {
                                Spacer(Modifier.height(Spacing.sm))
                                Text(origin, style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary)
                            }
                        }
                    }
                }
            }
        }
        if (!s.revealed) {
            Button(
                onClick = onReveal, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
                colors = ButtonDefaults.buttonColors(containerColor = Virgil.colors.primary, contentColor = Virgil.colors.onPrimary),
            ) { Text("Mostrar tradução") }
            TextButton(onClick = onKnown, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = Spacing.lg)) { Text("Já conheço esta palavra") }
        } else {
            Row(Modifier.fillMaxWidth().padding(bottom = Spacing.xl), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Rating.entries.forEach { r ->
                    OutlinedButton(onClick = { onRate(r) }, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 10.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(r.labelPt, style = MaterialTheme.typography.labelLarge, color = if (r == Rating.Again) Virgil.colors.accentInk else Virgil.colors.primary, textAlign = TextAlign.Center)
                            Text(s.intervals[r].orEmpty(), style = MaterialTheme.typography.labelSmall, color = Virgil.colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

private fun highlighted(sentence: String, term: String, color: androidx.compose.ui.graphics.Color) = buildAnnotatedString {
    val i = sentence.indexOf(term, ignoreCase = true)
    if (i < 0) { append(sentence); return@buildAnnotatedString }
    append(sentence.substring(0, i))
    withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Normal)) { append(sentence.substring(i, i + term.length)) }
    append(sentence.substring(i + term.length))
}
