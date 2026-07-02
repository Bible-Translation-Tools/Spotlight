package org.bibletranslationtools.glossary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.read_more

@Composable
fun VerseReference(
    reference: String,
    phrase: String,
    text: String,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val style = TextStyle.Default.copy(
        fontFamily = fontFamily,
        lineHeight = 28.sp,
        fontSize = 16.sp
    )

    val referenceTag = "reference"
    val referenceView = InlineTextContent(
        placeholder = Placeholder(
            width = (reference.length * 11).sp,
            height = style.lineHeight,
            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
        )
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                )
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = reference,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    var currentVerseText by remember { mutableStateOf(text) }

    LaunchedEffect(text) {
        currentVerseText = shortenVerseText(text, phrase)
    }

    val annotatedText = buildAnnotatedString {
        appendInlineContent(referenceTag, reference)

        var lastIndex = 0
        val regex = Regex(
            pattern = "\\b${Regex.escape(phrase)}\\b",
            option = RegexOption.IGNORE_CASE
        )
        regex.findAll(currentVerseText).forEach { match ->
            val startIndex = match.range.first
            val endIndex = match.range.last + 1

            append(currentVerseText.substring(lastIndex, startIndex))

            withStyle(
                style = SpanStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(match.value)
            }

            lastIndex = endIndex
        }
        if (lastIndex < currentVerseText.length) {
            append(currentVerseText.substring(lastIndex))
        }

        onClick?.let { func ->
            append(" ")
            withLink(
                link = LinkAnnotation.Clickable(
                    tag = "read_more",
                    linkInteractionListener = { func() }
                )
            ) {
                append(stringResource(Res.string.read_more))
            }
        }
    }

    Text(
        text = annotatedText,
        style = style,
        inlineContent = mapOf("reference" to referenceView),
        modifier = modifier
    )
}

private fun shortenVerseText(text: String, phrase: String): String {
    val maxLength = 100
    if (text.length <= maxLength) return text

    val wordIndex = text.indexOf(phrase, ignoreCase = true)

    if (wordIndex == -1) return "${text.take(maxLength)}..."

    val desiredStartIndex = wordIndex - (maxLength - phrase.length) / 2
    val startIndex = desiredStartIndex.coerceIn(0, text.length - maxLength)
    val endIndex = startIndex + maxLength

    return buildString {
        if (startIndex > 0) append("...")
        append(text.substring(startIndex, endIndex))
        if (endIndex < text.length) append("...")
    }
}
