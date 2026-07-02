package org.bibletranslationtools.glossary.ui.drawer.keyterms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.bibletranslationtools.glossary.data.PhraseWorkflow
import org.bibletranslationtools.glossary.ui.components.TopDrawerBar
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.add_another_phrase
import spotlight.shared.generated.resources.cancel
import spotlight.shared.generated.resources.create_new_phrase
import spotlight.shared.generated.resources.create_phrase
import spotlight.shared.generated.resources.description
import spotlight.shared.generated.resources.edit_reviewed_phrase_not_allowed
import spotlight.shared.generated.resources.editing
import spotlight.shared.generated.resources.return_to_glosssary
import spotlight.shared.generated.resources.save_exit
import spotlight.shared.generated.resources.saving
import spotlight.shared.generated.resources.spelling
import spotlight.shared.generated.resources.success
import spotlight.shared.generated.resources.successfully_added_phrase
import spotlight.shared.generated.resources.successfully_edited_phrase
import spotlight.shared.generated.resources.target_language_placeholder

@Composable
fun EditPhraseScreen(component: EditPhraseComponent) {
    val model by component.model.subscribeAsState()

    var spelling by rememberSaveable {
        mutableStateOf("")
    }
    var description by rememberSaveable {
        mutableStateOf("")
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(model.phrase) {
        model.phrase?.let { phrase ->
            if (spelling.isEmpty()) {
                spelling = phrase.spelling
            }
            if (description.isEmpty()) {
                description = phrase.description
            }
        }
        focusRequester.requestFocus()
    }

    DisposableEffect(Unit) {
        onDispose {
            component.reset()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!model.justSaved) {
            TopDrawerBar(
                title = if (model.isNewPhrase) {
                    stringResource(Res.string.create_phrase)
                } else stringResource(Res.string.editing),
                subTitle = model.phrase?.phrase,
                onBackClick = component::navigateBack,
                modifier = Modifier.fillMaxWidth()
            )

            Column(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .background(color = MaterialTheme.colorScheme.surface)
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(Res.string.spelling),
                            fontWeight = FontWeight.W600
                        )
                        OutlinedTextField(
                            value = spelling,
                            onValueChange = { spelling = it },
                            singleLine = true,
                            textStyle = TextStyle.Default.copy(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            enabled = !model.isSaving,
                            readOnly = model.phrase?.workflow == PhraseWorkflow.REVIEWED,
                            shape = MaterialTheme.shapes.medium,
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.8f
                                ),
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.12f
                                ),
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.1f
                                )
                            ),
                            placeholder = {
                                Text(
                                    text = stringResource(Res.string.target_language_placeholder),
                                    fontSize = 22.sp
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                                .focusRequester(focusRequester)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(Res.string.description),
                            fontWeight = FontWeight.W600
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            minLines = 5,
                            maxLines = 10,
                            enabled = !model.isSaving,
                            readOnly = model.phrase?.workflow == PhraseWorkflow.REVIEWED,
                            shape = MaterialTheme.shapes.medium,
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.8f
                                ),
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.12f
                                ),
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.1f
                                )
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                enabled = spelling.isNotEmpty()
                                        && !model.isSaving
                                        && model.phrase?.workflow != PhraseWorkflow.REVIEWED,
                                onClick = {
                                    component.savePendingPhrase(
                                        spelling = spelling,
                                        description = description
                                    )
                                },
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (model.isNewPhrase) {
                                        stringResource(Res.string.create_new_phrase)
                                    } else stringResource(Res.string.save_exit)
                                )
                            }
                            Button(
                                onClick = component::navigateBack,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(stringResource(Res.string.cancel))
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        if (model.phrase?.workflow == PhraseWorkflow.REVIEWED) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(Res.string.edit_reviewed_phrase_not_allowed),
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }

                        model.error?.let { error ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = error,
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
                    }
                }
            }
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(1f)
                    .padding(16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(Res.string.success),
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = stringResource(
                            if (model.isNewPhrase) {
                                Res.string.successfully_added_phrase
                            } else Res.string.successfully_edited_phrase
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = component::onNavigateToGlossary,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(
                                text = stringResource(Res.string.return_to_glosssary),
                                fontSize = 16.sp
                            )
                        }

                        if (model.isNewPhrase) {
                            Button(
                                onClick = component::navigateBack,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = stringResource(Res.string.add_another_phrase),
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}