package org.bibletranslationtools.glossary.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bibletranslationtools.glossary.data.Chapter
import org.bibletranslationtools.glossary.data.Phrase
import org.bibletranslationtools.glossary.data.Ref
import org.bibletranslationtools.glossary.data.Resource
import org.bibletranslationtools.glossary.data.Workbook
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.reference_not_found
import spotlight.shared.generated.resources.target_language_needed

data class PhraseDetails(
    val phrase: Phrase,
    val phrases: List<Phrase>,
    val ref: Ref?,
    val book: Workbook,
    val chapter: Chapter,
    val verse: String? = null
)

enum class PhraseNavDir(val value: Int) {
    PREV(-1),
    NEXT(1)
}

@Composable
fun PhraseDetailsBar(
    details: PhraseDetails,
    resource: Resource,
    fontFamily: FontFamily,
    onNavPhrase: (PhraseNavDir) -> Unit,
    onViewDetails: (Phrase) -> Unit,
    onDismiss: () -> Unit
) {
    val currentPhrase by rememberUpdatedState(details.phrase)
    val currentRef by rememberUpdatedState(details.ref)
    var currentVerseText by remember { mutableStateOf("") }

    val reference = currentRef?.let {
        "${it.book.uppercase()} ${it.chapter}:${it.verse}"
    }

    val targetNeededStr = stringResource(Res.string.target_language_needed)

    LaunchedEffect(currentPhrase, currentRef) {
        currentVerseText = currentRef?.getVerseText(resource) ?: ""
    }

    Column(
        modifier = Modifier
            .padding(vertical = 24.dp, horizontal = 16.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { onNavPhrase(PhraseNavDir.PREV) },
                modifier = Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.medium
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentPhrase.phrase,
                    style = LocalTextStyle.current.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.W700,
                        fontFamily = fontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            onDismiss()
                            onViewDetails(currentPhrase)
                        }
                    ) {
                        Text(
                            text = currentPhrase.spelling.ifEmpty { targetNeededStr },
                            fontSize = 31.sp,
                            fontWeight = FontWeight.W700,
                            fontFamily = fontFamily,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        )
                    }
                    if (!currentPhrase.audio.isNullOrEmpty()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Play pronunciation",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            IconButton(
                onClick = { onNavPhrase(PhraseNavDir.NEXT) },
                modifier = Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.medium
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        reference?.let { ref ->
            VerseReference(
                reference = ref,
                phrase = currentPhrase.phrase,
                text = currentVerseText,
                fontFamily = fontFamily,
                modifier = Modifier.fillMaxWidth()
            )
        } ?: run {
            Text(text = stringResource(Res.string.reference_not_found))
        }
    }
}