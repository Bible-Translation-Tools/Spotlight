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
import org.bibletranslationtools.glossary.data.api.PendingPhrase
import org.bibletranslationtools.glossary.domain.GlossaryApi
import org.bibletranslationtools.glossary.domain.NetworkResult
import org.bibletranslationtools.glossary.logE
import org.bibletranslationtools.glossary.ui.drawer.DrawerComponent
import org.bibletranslationtools.glossary.ui.drawer.DrawerContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface SettingsListComponent : DrawerContext {
    val model: Value<Model>

    data class Model(
        val progress: Progress? = null,
        val snackBarMessage: String? = null,
        val pendingPhrases: List<PendingPhrase> = emptyList(),
        val pendingPhrasesLoading: Boolean = false
    )

    fun navigateLogin()
    fun logout()
    fun navigateChangeEmoji()
    fun createGlossary()
    fun viewGlossaries()
    fun editPermissions()
    fun clearSnackBarMessage()
    fun loadPendingPhrases(glossary: Glossary)
    fun reviewChanges()
    fun advancedSettings()
}

class DefaultSettingsListComponent(
    componentContext: ComponentContext,
    parentContext: DrawerContext,
    private val onCreateGlossary: () -> Unit,
    private val onViewGlossaries: () -> Unit,
    private val onNavigateLogin: () -> Unit,
    private val onNavigateChangeEmoji: () -> Unit,
    private val onLogout: () -> Unit,
    private val onEditPermissions: () -> Unit,
    private val onReviewChanges: () -> Unit,
    private val onAdvancedSettings: () -> Unit
) : DrawerComponent(componentContext, parentContext), SettingsListComponent, KoinComponent {

    private val glossaryApi: GlossaryApi by inject()

    private val componentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _model = MutableValue(SettingsListComponent.Model())
    override val model: Value<SettingsListComponent.Model> = _model

    init {
        doOnResume {
            setFullscreen(false)
        }
    }

    override fun navigateLogin() {
        onNavigateLogin()
    }

    override fun logout() {
        onLogout()
    }

    override fun navigateChangeEmoji() {
        onNavigateChangeEmoji()
    }

    override fun createGlossary() {
        onCreateGlossary()
    }

    override fun viewGlossaries() {
        onViewGlossaries()
    }

    override fun editPermissions() {
        onEditPermissions()
    }

    override fun loadPendingPhrases(glossary: Glossary) {
        componentScope.launch {
            val remoteId = glossary.remoteId ?: return@launch

            _model.update { it.copy(pendingPhrasesLoading = true) }
            val result = withContext(Dispatchers.Default) {
                glossaryApi.getPendingPhrases(remoteId)
            }
            when (result) {
                is NetworkResult.Success -> {
                    _model.update {
                        it.copy(pendingPhrases = result.data)
                    }
                }
                is NetworkResult.Error -> {
                    this@DefaultSettingsListComponent.logE("Load pending phrases failed: $result")
                }
            }
            _model.update { it.copy(pendingPhrasesLoading = false) }
        }
    }

    override fun clearSnackBarMessage() {
        _model.update { it.copy(snackBarMessage = null) }
    }

    override fun reviewChanges() {
        onReviewChanges()
    }

    override fun advancedSettings() {
        onAdvancedSettings()
    }
}