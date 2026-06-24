package org.bibletranslationtools.glossary.ui.splash

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bibletranslationtools.glossary.domain.InitApp
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository
import org.bibletranslationtools.glossary.platform.ResourceContainerAccessor
import org.bibletranslationtools.glossary.ui.state.AppStateStore
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.loading_glossary
import spotlight.shared.generated.resources.loading_resources
import kotlin.time.Duration.Companion.milliseconds

interface SplashComponent {
    val model: Value<Model>

    fun initializeApp(resource: String, glossaryId: String?)

    data class Model(
        val message: String? = null
    )
}

class DefaultSplashComponent(
    componentContext: ComponentContext,
    private val onInitDone: () -> Unit
) : SplashComponent, KoinComponent, ComponentContext by componentContext {

    private val initAppUseCase: InitApp by inject()
    private val appStateStore: AppStateStore by inject()
    private val resourceContainerAccessor: ResourceContainerAccessor by inject()
    private val glossaryRepository: GlossaryRepository by inject()

    private val componentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _model = MutableValue(SplashComponent.Model())
    override val model: Value<SplashComponent.Model> = _model

    override fun initializeApp(resource: String, glossaryId: String?) {
        componentScope.launch {
            withContext(Dispatchers.IO) {
                initAppUseCase { message ->
                    _model.update { it.copy(message = message) }
                }
                loadResource(resource)
                loadGlossary(glossaryId)
            }

            _model.update { it.copy(message = null) }
            onInitDone()
        }
    }

    private suspend fun loadResource(resourceId: String) {
        _model.value = _model.value.copy(
            message = getString(Res.string.loading_resources)
        )

        delay(2000.milliseconds)

        withContext(Dispatchers.Default) {
            val (lang, type) = resourceId.split("_")
            var dbRes = glossaryRepository.getResource(lang, type)
            var filename = dbRes?.filename?.ifEmpty { null }

            if (filename == null) {
                // Default to English ULB
                dbRes = glossaryRepository.getResource("en", "ulb")
                filename = dbRes?.filename?.ifEmpty { null }
            }

            if (dbRes == null) {
                throw IllegalArgumentException("Resource ${dbRes.toString()} not found in database.")
            }

            if (filename == null) {
                throw IllegalArgumentException("Resource file not found.")
            }

            resourceContainerAccessor.read(filename)?.copy(id = dbRes.id, url = dbRes.url)
        }?.let { resource ->
            appStateStore.resourceStateHolder.setResource(resource)
        }
    }

    private suspend fun loadGlossary(glossaryId: String?) {
        _model.value = _model.value.copy(
            message = getString(Res.string.loading_glossary)
        )

        withContext(Dispatchers.Default) {
            glossaryId?.let { id ->
                glossaryRepository.getGlossary(id)?.let { glossary ->
                    appStateStore.glossaryStateHolder.setGlossary(glossary)
                }
            }
        }
    }
}