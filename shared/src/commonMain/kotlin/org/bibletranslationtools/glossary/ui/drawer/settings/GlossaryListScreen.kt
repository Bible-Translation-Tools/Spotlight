package org.bibletranslationtools.glossary.ui.drawer.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFileSaver
import kotlinx.coroutines.launch
import org.bibletranslationtools.glossary.ui.components.GlossaryItem
import org.bibletranslationtools.glossary.ui.components.TopDrawerBar
import org.bibletranslationtools.glossary.ui.dialogs.ProgressDialog
import org.bibletranslationtools.glossary.ui.navigation.LocalSnackBarHostState
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.add_create_glossary
import spotlight.shared.generated.resources.add_glossary
import spotlight.shared.generated.resources.available_glossaries
import spotlight.shared.generated.resources.create_glossary
import spotlight.shared.generated.resources.glossaries_unavailable
import spotlight.shared.generated.resources.import_glossary_manually

@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun GlossaryListScreen(component: GlossaryListComponent) {
    val model by component.model.subscribeAsState()

    var isLoaded by remember { mutableStateOf(false) }
    val scrollState = rememberLazyListState()

    val coroutineScope = rememberCoroutineScope()
    val snackBar = LocalSnackBarHostState.current

    LaunchedEffect(model.selectedGlossary, model.glossaries) {
        if (model.glossaries.isNotEmpty() && !isLoaded) {
            model.selectedGlossary?.let { glossary ->
                val index = model.glossaries.map { it.glossary.id }.indexOf(glossary.glossary.id)
                scrollState.animateScrollToItem(index)
                isLoaded = true
            }
        }
    }

    LaunchedEffect(model.snackBarMessage) {
        model.snackBarMessage?.let { message ->
            coroutineScope.launch {
                component.clearSnackBarMessage()
                snackBar?.showSnackbar(message)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        TopDrawerBar(
            title = stringResource(Res.string.available_glossaries),
            onBackClick = component::navigateBack,
            modifier = Modifier.fillMaxWidth()
        )

        Column(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                        .padding(16.dp)
                ) {
                    if (model.glossaries.isNotEmpty()) {
                        Scaffold(
                            containerColor = MaterialTheme.colorScheme.surface,
                            floatingActionButton = {
                                Button(
                                    shape = CircleShape,
                                    onClick = component::navigateImportGlossary,
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "import glossary"
                                    )
                                }
                            }
                        ) {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(bottom = 2.dp),
                                state = scrollState
                            ) {
                                items(model.glossaries) { item ->
                                    GlossaryItem(
                                        item = item,
                                        isSelected = model.selectedGlossary == item,
                                        isActive = model.activeGlossary == item,
                                        onSelected = { component.selectGlossary(item) },
                                        onSelectedSave = component::saveGlossary,
                                        onShare = {
                                            coroutineScope.launch {
                                                FileKit.openFileSaver(
                                                    suggestedName = "glossary-${item.glossary.code}",
                                                    defaultExtension = "zip"
                                                )?.let { file ->
                                                    component.exportGlossary(file)
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(
                                space = 8.dp,
                                alignment = Alignment.CenterVertically
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(Res.string.glossaries_unavailable),
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            )

                            Text(
                                text = stringResource(Res.string.add_create_glossary),
                                fontSize = 16.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = component::navigateCreateGlossary,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text(stringResource(Res.string.create_glossary))
                            }

                            ElevatedButton(
                                onClick = component::navigateImportGlossary,
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text(stringResource(Res.string.add_glossary))
                            }

                            TextButton(
                                onClick = component::navigateImportManually
                            ) {
                                Text(
                                    text = stringResource(
                                        Res.string.import_glossary_manually
                                    ),
                                    textDecoration = TextDecoration.Underline
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    model.progress?.let { progress ->
        ProgressDialog(progress)
    }
}
