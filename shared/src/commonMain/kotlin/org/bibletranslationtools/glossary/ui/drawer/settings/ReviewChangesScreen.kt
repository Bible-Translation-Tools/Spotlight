package org.bibletranslationtools.glossary.ui.drawer.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.launch
import org.bibletranslationtools.glossary.data.api.PendingPhrase
import org.bibletranslationtools.glossary.data.api.UserRole
import org.bibletranslationtools.glossary.ui.components.PendingPhrase
import org.bibletranslationtools.glossary.ui.components.ReviewPendingPhraseBar
import org.bibletranslationtools.glossary.ui.components.SettingsSection
import org.bibletranslationtools.glossary.ui.components.SettingsSectionDefaults
import org.bibletranslationtools.glossary.ui.components.TopDrawerBar
import org.bibletranslationtools.glossary.ui.dialogs.ProgressDialog
import org.bibletranslationtools.glossary.ui.navigation.LocalSnackBarHostState
import org.bibletranslationtools.glossary.ui.state.AppStateStore
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.edited_terms
import spotlight.shared.generated.resources.new_terms
import spotlight.shared.generated.resources.pending_phrases
import spotlight.shared.generated.resources.review_changes
import spotlight.shared.generated.resources.words_total

@Composable
fun ReviewChangesScreen(component: ReviewChangesComponent) {
    val model by component.model.subscribeAsState()

    val appStateStore = koinInject<AppStateStore>()
    val userState by appStateStore.userStateHolder.state
        .collectAsStateWithLifecycle()
    val glossaryState by appStateStore.glossaryStateHolder.state
        .collectAsStateWithLifecycle()

    var selectedPhrase by remember { mutableStateOf<PendingPhrase?>(null) }
    val snackBar = LocalSnackBarHostState.current
    val scope = rememberCoroutineScope()

    val (editedPhrases, newPhrases) = remember(model.pendingPhrases) {
        model.pendingPhrases.partition { it.original != null }
    }

    LaunchedEffect(glossaryState.glossary, userState.user) {
        glossaryState.glossary?.let { glossary ->
            component.loadPendingPhrases(glossary, false)
        }
    }

    LaunchedEffect(model.snackBarMessage) {
        model.snackBarMessage?.let { message ->
            scope.launch {
                component.clearSnackBarMessage()
                snackBar?.showSnackbar(message)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            PullToRefreshBox(
                isRefreshing = model.isRefreshing,
                onRefresh = {
                    glossaryState.glossary?.let { glossary ->
                        component.loadPendingPhrases(glossary, true)
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    TopDrawerBar(
                        title = stringResource(Res.string.review_changes),
                        onBackClick = component::navigateBack,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!model.isLoading) {
                        SettingsSection(
                            title = stringResource(Res.string.pending_phrases),
                            titleStyle = SettingsSectionDefaults.titleStyle(
                                color = Color.Unspecified,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                item {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(Res.string.edited_terms),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.W500,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = stringResource(
                                                Res.string.words_total,
                                                editedPhrases.size
                                            ),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.W500,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                items(editedPhrases, key = { it.phrase.id!! }) { pendingPhrase ->
                                    PendingPhrase(
                                        pendingPhrase = pendingPhrase,
                                        adminsCount = glossaryState.users.count {
                                            it.role == UserRole.OWNER || it.role == UserRole.ADMIN
                                        },
                                        onView = { selectedPhrase = pendingPhrase },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                item {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(Res.string.new_terms),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.W500,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = stringResource(
                                                Res.string.words_total,
                                                newPhrases.size
                                            ),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.W500,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                items(newPhrases, key = { it.phrase.id!! }) { pendingPhrase ->
                                    PendingPhrase(
                                        pendingPhrase = pendingPhrase,
                                        adminsCount = glossaryState.users.count {
                                            it.role == UserRole.OWNER || it.role == UserRole.ADMIN
                                        },
                                        onView = { selectedPhrase = pendingPhrase },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedPhrase?.let { pendingPhrase ->
        ReviewPendingPhraseBar(
            pendingPhrase,
            onSave = {
                component.saveReviewStatus(pendingPhrase, it)
            },
            onDismiss = {
                selectedPhrase = null
            }
        )
    }

    model.progress?.let { progress ->
        ProgressDialog(progress)
    }
}