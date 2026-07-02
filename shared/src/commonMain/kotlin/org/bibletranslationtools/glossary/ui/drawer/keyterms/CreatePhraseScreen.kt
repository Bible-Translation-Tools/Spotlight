package org.bibletranslationtools.glossary.ui.drawer.keyterms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.bibletranslationtools.glossary.ui.components.PhraseItem
import org.bibletranslationtools.glossary.ui.components.SearchField
import org.bibletranslationtools.glossary.ui.components.TopDrawerBar
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.create_phrase
import spotlight.shared.generated.resources.search_placeholder
import spotlight.shared.generated.resources.search_word_hint

@Composable
fun CreatePhraseScreen(component: CreatePhraseComponent) {
    val model by component.model.subscribeAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val searchHintString = stringResource(Res.string.search_word_hint)
    val searchHintAnnotated = buildAnnotatedString {
        val parts = searchHintString.split("{", "}")
        parts.forEachIndexed { index, part ->
            if (index % 2 != 0) {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(part)
                }
            } else {
                append(part)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .padding(top = 16.dp)
    ) {
        TopDrawerBar(
            title = stringResource(Res.string.create_phrase),
            onDismiss = component::dismiss,
            onBackClick = component::navigateBack,
            modifier = Modifier.fillMaxWidth()
        )

        Column(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .background(color = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .padding(16.dp)
                ) {
                    SearchField(
                        searchQuery = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            component.onSearchQueryChanged(searchQuery)
                        },
                        placeholder = {
                            Text(
                                text = stringResource(Res.string.search_placeholder),
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.5f
                                )
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = searchHintAnnotated,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (model.isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        LazyColumn {
                            items(model.results) { phrase ->
                                if (phrase.spelling.isNotEmpty()) {
                                    PhraseItem(
                                        phrase = phrase,
                                        onClick = { component.onEditClick(phrase) }
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier.padding(12.dp)
                                            .clickable {
                                                component.onEditClick(phrase)
                                            }
                                    ) {
                                        Text(
                                            text = phrase.phrase,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "create"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}