package org.bibletranslationtools.glossary.ui.drawer.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.launch
import org.bibletranslationtools.glossary.data.api.GlossaryUser
import org.bibletranslationtools.glossary.ui.components.EditPermissionsBar
import org.bibletranslationtools.glossary.ui.components.GlossaryUser
import org.bibletranslationtools.glossary.ui.components.SettingsSection
import org.bibletranslationtools.glossary.ui.components.TopDrawerBar
import org.bibletranslationtools.glossary.ui.dialogs.ProgressDialog
import org.bibletranslationtools.glossary.ui.navigation.LocalSnackBarHostState
import org.bibletranslationtools.glossary.ui.state.AppStateStore
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.active_users
import spotlight.shared.generated.resources.edit_permissions

@Composable
fun EditPermissionsScreen(component: EditPermissionsComponent) {
    val model by component.model.subscribeAsState()

    val appStateStore = koinInject<AppStateStore>()
    val userState by appStateStore.userStateHolder.state
        .collectAsStateWithLifecycle()
    val glossaryState by appStateStore.glossaryStateHolder.state
        .collectAsStateWithLifecycle()

    var selectedUser by remember { mutableStateOf<GlossaryUser?>(null) }
    val snackBar = LocalSnackBarHostState.current
    val scope = rememberCoroutineScope()

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
                        component.loadGlossaryUsers(glossary)
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
                        title = stringResource(Res.string.edit_permissions),
                        onBackClick = component::navigateBack,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsSection(
                        title = stringResource(Res.string.active_users),
                        modifier = Modifier.weight(1f)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.medium
                                )
                        ) {
                            itemsIndexed(glossaryState.users) { index, glossaryUser ->
                                GlossaryUser(
                                    glossaryUser = glossaryUser,
                                    isMe = glossaryUser.user.username == userState.user?.username,
                                    onEdit = {
                                        selectedUser = glossaryUser
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (index < glossaryState.users.lastIndex) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedUser?.let { user ->
        EditPermissionsBar(
            user,
            onSave = { role ->
                if (user.role != role) {
                    glossaryState.glossary?.let { glossary ->
                        component.updateUserRole(glossary, user, role)
                    }
                }
            },
            onDismiss = {
                selectedUser = null
            }
        )
    }

    model.progress?.let { progress ->
        ProgressDialog(progress)
    }
}