package app.virgil.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.virgil.core.data.PreferencesRepository
import app.virgil.core.data.VocabularyRepository
import app.virgil.core.designsystem.component.VirgilDivider
import app.virgil.core.designsystem.theme.Spacing
import app.virgil.core.designsystem.theme.Virgil
import app.virgil.core.designsystem.theme.VirgilFonts
import app.virgil.core.model.Language
import app.virgil.engine.books.BookStore
import app.virgil.engine.speech.IpaSource
import app.virgil.engine.speech.SpeechEngine
import app.virgil.engine.translation.Translator
import kotlin.math.abs

/** Velocidades oferecidas no modo de estudo (a escolha vale também para o cartão de consulta). */
private val Rates = listOf(0.6f, 0.75f, 0.9f, 1.0f, 1.2f)

@Composable
fun StudyRoute(
    bookId: Long,
    mode: StudyMode,
    startChapter: Int?,
    startOffset: Int?,
    store: BookStore,
    vocabulary: VocabularyRepository,
    preferences: PreferencesRepository,
    translator: Translator?,
    speech: SpeechEngine?,
    ipa: IpaSource?,
    onClose: () -> Unit,
    onOpenTranslationSettings: () -> Unit,
) {
    val vm: StudyViewModel = viewModel(
        key = "study-$bookId-$mode-$startChapter-$startOffset",
        factory = viewModelFactory { initializer { StudyViewModel(bookId, mode, startChapter, startOffset, store, vocabulary, preferences, translator, speech, ipa) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val speaking by vm.speaking.collectAsStateWithLifecycle()
    val leave = { vm.leave(); onClose() }
    BackHandler { if (state.lookup != null) vm.closeLookup() else leave() }
    StudyScreen(
        state = state, rate = prefs?.speech?.rate ?: 0.9f, fontSizeSp = prefs?.reading?.fontSizeSp ?: 19f,
        speaking = speaking, vm = vm, onClose = leave, onOpenTranslationSettings = onOpenTranslationSettings,
    )
}

@Composable
internal fun StudyScreen(
    state: StudyUiState,
    rate: Float,
    fontSizeSp: Float,
    speaking: String?,
    vm: StudyViewModel,
    onClose: () -> Unit,
    onOpenTranslationSettings: () -> Unit,
) {
    val unit = state.unit
    Box(Modifier.fillMaxSize().background(Virgil.colors.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Cabeçalho: sair, modo, posição
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Voltar ao leitor") }
                Column(Modifier.weight(1f)) {
                    Text(if (state.mode == StudyMode.Sentence) "Frase a frase" else "Parágrafo a parágrafo", style = MaterialTheme.typography.titleSmall, color = Virgil.colors.textPrimary)
                    state.chapterTitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                if (state.units.isNotEmpty()) {
                    Text(
                        "${state.index + 1} / ${state.units.size}",
                        style = MaterialTheme.typography.labelLarge, color = Virgil.colors.textSecondary,
                        modifier = Modifier.padding(end = Spacing.md),
                    )
                }
            }
            VirgilDivider()

            when {
                state.error != null -> Text(state.error, color = Virgil.colors.textSecondary, modifier = Modifier.padding(Spacing.screen))
                state.loading || unit == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (state.loading) CircularProgressIndicator(color = Virgil.colors.secondary, strokeWidth = 2.dp)
                    else TranslationText(state.translation, 15f, onOpenTranslationSettings)
                }
                else -> {
                    Column(
                        Modifier.weight(1f).fillMaxWidth()
                            .swipeUnits { if (it < 0) vm.next() else vm.previous() }
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.screen, vertical = Spacing.xl),
                    ) {
                        // Original em destaque; palavras tocáveis
                        val size = if (state.mode == StudyMode.Sentence) fontSizeSp * 1.25f else fontSizeSp * 1.05f
                        TappableText(
                            text = unit.text,
                            selection = state.lookup?.selection?.takeIf { it.blockIndex == state.index }?.let { it.start until it.end },
                            style = TextStyle(
                                fontFamily = VirgilFonts.Literata, fontSize = size.sp, lineHeight = (size * 1.5f).sp,
                                color = Virgil.colors.textReading, hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph,
                            ),
                            onTap = vm::tapWord,
                        )
                        Spacer(Modifier.height(Spacing.lg))
                        // Tradução menor, abaixo; pode ser ocultada
                        if (state.translationVisible) {
                            TranslationText(state.translation, (size * 0.72f).coerceAtLeast(14f), onOpenTranslationSettings)
                        } else {
                            TextButton(onClick = vm::toggleTranslation, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                Text("Mostrar tradução", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            // Controles: anterior · velocidade · ouvir · tradução · próxima
            VirgilDivider()
            val unitPlaying = vm.isUnitSpeaking(speaking)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = vm::previous, enabled = state.hasPrevious) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, if (state.mode == StudyMode.Sentence) "Frase anterior" else "Parágrafo anterior")
                }
                TextButton(onClick = {
                    val i = Rates.indexOfFirst { abs(it - rate) < 0.01f }
                    vm.setRate(Rates[(i + 1).mod(Rates.size)])
                }) { Text("${"%.2f".format(rate).trimEnd('0').trimEnd('.', ',')}×", style = MaterialTheme.typography.labelLarge) }
                if (vm.hasSpeech) {
                    FilledTonalIconButton(onClick = vm::speakUnit, modifier = Modifier.size(52.dp), enabled = unit != null) {
                        Icon(if (unitPlaying) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.VolumeUp, if (unitPlaying) "Parar" else "Ouvir")
                    }
                }
                IconButton(onClick = vm::toggleTranslation) {
                    Icon(
                        if (state.translationVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        if (state.translationVisible) "Ocultar tradução" else "Mostrar tradução",
                    )
                }
                IconButton(onClick = vm::next, enabled = state.hasNext) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, if (state.mode == StudyMode.Sentence) "Próxima frase" else "Próximo parágrafo")
                }
            }
        }

        AnimatedVisibility(
            state.lookup != null,
            enter = slideInVertically(tween(180)) { it / 2 } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(140)) { it / 2 } + fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            state.lookup?.let { l ->
                val voiceReady by vm.voiceReady.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val controls = if (!vm.hasSpeech) null else SpeechControls(
                    playing = speaking == l.speechId,
                    availability = remember(voiceReady, state.language) { vm.voiceAvailability() },
                    languageName = (state.language ?: Language.EN_GB).displayNamePt.lowercase().substringBefore(" ("),
                    onPlay = vm::speakWord,
                    onInstallVoice = { openVoiceInstall(context) },
                )
                LookupCard(l, onKind = {}, onSave = vm::saveWord, onClose = vm::closeLookup, onOpenSettings = onOpenTranslationSettings, speech = controls, showKinds = false)
            }
        }
    }
}

@Composable
private fun TranslationText(t: TranslationUi, sizeSp: Float, onOpenSettings: () -> Unit) {
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = sizeSp.sp, lineHeight = (sizeSp * 1.45f).sp)
    when (t) {
        TranslationUi.NoModel -> Column {
            Text("Sem modelo de tradução instalado: a frase original, a voz e o IPA continuam disponíveis.", style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textSecondary)
            TextButton(onClick = onOpenSettings, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("Ver opções de tradução") }
        }
        TranslationUi.Working, TranslationUi.LoadingModel -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = Virgil.colors.secondary)
            Spacer(Modifier.size(Spacing.sm))
            Text(
                if (t == TranslationUi.LoadingModel) "preparando o modelo de tradução…" else "traduzindo neste aparelho…",
                style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary,
            )
        }
        is TranslationUi.Streaming -> Text(t.text, style = style, color = Virgil.colors.textSecondary.copy(alpha = 0.8f))
        is TranslationUi.Ready -> Text(t.text, style = style, color = Virgil.colors.textSecondary)
        is TranslationUi.Failed -> Text(t.message, style = MaterialTheme.typography.bodySmall, color = Virgil.colors.error)
    }
}

/** Texto com palavras tocáveis: o deslocamento real do toque no layout decide a palavra. */
@Composable
private fun TappableText(text: String, selection: IntRange?, style: TextStyle, onTap: (Int) -> Unit) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val selColor = Virgil.colors.selection
    val annotated: AnnotatedString = remember(text, selection, selColor) {
        buildAnnotatedString {
            append(text)
            if (selection != null && selection.first >= 0 && selection.last < text.length) {
                addStyle(SpanStyle(background = selColor), selection.first, selection.last + 1)
            }
        }
    }
    Text(
        annotated, style = style, onTextLayout = { layout = it },
        modifier = Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { pos -> layout?.let { onTap(it.getOffsetForPosition(pos)) } } },
    )
}

/** Deslizar para os lados troca de unidade (além dos botões). */
private fun Modifier.swipeUnits(onSwipe: (Float) -> Unit): Modifier = pointerInput(Unit) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = { if (abs(total) > 120f) onSwipe(total); total = 0f },
        onHorizontalDrag = { _, d -> total += d },
    )
}
