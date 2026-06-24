package org.bibletranslationtools.glossary.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bibletranslationtools.glossary.data.Phrase
import org.bibletranslationtools.glossary.data.api.ReviewStatus
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.target_language_needed

@Composable
fun PhraseItem(
    phrase: Phrase,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetNeededStr = stringResource(Res.string.target_language_needed)

    Card(
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier.heightIn(min = 48.dp),
        onClick = onClick
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            if (phrase.pending) {
                when(phrase.status) {
                    null, ReviewStatus.UNREVIEWED -> {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "pending"
                        )
                    }
                    ReviewStatus.APPROVED -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "approved",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    ReviewStatus.REJECTED -> {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = "rejected",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = phrase.phrase,
                    fontSize = 12.sp
                )
                Text(
                    text = phrase.spelling.ifEmpty { targetNeededStr },
                    fontWeight = FontWeight.Bold,
                    fontStyle = if (phrase.spelling.isEmpty()) {
                        FontStyle.Italic
                    } else FontStyle.Normal,
                    color = if (phrase.spelling.isEmpty()) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    } else MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "go"
            )
        }
    }
}