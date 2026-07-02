package org.bibletranslationtools.glossary.ui.drawer.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
import kotlinx.coroutines.launch
import org.bibletranslationtools.glossary.ui.components.OtpInput
import org.bibletranslationtools.glossary.ui.components.TopDrawerBar
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.back
import spotlight.shared.generated.resources.download
import spotlight.shared.generated.resources.downloading_glossary
import spotlight.shared.generated.resources.downloading_glossary_hint
import spotlight.shared.generated.resources.glossary_code_not_recognized
import spotlight.shared.generated.resources.import_glossary_hint
import spotlight.shared.generated.resources.import_glossary_manually
import spotlight.shared.generated.resources.import_glossary_title

@Composable
fun ImportGlossaryScreen(component: ImportGlossaryComponent) {
    val model by component.model.subscribeAsState()

    val focusRequesters = remember { List(5) { FocusRequester() } }
    val coroutineScope = rememberCoroutineScope()

    val onImeAction = {
        if (model.otpCode.none { it == null }) {
            component.onDownloadClicked()
        }
    }

    LaunchedEffect(model.focusedIndex) {
        model.focusedIndex?.let { index ->
            if (index in focusRequesters.indices) {
                focusRequesters[index].requestFocus()
            }
        }
    }

    LaunchedEffect(model.autoImportManually) {
        if (model.autoImportManually) {
            coroutineScope.launch {
                FileKit.openFilePicker()?.let {
                    component.onImportClicked(it)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopDrawerBar(
            title = stringResource(Res.string.back),
            subTitle = "",
            enabled = model.progress == null,
            onBackClick = component::navigateBack,
            modifier = Modifier.fillMaxWidth()
        )

        Column(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .background(color = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                        .padding(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(128.dp))

                    if (model.progress == null) {
                        Text(
                            text = stringResource(Res.string.import_glossary_title),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(Res.string.import_glossary_hint),
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        OtpInput(
                            code = model.otpCode,
                            focusRequesters = focusRequesters,
                            onAction = { component.onOtpAction(it) },
                            onImeAction = onImeAction,
                            isError = model.error != null,
                            modifier = Modifier
                                .widthIn(max = 600.dp)
                        )

                        if (model.error != null) {
                            Spacer(modifier = Modifier.height(32.dp))

                            Text(
                                text = stringResource(Res.string.glossary_code_not_recognized),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = component::onDownloadClicked,
                            enabled = model.otpCode.none { it == null },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = stringResource(Res.string.download),
                                fontSize = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    FileKit.openFilePicker()?.let {
                                        component.onImportClicked(it)
                                    }
                                }
                            }
                        ) {
                            Text(
                                text = stringResource(Res.string.import_glossary_manually),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp,
                                textDecoration = TextDecoration.Underline
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(Res.string.downloading_glossary),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(Res.string.downloading_glossary_hint),
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        LinearProgressIndicator(
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}