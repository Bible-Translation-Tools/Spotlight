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
import org.bibletranslationtools.glossary.data.Progress
import org.bibletranslationtools.glossary.data.api.User
import org.bibletranslationtools.glossary.domain.GlossaryApi
import org.bibletranslationtools.glossary.domain.NetworkResult
import org.bibletranslationtools.glossary.ui.drawer.DrawerComponent
import org.bibletranslationtools.glossary.ui.drawer.DrawerContext
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.login_progress
import spotlight.shared.generated.resources.login_success

interface LoginComponent : DrawerContext {
    val model: Value<Model>

    data class Model(
        val progress: Progress? = null,
        val snackBarMessage: String? = null
    )

    fun login(username: String, password: String)
    fun clearSnackBarMessage()
}

class DefaultLoginComponent(
    componentContext: ComponentContext,
    parentContext: DrawerContext,
    private val onUserUpdated: (User) -> Unit,
) : DrawerComponent(componentContext, parentContext), LoginComponent, KoinComponent {

    private val glossaryApi: GlossaryApi by inject()

    private val _model = MutableValue(LoginComponent.Model())
    override val model: Value<LoginComponent.Model> = _model

    private val componentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        doOnResume {
            setFullscreen(true)
        }
    }

    override fun login(username: String, password: String) {
        componentScope.launch {
            val loginProgress = getString(Res.string.login_progress)
            val success = getString(Res.string.login_success)

            _model.update {
                it.copy(
                    progress = Progress(value = -1f, message = loginProgress)
                )
            }

            withContext(Dispatchers.Default) {
                glossaryApi.login(username, password).let { result ->
                    when (result) {
                        is NetworkResult.Success -> {
                            onUserUpdated(result.data)
                            navigateBack()
                            _model.update {
                                it.copy(snackBarMessage = success)
                            }
                        }
                        is NetworkResult.Error -> {
                            _model.update {
                                it.copy(snackBarMessage = result.message.error)
                            }
                        }
                    }
                }
            }

            _model.update { it.copy(progress = null) }
        }
    }

    override fun clearSnackBarMessage() {
        _model.update { it.copy(snackBarMessage = null) }
    }
}