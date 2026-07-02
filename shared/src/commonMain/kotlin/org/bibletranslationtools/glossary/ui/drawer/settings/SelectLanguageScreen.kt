package org.bibletranslationtools.glossary.ui.drawer.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.bibletranslationtools.glossary.ui.components.LanguageItem
import org.bibletranslationtools.glossary.ui.components.SearchField
import org.bibletranslationtools.glossary.ui.components.TopDrawerBar
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.search_language_placeholder
import spotlight.shared.generated.resources.source_language
import spotlight.shared.generated.resources.target_language

@Composable
fun SelectLanguageScreen(component: SelectLanguageComponent) {
    val model by component.model.subscribeAsState()

    var filteredLanguages by remember(model.languages) {
        mutableStateOf(model.languages)
    }
    var searchQuery by rememberSaveable {
        mutableStateOf("")
    }

    LaunchedEffect(searchQuery) {
        filteredLanguages = if (searchQuery.isNotEmpty()) {
            model.languages.filter { language ->
                language.slug.startsWith(searchQuery, ignoreCase = true)
                        || language.name.startsWith(searchQuery, ignoreCase = true)
                        || language.angName.startsWith(searchQuery, ignoreCase = true)
            }
        } else model.languages
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val title = if (model.type == LanguageType.SOURCE) {
            stringResource(Res.string.source_language)
        } else {
            stringResource(Res.string.target_language)
        }
        TopDrawerBar(
            title = title,
            subTitle = "",
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
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                        .padding(16.dp)
                ) {
                    SearchField(
                        searchQuery = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = stringResource(Res.string.search_language_placeholder),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider()

                    LazyColumn {
                        items(filteredLanguages) { language ->
                            LanguageItem(
                                language = language,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    component.onLanguageClick(language)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}