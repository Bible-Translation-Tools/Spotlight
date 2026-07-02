package org.bibletranslationtools.glossary.ui.drawer.keyterms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import dev.burnoo.compose.remembersetting.rememberIntSetting
import dev.burnoo.compose.remembersetting.rememberStringSetting
import kotlinx.coroutines.launch
import org.bibletranslationtools.glossary.data.PhraseWorkflow
import org.bibletranslationtools.glossary.data.api.UserRole
import org.bibletranslationtools.glossary.domain.Settings
import org.bibletranslationtools.glossary.ui.components.GlossaryUpdate
import org.bibletranslationtools.glossary.ui.components.PhraseItem
import org.bibletranslationtools.glossary.ui.components.SearchField
import org.bibletranslationtools.glossary.ui.components.TopDrawerBar
import org.bibletranslationtools.glossary.ui.components.UpdateStatus
import org.bibletranslationtools.glossary.ui.dialogs.ProgressDialog
import org.bibletranslationtools.glossary.ui.navigation.LocalSnackBarHostState
import org.bibletranslationtools.glossary.ui.state.AppStateStore
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.add_glossary
import spotlight.shared.generated.resources.add_glossary_key_terms
import spotlight.shared.generated.resources.check_updates
import spotlight.shared.generated.resources.clear
import spotlight.shared.generated.resources.create_glossary
import spotlight.shared.generated.resources.create_new_phrase
import spotlight.shared.generated.resources.glossary_code
import spotlight.shared.generated.resources.join_glossary
import spotlight.shared.generated.resources.key_terms
import spotlight.shared.generated.resources.key_terms_unavailable
import spotlight.shared.generated.resources.no_phrases_found
import spotlight.shared.generated.resources.pending_review
import spotlight.shared.generated.resources.pending_submission
import spotlight.shared.generated.resources.reviewed
import spotlight.shared.generated.resources.search
import spotlight.shared.generated.resources.upload_glossary
import spotlight.shared.generated.resources.upload_phrases

@Composable
fun KeyTermsListScreen(component: KeyTermsListComponent) {
    val model by component.model.subscribeAsState()

    val appStateStore = koinInject<AppStateStore>()
    val glossaryState by appStateStore.glossaryStateHolder.state
        .collectAsStateWithLifecycle()
    val userState by appStateStore.userStateHolder.state
        .collectAsStateWithLifecycle()

    val hasPendingPhrases by remember(model.phrases) {
        mutableStateOf(model.phrases.any { it.workflow == PhraseWorkflow.PENDING })
    }
    var filteredPhrases by remember {
        mutableStateOf(model.phrases)
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    var activeBookSlug by rememberStringSetting(
        Settings.BOOK,
        "mat"
    )
    var activeChapterNum by rememberIntSetting(
        Settings.CHAPTER,
        1
    )

    var glossaryUpdateStatus by remember(model.updateStatus) {
        mutableStateOf(model.updateStatus)
    }

    val coroutineScope = rememberCoroutineScope()
    val snackBar = LocalSnackBarHostState.current

    var canJoin by remember { mutableStateOf(false) }
    var joined by remember { mutableStateOf(false) }
    var isAdmin by remember { mutableStateOf(false) }
    var canEdit by remember { mutableStateOf(false) }
    var isGlossaryPublished by remember { mutableStateOf(false) }

    LaunchedEffect(glossaryState.glossary) {
        glossaryState.glossary?.let { glossary ->
            isGlossaryPublished = !glossary.remoteId.isNullOrBlank()
        }
    }

    LaunchedEffect(
        glossaryState.users,
        userState.user,
        isGlossaryPublished
    ) {
        userState.user?.let { glossaryUser ->
            joined = glossaryState.users
                .map { it.user.username }
                .contains(glossaryUser.username)

            canJoin = !joined && isGlossaryPublished

            canEdit = glossaryState.users
                .filter { it.role != UserRole.VIEWER }
                .map { it.user.username }
                .contains(glossaryUser.username) || !isGlossaryPublished

            isAdmin = glossaryState.users
                .filter { it.role == UserRole.OWNER || it.role == UserRole.ADMIN }
                .map { it.user.username }
                .contains(glossaryUser.username) || !isGlossaryPublished
        } ?: run {
            // Allow to edit when in "offline" mode
            canEdit = true
        }
    }

    LaunchedEffect(searchQuery, model.phrases) {
        filteredPhrases = model.phrases.filter { phrase ->
            phrase.phrase.contains(searchQuery, ignoreCase = true)
                    || phrase.spelling.contains(searchQuery, ignoreCase = true)
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
        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    TopDrawerBar(
                        title = stringResource(Res.string.key_terms),
                        subTitle = glossaryState.glossary?.let {
                            stringResource(
                                Res.string.glossary_code,
                                it.code
                            )
                        },
                        onDismiss = component::dismiss,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (canJoin) {
                        Text(
                            text = stringResource(Res.string.join_glossary),
                            textDecoration = TextDecoration.Underline,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 16.sp,
                            modifier = Modifier.clickable {
                                component.joinGlossary()
                            }
                        )
                    }

                    glossaryState.glossary?.let {
                        Spacer(modifier = Modifier.height(32.dp))

                        SearchField(
                            searchQuery = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = stringResource(Res.string.search),
                                    color = MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = 0.5f
                                    )
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.onBackground
                                    .copy(alpha = 0.1f),
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (model.isLoading) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth()
                                    .weight(1f)
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            if (filteredPhrases.isEmpty()) {
                                Text(text = stringResource(Res.string.no_phrases_found))
                            }

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (model.isRemoteLoading) {
                                    item {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(stringResource(Res.string.pending_review))
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }

                                // Pending submission
                                val pending = filteredPhrases.filter {
                                    it.workflow == PhraseWorkflow.PENDING
                                }
                                if (pending.isNotEmpty()) {
                                    item {
                                        Text(stringResource(Res.string.pending_submission))
                                    }
                                    items(pending) { phrase ->
                                        PhraseItem(
                                            phrase = phrase,
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                component.navigateViewPhrase(phrase)
                                            }
                                        )
                                    }
                                    item {
                                        HorizontalDivider()
                                    }
                                }

                                // Pending review
                                val inReview = filteredPhrases.filter {
                                    it.workflow == PhraseWorkflow.IN_REVIEW
                                }
                                if (inReview.isNotEmpty()) {
                                    item {
                                        Text(stringResource(Res.string.pending_review))
                                    }
                                    items(inReview) { phrase ->
                                        PhraseItem(
                                            phrase = phrase,
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                component.navigateViewPhrase(phrase)
                                            }
                                        )
                                    }
                                    item {
                                        HorizontalDivider()
                                    }
                                }

                                // Reviewed
                                val reviewed = filteredPhrases.filter {
                                    it.workflow == PhraseWorkflow.REVIEWED
                                }
                                if (reviewed.isNotEmpty()) {
                                    item {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(stringResource(Res.string.reviewed))
                                            TextButton(
                                                onClick = component::clearReviewedPhrases
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Delete,
                                                    contentDescription = "clear reviewed items",
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = stringResource(Res.string.clear),
                                                    fontSize = 16.sp
                                                )
                                            }
                                        }
                                    }
                                    items(reviewed) { phrase ->
                                        PhraseItem(
                                            phrase = phrase,
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                component.navigateViewPhrase(phrase)
                                            }
                                        )
                                    }
                                    item {
                                        HorizontalDivider()
                                    }
                                }

                                // Local items
                                val saved = filteredPhrases.filter {
                                    it.workflow == PhraseWorkflow.SAVED
                                }
                                items(saved) { phrase ->
                                    PhraseItem(
                                        phrase = phrase,
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            component.navigateViewPhrase(phrase)
                                        }
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                )

                                if (model.glossaryHasUpdate
                                    || glossaryUpdateStatus != UpdateStatus.DEFAULT
                                ) {
                                    GlossaryUpdate(
                                        status = glossaryUpdateStatus,
                                        onDownload = component::downloadGlossary,
                                        onDismiss = component::clearHasUpdate
                                    )
                                } else {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        if (canEdit) {
                                            Button(
                                                onClick = component::navigateSearchPhrases,
                                                shape = MaterialTheme.shapes.medium,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (hasPendingPhrases) {
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    } else MaterialTheme.colorScheme.primary,
                                                    contentColor = if (hasPendingPhrases) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else MaterialTheme.colorScheme.onPrimary
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "add new word"
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = stringResource(
                                                        Res.string.create_new_phrase
                                                    ),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            when {
                                                isAdmin && !isGlossaryPublished -> {
                                                    // Upload glossary
                                                    Button(
                                                        onClick = component::uploadGlossary,
                                                        shape = MaterialTheme.shapes.medium,
                                                        modifier = Modifier.fillMaxWidth()
                                                            .height(40.dp)
                                                    ) {
                                                        Text(
                                                            text = stringResource(
                                                                Res.string.upload_glossary
                                                            )
                                                        )
                                                    }
                                                }

                                                userState.user != null && canEdit -> {
                                                    // Upload pending phrases
                                                    Button(
                                                        onClick = component::uploadPendingPhrases,
                                                        shape = MaterialTheme.shapes.medium,
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (hasPendingPhrases) {
                                                                MaterialTheme.colorScheme.primary
                                                            } else MaterialTheme.colorScheme.primaryContainer,
                                                            contentColor = if (hasPendingPhrases) {
                                                                MaterialTheme.colorScheme.primaryContainer
                                                            } else MaterialTheme.colorScheme.onPrimaryContainer
                                                        ),
                                                        modifier = Modifier.fillMaxWidth()
                                                            .height(40.dp)
                                                    ) {
                                                        Text(
                                                            text = stringResource(
                                                                Res.string.upload_phrases
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Allow to check for updates if glossary is published
                                        if (isGlossaryPublished) {
                                            TextButton(
                                                onClick = component::checkForUpdates,
                                                shape = MaterialTheme.shapes.medium,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = stringResource(
                                                        Res.string.check_updates
                                                    ),
                                                    fontWeight = FontWeight.Bold,
                                                    textDecoration = TextDecoration.Underline
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } ?: run {
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
                                text = stringResource(Res.string.key_terms_unavailable),
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            )

                            Text(
                                text = stringResource(Res.string.add_glossary_key_terms),
                                fontSize = 16.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = component::navigateImportGlossary,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text(stringResource(Res.string.add_glossary))
                            }

                            ElevatedButton(
                                onClick = component::navigateCreateGlossary,
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text(stringResource(Res.string.create_glossary))
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