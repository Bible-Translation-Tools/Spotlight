@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org.bibletranslationtools.glossary.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.selection.Selection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bibletranslationtools.glossary.data.Chapter
import org.bibletranslationtools.glossary.data.Phrase
import org.bibletranslationtools.glossary.normalize
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.add_to_glossary
import spotlight.shared.generated.resources.view_glossary
import kotlin.math.max
import kotlin.math.min

private const val VERSE_TAG = "verse_tag"

// Opt-in for onSelectionChange, which is an internal API.
@OptIn(InternalTextApi::class)
@Composable
fun SelectableText(
    chapter: Chapter,
    phrases: List<Phrase>,
    selectedText: String,
    currentVerse: String?,
    onSelectedTextChanged: (String) -> Unit,
    onSaveSelection: (String) -> Unit,
    onPhraseClick: (Phrase, String) -> Unit,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier
) {
    val dimColor = MaterialTheme.colorScheme.outline

    val currentChapter by rememberUpdatedState(newValue = chapter)
    val currentPhrases by rememberUpdatedState(newValue = phrases)

    var selection by remember { mutableStateOf<Selection?>(null) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var annotatedString by remember { mutableStateOf<AnnotatedString?>(null) }

    LaunchedEffect(selectedText) {
        if (selectedText.isEmpty()) {
            selection = null
        }
    }

    LaunchedEffect(
        currentChapter,
        currentPhrases,
        currentVerse,
        fontFamily,
        fontSize,
        lineHeight
    ) {
        selection = null
        onSelectedTextChanged("")
        textLayoutResult = null

        annotatedString = buildAnnotatedString {
            val regex = if (currentPhrases.isNotEmpty()) {
                val phrasesToFind = currentPhrases
                    .map { "\\b${Regex.escape(it.phrase.normalize())}\\b" }
                Regex(
                    phrasesToFind.joinToString("|"),
                    RegexOption.IGNORE_CASE
                )
            } else {
                null
            }

            currentChapter.verses.forEach { verse ->
                val normalizedVerseText = verse.text.normalize()

                val color = if (currentVerse == null || verse.number == currentVerse) {
                    Color.Unspecified
                } else {
                    dimColor
                }

                withStyle(
                    style = SpanStyle(
                        color = color,
                        fontSize = fontSize.div(1.5),
                        fontWeight = FontWeight.Bold,
                        baselineShift = BaselineShift.Superscript
                    )
                ) {
                    addStringAnnotation(
                        tag = VERSE_TAG,
                        annotation = verse.number,
                        start = length,
                        end = length + verse.number.length
                    )
                    append(verse.number)
                    append(" ")
                }

                if (regex == null) {
                    append(normalizedVerseText)
                } else {
                    var lastIndex = 0
                    regex.findAll(normalizedVerseText).forEach { match ->
                        withStyle(
                            style = SpanStyle(color = color)
                        ) {
                            append(normalizedVerseText.substring(lastIndex, match.range.first))
                        }

                        withLink(
                            link = LinkAnnotation.Clickable(
                                tag = match.value,
                                styles = TextLinkStyles(),
                                linkInteractionListener = {
                                    currentPhrases.firstOrNull {
                                        it.phrase.normalize()
                                            .equals(match.value.normalize(), ignoreCase = true)
                                    }?.let {
                                        onSelectedTextChanged("")
                                        onPhraseClick(it, verse.number)
                                    }
                                }
                            )
                        ) {
                            withStyle(
                                style = SpanStyle(
                                    color = color,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append(match.value)
                            }
                        }
                        lastIndex = match.range.last + 1
                    }

                    if (lastIndex < normalizedVerseText.length) {
                        withStyle(
                            style = SpanStyle(color = color)
                        ) {
                            append(normalizedVerseText.substring(lastIndex))
                        }
                    }
                }
                append(" ")
            }
        }
    }

    Box(modifier = modifier) {
        annotatedString?.let { text ->
            CompositionLocalProvider(LocalTextToolbar provides EmptyTextToolbar) {
                SelectionContainer(
                    selection = selection,
                    onSelectionChange = { newSelection ->
                        selection = newSelection
                        val newSelectedText = newSelection?.let { sel ->
                            val start = min(sel.start.offset, sel.end.offset)
                            val end = max(sel.start.offset, sel.end.offset)
                            text.substring(start, end)
                        } ?: ""
                        onSelectedTextChanged(newSelectedText)
                    }
                ) {
                    Text(
                        text = text,
                        style = LocalTextStyle.current.copy(lineHeight = 32.sp),
                        onTextLayout = { textLayoutResult = it },
                        fontFamily = fontFamily,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        selection?.let { sel ->
            textLayoutResult?.let { layoutResult ->
                val selectionBoundingBox = layoutResult.getPathForRange(
                    start = min(sel.start.offset, sel.end.offset),
                    end = max(sel.start.offset, sel.end.offset)
                ).getBounds()

                val density = LocalDensity.current
                val offsetX = with(density) { selectionBoundingBox.center.x.toDp() }
                val offsetY = with(density) { selectionBoundingBox.top.toDp() }

                Button(
                    onClick = {
                        onSaveSelection(selectedText)
                        onSelectedTextChanged("")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = MaterialTheme.shapes.medium,
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 8.dp
                    ),
                    modifier = Modifier
                        .offset(x = offsetX, y = offsetY)
                        .graphicsLayer {
                            val containerWidth = layoutResult.size.width.toFloat()
                            val margin = 8.dp.toPx()
                            val buttonHeight = size.height

                            val desiredX = selectionBoundingBox.center.x - size.width / 2
                            val clampedX = desiredX.coerceIn(0f, containerWidth - size.width)

                            val spaceAbove = selectionBoundingBox.top
                            val isEnoughSpaceAbove = spaceAbove > (buttonHeight + margin)

                            val desiredY = if (isEnoughSpaceAbove) {
                                spaceAbove - buttonHeight - margin
                            } else {
                                selectionBoundingBox.bottom + margin
                            }

                            translationX = clampedX - selectionBoundingBox.center.x
                            translationY = desiredY - selectionBoundingBox.top
                        }
                ) {
                    val isView = currentPhrases.any {
                        it.phrase.normalize().equals(selectedText.normalize(), ignoreCase = true)
                    } || selectedText.isEmpty()

                    val text = if (isView) {
                        stringResource(Res.string.view_glossary)
                    } else {
                        stringResource(Res.string.add_to_glossary)
                    }
                    Text(text)
                }
            }
        }
    }
}