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
import org.bibletranslationtools.glossary.logE
import org.bibletranslationtools.glossary.ui.drawer.DrawerComponent
import org.bibletranslationtools.glossary.ui.drawer.DrawerContext
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.updating_emoji

interface ChangeEmojiComponent : DrawerContext {
    val model: Value<Model>

    data class Model(
        val progress: Progress? = null,
        val snackBarMessage: String? = null
    )

    fun changeEmoji(emoji: String)
    fun clearSnackBarMessage()
}

class DefaultChangeEmojiComponent(
    componentContext: ComponentContext,
    parentContext: DrawerContext,
    private val onUserUpdated: (User) -> Unit,
) : DrawerComponent(componentContext, parentContext), ChangeEmojiComponent, KoinComponent {

    private val glossaryApi: GlossaryApi by inject()

    private val _model = MutableValue(ChangeEmojiComponent.Model())
    override val model: Value<ChangeEmojiComponent.Model> = _model

    private val componentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        doOnResume {
            setFullscreen(true)
        }
    }

    override fun changeEmoji(emoji: String) {
        componentScope.launch {
            val progress = Progress(
                value = -1f,
                message = getString(Res.string.updating_emoji)
            )
            _model.update { it.copy(progress = progress) }

            val result = withContext(Dispatchers.Default) {
                glossaryApi.updateEmoji(emoji)
            }
            when (result) {
                is NetworkResult.Success -> {
                    onUserUpdated(result.data)
                    navigateBack()
                }
                is NetworkResult.Error -> {
                    this@DefaultChangeEmojiComponent.logE("Change emoji failed: ${result.message}")
                    _model.update { it.copy(snackBarMessage = result.message.error) }
                }
            }

            _model.update { it.copy(progress = null) }
        }
    }

    override fun clearSnackBarMessage() {
        _model.update { it.copy(snackBarMessage = null) }
    }
}