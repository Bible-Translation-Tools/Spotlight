package org.bibletranslationtools.glossary.ui.drawer.settings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.doOnResume
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bibletranslationtools.glossary.data.Glossary
import org.bibletranslationtools.glossary.data.Progress
import org.bibletranslationtools.glossary.data.Resource
import org.bibletranslationtools.glossary.domain.GlossaryApi
import org.bibletranslationtools.glossary.domain.NetworkResult
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository
import org.bibletranslationtools.glossary.domain.usecases.ExportGlossary
import org.bibletranslationtools.glossary.platform.ResourceContainerAccessor
import org.bibletranslationtools.glossary.ui.drawer.DrawerComponent
import org.bibletranslationtools.glossary.ui.drawer.DrawerContext
import org.bibletranslationtools.glossary.ui.state.AppStateStore
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.exporting_glossary
import spotlight.shared.generated.resources.glossary_exported_successfully

data class GlossaryItem(
    val glossary: Glossary,
    val phraseCount: suspend () -> Int,
    val userCount: suspend () -> Int
)

interface GlossaryListComponent : DrawerContext {
    val model: Value<Model>

    data class Model(
        val isLoading: Boolean = false,
        val activeGlossary: GlossaryItem? = null,
        val selectedGlossary: GlossaryItem? = null,
        val selectedResource: Resource? = null,
        val glossaries: List<GlossaryItem> = emptyList(),
        val snackBarMessage: String? = null,
        val progress: Progress? = null
    )
    fun selectGlossary(glossary: GlossaryItem)
    fun navigateImportGlossary()
    fun navigateCreateGlossary()
    fun navigateImportManually()
    fun saveGlossary()
    fun exportGlossary(file: PlatformFile)
    fun clearSnackBarMessage()
}

class DefaultGlossaryListComponent(
    componentContext: ComponentContext,
    parentContext: DrawerContext,
    private val onNavigateImportGlossary: () -> Unit,
    private val onNavigateCreateGlossary: () -> Unit,
    private val onSelectGlossary: (glossary: Glossary, openKeyTerms: Boolean) -> Unit,
    private val onSelectResource: (resource: Resource) -> Unit,
    private val onImportManually: () -> Unit
) : DrawerComponent(componentContext, parentContext), GlossaryListComponent, KoinComponent {

    private val appStateStore: AppStateStore by inject()
    private val glossaryRepository: GlossaryRepository by inject()
    private val resourceContainerAccessor: ResourceContainerAccessor by inject()
    private val exportGlossaryUseCase: ExportGlossary by inject()
    private val glossaryApi: GlossaryApi by inject()

    private val glossaryState = appStateStore.glossaryStateHolder.state
    private val componentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _model = MutableValue(GlossaryListComponent.Model())
    override val model: Value<GlossaryListComponent.Model> = _model

    init {
        doOnResume {
            setFullscreen(true)
            loadGlossaries()
            refreshSelectedGlossary()
        }
    }

    override fun selectGlossary(glossary: GlossaryItem) {
        componentScope.launch {
            _model.value = _model.value.copy(isLoading = true)

            withContext(Dispatchers.Default) {
                glossaryRepository.getResource(glossary.glossary.resourceId!!)?.let { dbRes ->
                    val resource = resourceContainerAccessor.read(dbRes.filename)
                        ?.copy(id = dbRes.id, url = dbRes.url)

                    _model.update {
                        it.copy(
                            isLoading = false,
                            selectedGlossary = glossary,
                            selectedResource = resource
                        )
                    }
                }
            }
        }
    }

    override fun navigateImportGlossary() {
        onNavigateImportGlossary()
    }

    override fun navigateCreateGlossary() {
        onNavigateCreateGlossary()
    }

    override fun navigateImportManually() {
        onImportManually()
    }

    override fun saveGlossary() {
        componentScope.launch {
            val glossary = _model.value.selectedGlossary?.glossary ?: return@launch
            val resource = _model.value.selectedResource ?: return@launch

            onSelectResource(resource)
            onSelectGlossary(glossary, true)
        }
    }

    override fun exportGlossary(file: PlatformFile) {
        componentScope.launch {
            val glossary = _model.value.selectedGlossary?.glossary ?: return@launch

            val progress = Progress(
                value = -1f,
                message = getString(Res.string.exporting_glossary)
            )
            _model.update { it.copy(progress = progress) }

            withContext(Dispatchers.Default) {
                exportGlossaryUseCase(glossary, file)
            }

            val message = getString(Res.string.glossary_exported_successfully)

            _model.update { it.copy(progress = null, snackBarMessage = message) }
        }
    }

    override fun clearSnackBarMessage() {
        _model.update { it.copy(snackBarMessage = null) }
    }

    private fun loadGlossaries() {
        componentScope.launch {
            _model.update { it.copy(isLoading = true) }

            val glossaryItems = withContext(Dispatchers.Default) {
                val glossaries = glossaryRepository.getGlossaries()

                glossaries.map { glossary ->
                    GlossaryItem(
                        glossary = glossary,
                        phraseCount = {
                            glossaryRepository.getPhrases(glossary.id).size
                        },
                        userCount = {
                            val usersResult = glossary.remoteId?.let {
                                glossaryApi.getGlossaryUsers(it)
                            }
                            when (usersResult) {
                                is NetworkResult.Success -> usersResult.data.size
                                else -> 0
                            }
                        }
                    )
                }
            }

            _model.update {
                it.copy(
                    isLoading = false,
                    glossaries = glossaryItems
                )
            }

            refreshSelectedGlossary()
        }
    }

    private fun refreshSelectedGlossary() {
        val activeGlossary = _model.value.glossaries.singleOrNull {
            it.glossary.id == glossaryState.value.glossary?.id
        }
        val selectedGlossary = _model.value.selectedGlossary ?: activeGlossary

        _model.update {
            it.copy(
                activeGlossary = activeGlossary,
                selectedGlossary = selectedGlossary
            )
        }
    }
}