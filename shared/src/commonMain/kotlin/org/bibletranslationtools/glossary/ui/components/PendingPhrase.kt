package org.bibletranslationtools.glossary.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bibletranslationtools.glossary.data.api.PendingPhrase
import org.bibletranslationtools.glossary.data.api.ReviewStatus
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.created_by
import spotlight.shared.generated.resources.edited_by
import spotlight.shared.generated.resources.percent_approved
import spotlight.shared.generated.resources.percent_rejected
import spotlight.shared.generated.resources.percent_undecided
import spotlight.shared.generated.resources.review
import kotlin.math.roundToInt

@Composable
fun PendingPhrase(
    pendingPhrase: PendingPhrase,
    adminsCount: Int,
    onView: () -> Unit,
    modifier: Modifier = Modifier
) {
    val approvedProgress by remember(pendingPhrase.reviews) {
        mutableFloatStateOf(
            pendingPhrase.reviews.count {
                it.status == ReviewStatus.APPROVED
            } / adminsCount.toFloat()
        )
    }

    val rejectedProgress by remember(pendingPhrase.reviews) {
        mutableFloatStateOf(
            pendingPhrase.reviews.count {
                it.status == ReviewStatus.REJECTED
            } / adminsCount.toFloat()
        )
    }

    val authorText = if (pendingPhrase.original != null) {
        stringResource(
            Res.string.edited_by,
            pendingPhrase.user.emoji,
            pendingPhrase.user.username
        )
    } else stringResource(
        Res.string.created_by,
        pendingPhrase.user.emoji,
        pendingPhrase.user.username
    )

    val restProgress = 1f - approvedProgress - rejectedProgress
    val arrangement = if (approvedProgress > 0 || rejectedProgress > 0) {
        Arrangement.SpaceBetween
    } else Arrangement.End

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier
                .padding(16.dp)
        ) {
            Column(
                modifier = modifier
            ) {
                Row(
                    horizontalArrangement = arrangement,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (approvedProgress > 0) {
                        Text(
                            text = stringResource(
                                Res.string.percent_approved,
                                (approvedProgress * 100).roundToInt()
                            ),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (rejectedProgress > 0) {
                        Text(
                            text = stringResource(
                                Res.string.percent_rejected,
                                (rejectedProgress * 100).roundToInt()
                            ),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (restProgress > 0) {
                        Text(
                            text = stringResource(
                                Res.string.percent_undecided,
                                (restProgress * 100).roundToInt()
                            ),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                SegmentedProgressBar(
                    progress1 = approvedProgress,
                    progress2 = rejectedProgress,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(
                text = authorText,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.outline
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = pendingPhrase.phrase.spelling,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = pendingPhrase.phrase.phrase,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Button(
                    onClick = onView,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = stringResource(Res.string.review),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}