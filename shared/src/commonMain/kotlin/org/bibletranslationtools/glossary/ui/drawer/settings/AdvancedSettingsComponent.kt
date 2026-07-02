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
import org.bibletranslationtools.glossary.data.Progress
import org.bibletranslationtools.glossary.domain.usecases.UpdateCatalog
import org.bibletranslationtools.glossary.domain.usecases.UpdateLanguages
import org.bibletranslationtools.glossary.logE
import org.bibletranslationtools.glossary.ui.drawer.DrawerComponent
import org.bibletranslationtools.glossary.ui.drawer.DrawerContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.catalog_update_failed
import spotlight.shared.generated.resources.catalog_updated
import spotlight.shared.generated.resources.languages_update_failed
import spotlight.shared.generated.resources.languages_updated
import spotlight.shared.generated.resources.updating_catalog
import spotlight.shared.generated.resources.updating_languages

interface AdvancedSettingsComponent : DrawerContext {
    val model: Value<Model>

    data class Model(
        val progress: Progress? = null,
        val snackBarMessage: String? = null
    )

    fun downloadLanguages()
    fun importLanguages(file: PlatformFile)
    fun downloadCatalog()
    fun clearSnackBarMessage()
}

class DefaultAdvancedSettingsComponent(
    componentContext: ComponentContext,
    parentContext: DrawerContext
) : DrawerComponent(componentContext, parentContext), AdvancedSettingsComponent, KoinComponent {

    private val updateLanguages: UpdateLanguages by inject()
    private val updateCatalog: UpdateCatalog by inject()

    private val componentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _model = MutableValue(AdvancedSettingsComponent.Model())
    override val model: Value<AdvancedSettingsComponent.Model> = _model

    init {
        doOnResume {
            setFullscreen(false)
        }
    }

    override fun downloadLanguages() {
        runUpdate(
            progressMessage = Res.string.updating_languages,
            successMessage = Res.string.languages_updated,
            failureMessage = Res.string.languages_update_failed,
            logTag = "Update languages failed"
        ) { updateLanguages.fromUrl() }
    }

    override fun importLanguages(file: PlatformFile) {
        runUpdate(
            progressMessage = Res.string.updating_languages,
            successMessage = Res.string.languages_updated,
            failureMessage = Res.string.languages_update_failed,
            logTag = "Update languages failed"
        ) { updateLanguages.fromFile(file) }
    }

    override fun downloadCatalog() {
        runUpdate(
            progressMessage = Res.string.updating_catalog,
            successMessage = Res.string.catalog_updated,
            failureMessage = Res.string.catalog_update_failed,
            logTag = "Update catalog failed"
        ) { updateCatalog.fromUrl() }
    }

    private fun runUpdate(
        progressMessage: StringResource,
        successMessage: StringResource,
        failureMessage: StringResource,
        logTag: String,
        block: suspend () -> Int
    ) {
        componentScope.launch {
            val progress = Progress(
                value = -1f,
                message = getString(progressMessage)
            )
            _model.update { it.copy(progress = progress) }

            val message = try {
                val count = withContext(Dispatchers.IO) { block() }
                getString(successMessage, count)
            } catch (e: Exception) {
                this@DefaultAdvancedSettingsComponent.logE(logTag, e)
                getString(failureMessage)
            }

            _model.update {
                it.copy(progress = null, snackBarMessage = message)
            }
        }
    }

    override fun clearSnackBarMessage() {
        _model.update { it.copy(snackBarMessage = null) }
    }
}
