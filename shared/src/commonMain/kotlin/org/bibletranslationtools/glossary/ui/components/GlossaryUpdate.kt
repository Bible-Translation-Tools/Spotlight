package org.bibletranslationtools.glossary.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.cancel
import spotlight.shared.generated.resources.download_now
import spotlight.shared.generated.resources.downloading
import spotlight.shared.generated.resources.glossary_update_failed
import spotlight.shared.generated.resources.glossary_update_failed_description
import spotlight.shared.generated.resources.glossary_updated
import spotlight.shared.generated.resources.glossary_updated_description
import spotlight.shared.generated.resources.ok
import spotlight.shared.generated.resources.this_make_take_moment
import spotlight.shared.generated.resources.try_again
import spotlight.shared.generated.resources.update_available
import spotlight.shared.generated.resources.update_available_description

enum class UpdateStatus {
    DEFAULT,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

@Composable
fun GlossaryUpdate(
    status: UpdateStatus,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
            .padding(16.dp)
    ) {
        when (status) {
            UpdateStatus.DEFAULT -> {
                Text(
                    text = stringResource(Res.string.update_available),
                    fontWeight = FontWeight.W500,
                    fontSize = 24.sp
                )

                Text(
                    text = stringResource(Res.string.update_available_description),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDownload,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            Res.string.download_now
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                ElevatedButton(
                    onClick = onDismiss,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = ButtonDefaults.buttonElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                        .height(36.dp)
                ) {
                    Text(stringResource(Res.string.cancel))
                }
            }
            UpdateStatus.DOWNLOADING -> {
                Text(
                    text = stringResource(Res.string.downloading),
                    fontWeight = FontWeight.W500,
                    fontSize = 24.sp
                )

                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(Res.string.this_make_take_moment),
                    textAlign = TextAlign.Center
                )

                ElevatedButton(
                    onClick = onDismiss,
                    enabled = false,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = ButtonDefaults.buttonElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                        .height(36.dp)
                ) {
                    Text(stringResource(Res.string.cancel))
                }
            }
            UpdateStatus.DOWNLOADED -> {
                Text(
                    text = stringResource(Res.string.glossary_updated),
                    fontWeight = FontWeight.W500,
                    fontSize = 24.sp
                )

                Text(
                    text = stringResource(Res.string.glossary_updated_description),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )

                Button(
                    onClick = onDismiss,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            Res.string.ok
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            UpdateStatus.FAILED -> {
                Text(
                    text = stringResource(Res.string.glossary_update_failed),
                    fontWeight = FontWeight.W500,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.error
                )

                Text(
                    text = stringResource(Res.string.glossary_update_failed_description),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error
                )

                TextButton(
                    onClick = onDownload
                ) {
                    Text(
                        text = stringResource(Res.string.try_again)
                    )
                }
            }
        }
    }
}