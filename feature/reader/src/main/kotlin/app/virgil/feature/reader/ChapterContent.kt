package app.virgil.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.virgil.core.designsystem.component.FileImage
import app.virgil.core.designsystem.theme.Virgil
import app.virgil.core.designsystem.theme.VirgilFonts
import app.virgil.core.model.Language
import app.virgil.core.model.ReadingPreferences
import app.virgil.engine.books.model.Block
import app.virgil.engine.books.model.BlockType
import app.virgil.engine.books.model.ChapterDoc
import app.virgil.engine.books.model.SpanStyle as DocSpan
import app.virgil.core.model.TextAlign as PrefAlign

/** Callbacks de interação com o texto. Offsets são relativos ao texto do bloco. */
class TextGestures(
    val onTap: (blockIndex: Int, offset: Int) -> Boolean,
    /** Toque no texto fora de uma palavra (espaço, pontuação, fim de linha): equivale a tocar no fundo. */
    val onBackground: () -> Unit,
    val onLongPress: (blockIndex: Int, offset: Int) -> Boolean,
    val onDrag: (blockIndex: Int, offset: Int) -> Unit,
    val onDragEnd: () -> Unit,
)

@Composable
fun ChapterContent(
    doc: ChapterDoc,
    chapterTitle: String?,
    language: Language?,
    prefs: ReadingPreferences,
    selection: TextSelection?,
    listState: LazyListState,
    contentPadding: PaddingValues,
    gestures: TextGestures,
    imagePath: (String) -> String,
    nextChapterTitle: String?,
    isLastChapter: Boolean,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Virgil.colors
    val base = remember(prefs, language, colors) {
        TextStyle(
            fontFamily = VirgilFonts.reading(prefs.font),
            fontSize = prefs.fontSizeSp.sp,
            lineHeight = (prefs.fontSizeSp * prefs.lineHeight).sp,
            color = colors.textReading,
            textAlign = if (prefs.align == PrefAlign.Justify) TextAlign.Justify else TextAlign.Start,
            hyphens = if (prefs.hyphenate) Hyphens.Auto else Hyphens.None,
            lineBreak = LineBreak.Paragraph,
            // Idioma do livro: hifenização e quebra de linha seguem as regras do idioma certo.
            localeList = language?.let { LocaleList(it.tag) },
            textIndent = if (prefs.paragraphSpacingEm < 0.05f) TextIndent(firstLine = 1.4.em) else TextIndent.None,
        )
    }
    val paragraphGap = with(LocalDensity.current) { (prefs.fontSizeSp * prefs.paragraphSpacingEm).sp.toDp() }
    val startsWithHeading = doc.blocks.firstOrNull()?.type == BlockType.Heading

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "header") {
            if (!startsWithHeading && !chapterTitle.isNullOrBlank()) {
                Text(
                    chapterTitle.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.14.em),
                    color = colors.secondaryInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = prefs.horizontalMarginDp.dp).padding(top = 24.dp, bottom = 28.dp),
                )
            } else {
                Spacer(Modifier.height(16.dp))
            }
        }
        itemsIndexed(doc.blocks, key = { _, b -> b.start * 8 + b.type.ordinal }) { index, block ->
            val sel = selection?.takeIf { it.blockIndex == index }
            BlockView(
                block = block, index = index, base = base, prefs = prefs, selection = sel, gestures = gestures,
                imagePath = imagePath, paragraphGap = paragraphGap,
            )
        }
        item(key = "footer") {
            ChapterFooter(nextChapterTitle, isLastChapter, onNextChapter, prefs.horizontalMarginDp)
        }
    }
}

@Composable
private fun BlockView(
    block: Block,
    index: Int,
    base: TextStyle,
    prefs: ReadingPreferences,
    selection: TextSelection?,
    gestures: TextGestures,
    imagePath: (String) -> String,
    paragraphGap: androidx.compose.ui.unit.Dp,
) {
    val colors = Virgil.colors
    val margin = prefs.horizontalMarginDp.dp
    when (block.type) {
        BlockType.Image -> {
            FileImage(
                path = block.image?.let(imagePath),
                contentDescription = block.alt,
                contentScale = ContentScale.Fit,
                targetWidthPx = 1080,
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(horizontal = margin, vertical = 12.dp),
            )
        }
        BlockType.Separator -> {
            Text(
                "⁂", textAlign = TextAlign.Center, color = colors.secondary,
                style = base.copy(fontSize = (prefs.fontSizeSp * 0.9f).sp, textIndent = TextIndent.None),
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            )
        }
        else -> {
            val style = styleFor(block, base)
            val annotated = remember(block, selection, colors) { annotate(block, selection, colors.selection, colors.secondaryInk) }
            val textModifier = Modifier
            val bottomGap = when (block.type) {
                BlockType.Heading -> 18.dp
                BlockType.Verse -> paragraphGap.coerceAtLeast(10.dp)
                else -> paragraphGap
            }
            when (block.type) {
                BlockType.Quote -> Row(Modifier.padding(start = margin + (8 * (block.level - 1)).dp, end = margin, bottom = bottomGap)) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(colors.divider))
                    Spacer(Modifier.width(14.dp))
                    SelectableText(annotated, style, index, gestures, textModifier.weight(1f))
                }
                BlockType.ListItem -> Row(Modifier.padding(start = margin + (12 * (block.level - 1)).dp, end = margin, bottom = bottomGap)) {
                    Text("•", style = style.copy(color = colors.secondary, textIndent = TextIndent.None), modifier = Modifier.width(18.dp))
                    SelectableText(annotated, style, index, gestures, textModifier.weight(1f))
                }
                BlockType.Verse -> SelectableText(annotated, style, index, gestures, textModifier.padding(start = margin + 18.dp, end = margin, bottom = bottomGap))
                BlockType.Heading -> SelectableText(annotated, style, index, gestures, textModifier.padding(start = margin, end = margin, top = if (index == 0) 8.dp else 20.dp, bottom = bottomGap))
                else -> SelectableText(annotated, style, index, gestures, textModifier.padding(start = margin, end = margin, bottom = bottomGap))
            }
        }
    }
}

private class LayoutHolder { var layout: TextLayoutResult? = null }

/**
 * Texto do livro com gestos de seleção:
 *  - toque: palavra (fora de uma palavra, o toque equivale a tocar no fundo: alterna os controles);
 *  - pressão longa + arraste: expressão (várias palavras no mesmo parágrafo).
 */
@Composable
private fun SelectableText(text: AnnotatedString, style: TextStyle, index: Int, gestures: TextGestures, modifier: Modifier) {
    val holder = remember { LayoutHolder() }
    Text(
        text,
        style = style,
        onTextLayout = { holder.layout = it },
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(index, gestures) {
                detectTapGestures(onTap = { pos ->
                    val offset = holder.layout?.let { offsetAt(it, pos) }
                    if (offset == null || !gestures.onTap(index, offset)) gestures.onBackground()
                })
            }
            .pointerInput(index, gestures) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos -> holder.layout?.let { l -> offsetAt(l, pos)?.let { gestures.onLongPress(index, it) } } },
                    onDrag = { change, _ ->
                        holder.layout?.let { l -> gestures.onDrag(index, l.getOffsetForPosition(change.position)) }
                        change.consume()
                    },
                    onDragEnd = { gestures.onDragEnd() },
                    onDragCancel = { gestures.onDragEnd() },
                )
            },
    )
}

/** Offset do caractere sob o ponto, ou null se o ponto estiver fora da área ocupada pelo texto da linha. */
internal fun offsetAt(layout: TextLayoutResult, pos: Offset): Int? {
    if (pos.y < 0 || pos.y > layout.size.height) return null
    val line = layout.getLineForVerticalPosition(pos.y)
    val left = layout.getLineLeft(line)
    val right = layout.getLineRight(line)
    if (pos.x < left - 4f || pos.x > right + 4f) return null
    return layout.getOffsetForPosition(pos).coerceIn(0, (layout.layoutInput.text.length - 1).coerceAtLeast(0))
}

private fun styleFor(block: Block, base: TextStyle): TextStyle = when (block.type) {
    BlockType.Heading -> base.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = base.fontSize * (if (block.level <= 1) 1.3f else if (block.level == 2) 1.18f else 1.06f),
        lineHeight = base.fontSize * 1.6f,
        textAlign = if (block.level <= 2) TextAlign.Center else TextAlign.Start,
        hyphens = Hyphens.None,
        textIndent = TextIndent.None,
    )
    BlockType.Quote -> base.copy(fontSize = base.fontSize * 0.95f, textIndent = TextIndent.None)
    BlockType.Verse, BlockType.Preformatted -> base.copy(textAlign = TextAlign.Start, hyphens = Hyphens.None, textIndent = TextIndent.None)
    BlockType.ListItem -> base.copy(textIndent = TextIndent.None)
    else -> base
}

internal fun annotate(block: Block, selection: TextSelection?, selectionColor: androidx.compose.ui.graphics.Color, noteColor: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        append(block.text)
        val len = block.text.length
        block.spans.forEach { sp ->
            val s = sp.start.coerceIn(0, len)
            val e = sp.end.coerceIn(s, len)
            if (e <= s) return@forEach
            val style = when (sp.style) {
                DocSpan.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
                DocSpan.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
                DocSpan.Superscript -> SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.7.em)
                DocSpan.Subscript -> SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.7.em)
                DocSpan.SmallCaps -> SpanStyle(fontFeatureSettings = "smcp, c2sc")
                DocSpan.Link -> SpanStyle(color = noteColor)
            }
            addStyle(style, s, e)
        }
        if (selection != null) {
            val s = selection.start.coerceIn(0, len)
            val e = selection.end.coerceIn(s, len)
            if (e > s) addStyle(SpanStyle(background = selectionColor), s, e)
        }
    }

@Composable
private fun ChapterFooter(nextTitle: String?, isLast: Boolean, onNext: () -> Unit, margin: Int) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = margin.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("⁂", color = Virgil.colors.secondary, style = MaterialTheme.typography.titleLarge)
        if (isLast) {
            Text("Fim do livro", style = MaterialTheme.typography.titleMedium, color = Virgil.colors.textSecondary)
        } else if (nextTitle != null) {
            TextButton(onClick = onNext) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PRÓXIMO CAPÍTULO", style = MaterialTheme.typography.labelSmall, color = Virgil.colors.secondaryInk)
                    Text(nextTitle, style = MaterialTheme.typography.titleMedium.copy(fontFamily = VirgilFonts.Literata), color = Virgil.colors.primary, textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(120.dp))
    }
}
