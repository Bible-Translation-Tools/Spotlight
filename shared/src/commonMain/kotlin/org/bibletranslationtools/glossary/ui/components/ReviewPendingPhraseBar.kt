package org.bibletranslationtools.glossary.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.petertrr.diffutils.diff
import io.github.petertrr.diffutils.patch.DeltaType
import org.bibletranslationtools.glossary.data.api.PendingPhrase
import org.bibletranslationtools.glossary.data.api.ReviewStatus
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.approve
import spotlight.shared.generated.resources.reject

@Composable
fun ReviewPendingPhraseBar(
    pendingPhrase: PendingPhrase,
    onSave: (ReviewStatus) -> Unit,
    onDismiss: () -> Unit
) {
    val currentPhrase by rememberUpdatedState(pendingPhrase)
    var compareDiff by remember { mutableStateOf(false) }

    var spellingDiff by remember { mutableStateOf(AnnotatedString("")) }
    var descriptionDiff by remember { mutableStateOf(AnnotatedString("")) }

    LaunchedEffect(compareDiff) {
        if (compareDiff) {
            spellingDiff = generateAnnotatedDiff(
                oldText = currentPhrase.original?.spelling ?: "",
                newText = currentPhrase.phrase.spelling
            )
            descriptionDiff = generateAnnotatedDiff(
                oldText = currentPhrase.original?.description ?: "",
                newText = currentPhrase.phrase.description
            )
        } else {
            spellingDiff = AnnotatedString(currentPhrase.phrase.spelling)
            descriptionDiff = AnnotatedString(currentPhrase.phrase.description)
        }
    }

    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
                .clickable(enabled = false, onClick = {})
                .padding(bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    IconButton(
                        onClick = { compareDiff = !compareDiff }
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "compare",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = onDismiss
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "close",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                        .fillMaxWidth()
                ) {

                    Text(
                        text = spellingDiff,
                        fontSize = 39.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentPhrase.phrase.phrase,
                        fontSize = 28.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = descriptionDiff,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                onSave(ReviewStatus.APPROVED)
                                onDismiss()
                            },
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.tertiary
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ),
                            modifier = Modifier.weight(1f)
                                .height(48.dp)
                        ) {
                            Text(stringResource(Res.string.approve))
                        }

                        OutlinedButton(
                            onClick = {
                                onSave(ReviewStatus.REJECTED)
                                onDismiss()
                            },
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.error
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.weight(1f)
                                .height(48.dp)
                        ) {
                            Text(stringResource(Res.string.reject))
                        }
                    }
                }
            }
        }
    }
}

fun generateAnnotatedDiff(oldText: String, newText: String): AnnotatedString {
    val originalChars = oldText.toList()
    val newChars = newText.toList()
    val patch = diff(originalChars, newChars)

    return buildAnnotatedString {
        var lastOriginalIndex = 0

        patch.deltas.sortedBy { it.source.position }.forEach { delta ->
            if (delta.source.position > lastOriginalIndex) {
                append(oldText.substring(lastOriginalIndex, delta.source.position))
            }
            when (delta.type) {
                DeltaType.DELETE -> {
                    val deletedSegment = delta.source.lines.joinToString("")
                    pushStyle(
                        SpanStyle(
                            color = Color(0xFFB00020),
                            background = Color(0xFFFFCDD2),
                            textDecoration = TextDecoration.LineThrough
                        )
                    )
                    append(deletedSegment)
                    pop()
                }
                DeltaType.INSERT -> {
                    val insertedSegment = delta.target.lines.joinToString("")
                    pushStyle(
                        SpanStyle(
                            color = Color(0xFF006400),
                            background = Color(0xFFC8E6C9),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    append(insertedSegment)
                    pop()
                }
                DeltaType.CHANGE -> {
                    val oldSegment = delta.source.lines.joinToString("")
                    pushStyle(
                        SpanStyle(
                            color = Color(0xFFB00020),
                            background = Color(0xFFFFCDD2),
                            textDecoration = TextDecoration.LineThrough
                        )
                    )
                    append(oldSegment)
                    pop()

                    val newSegment = delta.target.lines.joinToString("")
                    pushStyle(
                        SpanStyle(
                            color = Color(0xFF006400),
                            background = Color(0xFFC8E6C9),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    append(newSegment)
                    pop()
                }
                else -> {}
            }

            lastOriginalIndex = delta.source.position + delta.source.lines.size
        }

        if (lastOriginalIndex < oldText.length) {
            append(oldText.substring(lastOriginalIndex))
        }
    }
}