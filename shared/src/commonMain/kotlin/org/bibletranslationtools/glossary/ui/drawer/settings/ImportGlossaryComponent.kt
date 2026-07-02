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
import org.bibletranslationtools.glossary.domain.FileSystemProvider
import org.bibletranslationtools.glossary.domain.GlossaryApi
import org.bibletranslationtools.glossary.domain.NetworkResult
import org.bibletranslationtools.glossary.domain.usecases.ImportGlossary
import org.bibletranslationtools.glossary.logE
import org.bibletranslationtools.glossary.ui.components.OtpAction
import org.bibletranslationtools.glossary.ui.drawer.DrawerComponent
import org.bibletranslationtools.glossary.ui.drawer.DrawerContext
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.downloading_glossary
import spotlight.shared.generated.resources.importing_glossary

interface ImportGlossaryComponent : DrawerContext {
    val model: Value<Model>

    data class Model(
        val isLoading: Boolean = false,
        val otpCode: List<String?> = (1..5).map { null },
        val focusedIndex: Int? = null,
        val autoImportManually: Boolean = false,
        val progress: Progress? = null,
        val error: String? = null
    )

    fun onOtpAction(action: OtpAction)
    fun onDownloadClicked()
    fun onImportClicked(file: PlatformFile)
}

class DefaultImportGlossaryComponent(
    componentContext: ComponentContext,
    parentContext: DrawerContext,
    private val autoImportManually: Boolean,
    private val onSelectGlossary: (glossary: Glossary, openKeyTerms: Boolean) -> Unit,
    private val onSelectResource: (resource: Resource) -> Unit,
    private val onImportFinished: () -> Unit
) : DrawerComponent(componentContext, parentContext), ImportGlossaryComponent, KoinComponent {

    private val importGlossaryUseCase: ImportGlossary by inject()
    private val glossaryApi: GlossaryApi by inject()
    private val fileSystemProvider: FileSystemProvider by inject()

    private val _model = MutableValue(ImportGlossaryComponent.Model())
    override val model: Value<ImportGlossaryComponent.Model> = _model

    private val componentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        doOnResume {
            setFullscreen(true)
            _model.update {
                it.copy(
                    error = null,
                    autoImportManually = autoImportManually
                )
            }
        }
    }

    override fun onOtpAction(action: OtpAction) {
        val currentModel = _model.value
        val currentFocusIndex = currentModel.focusedIndex ?: 0

        when (action) {
            is OtpAction.OnChangeFieldFocused -> {
                _model.update { it.copy(focusedIndex = action.index) }
            }

            is OtpAction.OnEnterChar -> {
                val newOtpCode = currentModel.otpCode.toMutableList()
                newOtpCode[action.index] = action.char?.takeIf { it.isNotEmpty() }

                val nextFocusIndex =
                    if (action.char?.isNotEmpty() == true && action.index < newOtpCode.lastIndex) {
                        action.index + 1
                    } else {
                        action.index
                    }

                _model.update {
                    it.copy(
                        otpCode = newOtpCode,
                        focusedIndex = nextFocusIndex
                    )
                }
            }

            OtpAction.OnKeyboardBack -> {
                val codeAtIndex = currentModel.otpCode.getOrNull(currentFocusIndex)
                val newOtpCode = currentModel.otpCode.toMutableList()
                var previousFocusIndex = currentFocusIndex

                if (codeAtIndex != null) {
                    newOtpCode[currentFocusIndex] = null
                } else if (currentFocusIndex > 0) {
                    previousFocusIndex = currentFocusIndex - 1
                    newOtpCode[previousFocusIndex] = null
                }

                _model.update {
                    it.copy(
                        otpCode = newOtpCode,
                        focusedIndex = previousFocusIndex
                    )
                }
            }
        }
    }

    override fun onDownloadClicked() {
        componentScope.launch {
            if (model.value.otpCode.none { it == null }) {
                val progress = Progress(
                    value = -1f,
                    message = getString(Res.string.downloading_glossary)
                )
                _model.update {
                    it.copy(progress = progress, error = null)
                }

                val code = model.value.otpCode.joinToString("")

                val result: ImportGlossary.Result? = withContext(Dispatchers.IO) {
                    val result = glossaryApi.downloadGlossary(code)
                    if (result is NetworkResult.Success) {
                        val target = fileSystemProvider.createTempFile("download", ".zip")
                        fileSystemProvider.writeFile(result.data, target)

                        if (fileSystemProvider.exists(target)) {
                            importGlossaryUseCase(PlatformFile(target))
                        } else null
                    } else {
                        _model.update { it.copy(error = result.toString()) }
                        this@DefaultImportGlossaryComponent.logE("Download glossary failed: $result")
                        null
                    }
                }

                result?.let {
                    onSelectResource(it.resource)
                    onSelectGlossary(it.glossary, true)
                    onImportFinished()
                }

                _model.update { it.copy(progress = null) }
            }
        }
    }

    override fun onImportClicked(file: PlatformFile) {
        componentScope.launch {
            val progress = Progress(
                value = -1f,
                message = getString(Res.string.importing_glossary)
            )
            _model.update { it.copy(progress = progress) }

            val result = withContext(Dispatchers.Default) {
                importGlossaryUseCase(file)
            }

            onSelectResource(result.resource)
            onSelectGlossary(result.glossary, true)

            _model.update { it.copy(progress = null) }

            onImportFinished()
        }
    }
}