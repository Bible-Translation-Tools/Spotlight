package org.bibletranslationtools.glossary.ui.drawer.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.bibletranslationtools.glossary.Utils
import org.bibletranslationtools.glossary.ui.components.LanguageSelector
import org.bibletranslationtools.glossary.ui.components.TopDrawerBar
import org.bibletranslationtools.glossary.ui.dialogs.ConfirmDialog
import org.bibletranslationtools.glossary.ui.dialogs.ProgressDialog
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.cancel
import spotlight.shared.generated.resources.create_glossary
import spotlight.shared.generated.resources.dictionary
import spotlight.shared.generated.resources.download
import spotlight.shared.generated.resources.download_resource_request
import spotlight.shared.generated.resources.glossary_code
import spotlight.shared.generated.resources.new_glossary
import spotlight.shared.generated.resources.ok
import spotlight.shared.generated.resources.saving
import spotlight.shared.generated.resources.share_code_hint
import spotlight.shared.generated.resources.source_language
import spotlight.shared.generated.resources.target_language

@Composable
fun CreateGlossaryScreen(component: CreateGlossaryComponent) {
    val model by component.model.subscribeAsState()

    val code by rememberSaveable {
        mutableStateOf(Utils.randomCode())
    }

    val createEnabled by remember {
        derivedStateOf {
            model.sourceLanguage != null && model.targetLanguage != null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopDrawerBar(
            title = stringResource(Res.string.new_glossary),
            subTitle = "",
            onBackClick = component::navigateBack,
            modifier = Modifier.fillMaxWidth()
        )

        Column(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .background(color = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                        .padding(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Image(
                        painter = painterResource(Res.drawable.dictionary),
                        contentDescription = "dictionary",
                        modifier = Modifier.size(120.dp)
                    )
                    Text(
                        text = stringResource(
                            Res.string.glossary_code,
                            code
                        ),
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )

                    Text(
                        text = stringResource(Res.string.share_code_hint),
                        textAlign = TextAlign.Center
                    )

                    LanguageSelector(
                        title = stringResource(Res.string.source_language),
                        language = model.sourceLanguage,
                        onClick = component::onSourceLanguageClick,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LanguageSelector(
                        title = stringResource(Res.string.target_language),
                        language = model.targetLanguage,
                        onClick = component::onTargetLanguageClick,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            component.createGlossary(code)
                        },
                        shape = MaterialTheme.shapes.medium,
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp
                        ),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = if (createEnabled) {
                                Color.Unspecified
                            } else {
                                Color.Transparent
                            }
                        ),
                        enabled = createEnabled && !model.isSaving,
                        modifier = Modifier.fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.create_glossary),
                            fontSize = 16.sp
                        )
                    }

                    model.error?.let { error ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = error.error,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    if (model.isSaving) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(Res.string.saving))
                        }
                    }

                    model.resourceRequest?.let { request ->
                        ConfirmDialog(
                            title = stringResource(Res.string.download),
                            text = stringResource(Res.string.download_resource_request),
                            confirmButtonText = stringResource(Res.string.ok),
                            dismissButtonText = stringResource(Res.string.cancel),
                            onConfirm = {
                                component.downloadResource(request)
                            },
                            onDismiss = {
                                component.clearResourceRequest()
                            }
                        )
                    }

                    model.progress?.let { progress ->
                        ProgressDialog(progress)
                    }
                }
            }
        }
    }
}