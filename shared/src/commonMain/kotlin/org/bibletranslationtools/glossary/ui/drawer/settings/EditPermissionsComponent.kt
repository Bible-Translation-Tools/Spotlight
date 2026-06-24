package org.bibletranslationtools.glossary.ui.drawer.settings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.doOnResume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bibletranslationtools.glossary.data.Glossary
import org.bibletranslationtools.glossary.data.Progress
import org.bibletranslationtools.glossary.data.api.GlossaryUser
import org.bibletranslationtools.glossary.data.api.UserRole
import org.bibletranslationtools.glossary.domain.GlossaryApi
import org.bibletranslationtools.glossary.domain.NetworkResult
import org.bibletranslationtools.glossary.logE
import org.bibletranslationtools.glossary.ui.drawer.DrawerComponent
import org.bibletranslationtools.glossary.ui.drawer.DrawerContext
import org.bibletranslationtools.glossary.ui.state.AppStateStore
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.update_role_success
import spotlight.shared.generated.resources.updating_role

interface EditPermissionsComponent : DrawerContext {
    val model: Value<Model>

    data class Model(
        val isRefreshing: Boolean = false,
        val progress: Progress? = null,
        val snackBarMessage: String? = null
    )

    fun updateUserRole(glossary: Glossary, glossaryUser: GlossaryUser, role: UserRole)
    fun loadGlossaryUsers(glossary: Glossary)
    fun clearSnackBarMessage()
}

class DefaultEditPermissionsComponent(
    componentContext: ComponentContext,
    parentContext: DrawerContext,
) : DrawerComponent(componentContext, parentContext), EditPermissionsComponent, KoinComponent {

    private val glossaryApi: GlossaryApi by inject()
    private val appStateStore: AppStateStore by inject()
    private val glossaryStateHolder = appStateStore.glossaryStateHolder

    private val _model = MutableValue(EditPermissionsComponent.Model())
    override val model: Value<EditPermissionsComponent.Model> = _model

    private val componentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        doOnResume {
            setFullscreen(true)
        }
    }

    override fun updateUserRole(glossary: Glossary, glossaryUser: GlossaryUser, role: UserRole) {
        componentScope.launch {
            val glossaryRemoteId = glossary.remoteId ?: return@launch

            val progress = Progress(
                value = -1f,
                message = getString(Res.string.updating_role)
            )
            _model.update { it.copy(progress = progress) }
            val successMessage = getString(Res.string.update_role_success)

            val users = withContext(Dispatchers.Default) {
                glossaryApi.updateUserRole(
                    glossaryRemoteId,
                    glossaryUser.user.username,
                    role
                ).let { result ->
                    when (result) {
                        is NetworkResult.Success -> {
                            _model.update { it.copy(snackBarMessage = successMessage) }
                            result.data
                        }
                        is NetworkResult.Error -> {
                            this@DefaultEditPermissionsComponent.logE("Edit permissions failed: ${result.message.details}")
                            _model.update { it.copy(snackBarMessage = result.message.error) }
                            emptyList()
                        }
                    }
                }
            }
            glossaryStateHolder.setUsers(users)

            _model.update { it.copy(progress = null) }
        }
    }

    override fun loadGlossaryUsers(glossary: Glossary) {
        componentScope.launch {
            val glossaryRemoteId = glossary.remoteId ?: return@launch

            _model.update { it.copy(isRefreshing = true) }
            val result = withContext(Dispatchers.Default) {
                glossaryApi.getGlossaryUsers(glossaryRemoteId)
            }
            when (result) {
                is NetworkResult.Success -> {
                    glossaryStateHolder.setUsers(result.data)
                }
                is NetworkResult.Error -> {
                    this@DefaultEditPermissionsComponent.logE("Load glossary users failed: ${result.message}")
                }
            }
            _model.update { it.copy(isRefreshing = false) }
        }
    }

    override fun clearSnackBarMessage() {
        _model.update { it.copy(snackBarMessage = null) }
    }
}