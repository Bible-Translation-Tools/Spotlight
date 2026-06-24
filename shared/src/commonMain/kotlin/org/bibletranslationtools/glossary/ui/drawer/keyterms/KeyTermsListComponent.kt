package org.bibletranslationtools.glossary.ui.drawer.keyterms

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.doOnResume
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bibletranslationtools.glossary.data.Glossary
import org.bibletranslationtools.glossary.data.Phrase
import org.bibletranslationtools.glossary.data.PhraseWorkflow
import org.bibletranslationtools.glossary.data.Progress
import org.bibletranslationtools.glossary.data.Resource
import org.bibletranslationtools.glossary.data.api.GlossaryUpdate
import org.bibletranslationtools.glossary.data.api.ReviewStatus
import org.bibletranslationtools.glossary.domain.FileSystemProvider
import org.bibletranslationtools.glossary.domain.GlossaryApi
import org.bibletranslationtools.glossary.domain.NetworkResult
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository
import org.bibletranslationtools.glossary.domain.usecases.ExportGlossary
import org.bibletranslationtools.glossary.domain.usecases.ImportGlossary
import org.bibletranslationtools.glossary.logE
import org.bibletranslationtools.glossary.toTimestamp
import org.bibletranslationtools.glossary.ui.components.UpdateStatus
import org.bibletranslationtools.glossary.ui.drawer.DrawerComponent
import org.bibletranslationtools.glossary.ui.drawer.DrawerContext
import org.bibletranslationtools.glossary.ui.main.MainStateKeeper
import org.bibletranslationtools.glossary.ui.main.SharedEvent
import org.bibletranslationtools.glossary.ui.state.AppStateStore
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.checking_for_updates
import spotlight.shared.generated.resources.clear_reviewed_failed
import spotlight.shared.generated.resources.clear_reviewed_success
import spotlight.shared.generated.resources.error_checking_updates
import spotlight.shared.generated.resources.glossary_upload_failed
import spotlight.shared.generated.resources.glossary_uploaded_successfully
import spotlight.shared.generated.resources.join_glossary_progress
import spotlight.shared.generated.resources.join_glossary_success
import spotlight.shared.generated.resources.no_updates_found
import spotlight.shared.generated.resources.updates_found
import spotlight.shared.generated.resources.upload_pending_failed
import spotlight.shared.generated.resources.upload_pending_success
import spotlight.shared.generated.resources.uploading_glossary

interface KeyTermsListComponent : DrawerContext {
    val model: Value<Model>

    data class Model(
        val isLoading: Boolean = false,
        val isRemoteLoading: Boolean = false,
        val phrases: List<Phrase> = emptyList(),
        val updateStatus: UpdateStatus = UpdateStatus.DEFAULT,
        val glossaryHasUpdate: Boolean = false,
        val snackBarMessage: String? = null,
        val progress: Progress? = null
    )

    fun navigateImportGlossary()
    fun navigateCreateGlossary()
    fun navigateSearchPhrases()
    fun uploadGlossary()
    fun uploadPendingPhrases()
    fun navigateViewPhrase(phrase: Phrase)
    fun downloadGlossary()
    fun joinGlossary()
    fun checkForUpdates()
    fun clearHasUpdate()
    fun clearReviewedPhrases()
    fun clearSnackBarMessage()
}

class DefaultKeyTermsListComponent(
    componentContext: ComponentContext,
    parentContext: DrawerContext,
    private val onNavigateImportGlossary: () -> Unit,
    private val onNavigateCreateGlossary: () -> Unit,
    private val onNavigateSearchPhrases: () -> Unit,
    private val onNavigateViewPhrase: (phrase: Phrase) -> Unit,
    private val onSelectGlossary: (glossary: Glossary, openKeyTerms: Boolean) -> Unit,
    private val onSelectResource: (resource: Resource) -> Unit,
    private val sharedState: MainStateKeeper
) : DrawerComponent(componentContext, parentContext), KeyTermsListComponent, KoinComponent {

    private val glossaryRepository: GlossaryRepository by inject()
    private val importGlossaryUseCase: ImportGlossary by inject()
    private val fileSystemProvider: FileSystemProvider by inject()
    private val glossaryApi: GlossaryApi by inject()
    private val exportGlossaryUseCase: ExportGlossary by inject()

    private val appState: AppStateStore by inject()
    private val glossaryStateHolder = appState.glossaryStateHolder
    private val glossaryState = glossaryStateHolder.state
    private val userState = appState.userStateHolder.state

    private val _model = MutableValue(KeyTermsListComponent.Model())
    override val model: Value<KeyTermsListComponent.Model> = _model

    private val componentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        doOnResume {
            setFullscreen(false)
        }

        componentScope.launch {
            glossaryState
                .map { it.glossary }
                .distinctUntilChanged()
                .collect { glossary ->
                    if (glossary != null) {
                        initialize()
                    }
                }
        }

        componentScope.launch {
            sharedState.event.collect { event ->
                when (event) {
                    SharedEvent.TriggerUpdate -> {
                        initialize()
                    }
                }
            }
        }
    }

    private fun initialize() {
        componentScope.launch {
            val glossary = glossaryState.value.glossary ?: return@launch

            _model.update { it.copy(isLoading = true) }

            val phrases = withContext(Dispatchers.IO) {
                val saved = glossaryRepository.getPhrases(glossary.id)
                val pending = glossaryRepository.getPendingPhrases(glossary.id)

                (saved + pending).associateBy { it.phrase }
                    .values
                    .toList()
                    .sortedBy { it.phrase.lowercase() }
            }

            _model.update {
                it.copy(
                    isLoading = false,
                    phrases = phrases
                )
            }

            loadPendingPhrases()
        }
    }

    override fun navigateImportGlossary() {
        onNavigateImportGlossary()
    }

    override fun navigateCreateGlossary() {
        onNavigateCreateGlossary()
    }

    override fun navigateSearchPhrases() {
        onNavigateSearchPhrases()
    }

    override fun uploadGlossary() {
        componentScope.launch {
            val glossary = glossaryState.value.glossary ?: return@launch

            val progress = Progress(
                value = -1f,
                message = getString(Res.string.uploading_glossary)
            )
            _model.update { it.copy(progress = progress) }

            val message = withContext(Dispatchers.Default) {
                val uploadPath = fileSystemProvider.createTempFile("upload", ".zip")
                val uploadFile = PlatformFile(uploadPath)

                exportGlossaryUseCase(glossary, uploadFile)

                if (uploadFile.exists() && uploadFile.size() > 0) {
                    val result = glossaryApi.uploadGlossary(uploadFile)
                    if (result is NetworkResult.Success) {
                        val newGlossary = glossary.copy(
                            remoteId = result.data.id,
                            version = result.data.version
                        )
                        glossaryRepository.addGlossary(newGlossary)
                        glossaryStateHolder.setGlossary(newGlossary)

                        getString(Res.string.glossary_uploaded_successfully)
                    } else {
                        this@DefaultKeyTermsListComponent.logE("Glossary upload failed: $result")
                        getString(Res.string.glossary_upload_failed)
                    }
                } else {
                    getString(Res.string.glossary_upload_failed)
                }
            }

            _model.update { it.copy(progress = null, snackBarMessage = message) }

            initialize()
        }
    }

    override fun uploadPendingPhrases() {
        componentScope.launch {
            val glossary = glossaryState.value.glossary ?: return@launch
            val glossaryRemoteId = glossary.remoteId ?: return@launch

            val progress = Progress(
                value = -1f,
                message = getString(Res.string.uploading_glossary)
            )
            _model.update { it.copy(progress = progress) }

            val message = withContext(Dispatchers.Default) {
                val result = glossaryApi.uploadPendingPhrases(
                    glossaryId = glossaryRemoteId,
                    phrases = _model.value.phrases.filter {
                        it.workflow == PhraseWorkflow.PENDING
                    }
                )
                if (result is NetworkResult.Success) {
                    glossaryRepository.deletePendingByGlossary(glossary.id!!)
                    getString(Res.string.upload_pending_success)
                } else {
                    this@DefaultKeyTermsListComponent.logE("Upload pending phrases failed: $result")
                    getString(Res.string.upload_pending_failed)
                }
            }

            _model.update {
                it.copy(
                    progress = null,
                    snackBarMessage = message
                )
            }

            initialize()
        }
    }

    override fun navigateViewPhrase(phrase: Phrase) {
        onNavigateViewPhrase(phrase)
    }

    override fun downloadGlossary() {
        componentScope.launch {
            val glossary = glossaryState.value.glossary ?: return@launch

            _model.update { it.copy(updateStatus = UpdateStatus.DOWNLOADING) }

            val result: ImportGlossary.Result? = withContext(Dispatchers.IO) {
                val result = glossaryApi.downloadGlossary(glossary.code)
                if (result is NetworkResult.Success) {
                    val target = fileSystemProvider.createTempFile("download", ".zip")
                    fileSystemProvider.writeFile(result.data, target)

                    if (fileSystemProvider.exists(target)) {
                        importGlossaryUseCase(PlatformFile(target))
                    } else null
                } else {
                    this@DefaultKeyTermsListComponent.logE("Download glossary failed: $result")
                    null
                }
            }

            result?.let { (glossary, resource) ->
                onSelectResource(resource)
                onSelectGlossary(glossary, false)
                sharedState.sendEvent(SharedEvent.TriggerUpdate)

                _model.update { it.copy(updateStatus = UpdateStatus.DOWNLOADED) }
            } ?: run {
                _model.update { it.copy(updateStatus = UpdateStatus.FAILED) }
            }
        }
    }

    override fun joinGlossary() {
        componentScope.launch {
            val glossaryRemoteId = glossaryState.value.glossary?.remoteId ?: return@launch
            val successMessage = getString(Res.string.join_glossary_success)
            val progressMessage = getString(Res.string.join_glossary_progress)

            _model.update { it.copy(progress = Progress(-1f, progressMessage)) }

            val users = withContext(Dispatchers.Default) {
                glossaryApi.joinGlossary(glossaryRemoteId).let { result ->
                    when (result) {
                        is NetworkResult.Success -> {
                            _model.update { it.copy(snackBarMessage = successMessage) }
                            result.data
                        }
                        is NetworkResult.Error -> {
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

    override fun checkForUpdates() {
        componentScope.launch {
            val glossary = glossaryState.value.glossary ?: return@launch
            val remoteId = glossary.remoteId ?: return@launch

            val progress = Progress(
                value = -1f,
                message = getString(Res.string.checking_for_updates)
            )
            _model.update { it.copy(progress = progress) }

            val result = with(Dispatchers.Default) {
                val glossaryUpdate = GlossaryUpdate(
                    id = remoteId,
                    version = glossary.version,
                    createdAt = glossary.createdAt.toTimestamp(),
                    updatedAt = glossary.updatedAt.toTimestamp()
                )

                val updates = glossaryApi.checkUpdates(listOf(glossaryUpdate))
                if (updates is NetworkResult.Success) {
                    if (updates.data.any { it.id == glossary.remoteId }) {
                        _model.update { it.copy(glossaryHasUpdate = true) }
                        getString(Res.string.updates_found)
                    } else {
                        getString(Res.string.no_updates_found)
                    }
                } else {
                    this@DefaultKeyTermsListComponent.logE(
                        "Check for updates failed: " +
                                "${(updates as NetworkResult.Error).message}"
                    )
                    getString(Res.string.error_checking_updates)
                }
            }

            _model.update { it.copy(progress = null, snackBarMessage = result) }
        }
    }

    override fun clearHasUpdate() {
        _model.update {
            it.copy(
                updateStatus = UpdateStatus.DEFAULT,
                glossaryHasUpdate = false
            )
        }
        initialize()
    }

    override fun clearReviewedPhrases() {
        componentScope.launch {
            val glossaryId = glossaryState.value.glossary?.remoteId ?: return@launch

            _model.update { it.copy(isLoading = true) }

            val message = withContext(Dispatchers.IO) {
                val result = glossaryApi.deleteReviewedPhrases(glossaryId)
                if (result is NetworkResult.Success) {
                    getString(Res.string.clear_reviewed_success)
                } else {
                    getString(Res.string.clear_reviewed_failed)
                }
            }

            _model.update { state ->
                state.copy(
                    snackBarMessage = message,
                    isLoading = false,
                    phrases = state.phrases.filter { it.workflow != PhraseWorkflow.REVIEWED }
                )
            }

            checkForUpdates()
        }
    }

    override fun clearSnackBarMessage() {
        _model.update { it.copy(snackBarMessage = null) }
    }

    private fun loadPendingPhrases() {
        componentScope.launch {
            val glossary = glossaryState.value.glossary ?: return@launch
            val user = userState.value.user

            _model.update { it.copy(isRemoteLoading = true) }

            val pendingPhrases = withContext(Dispatchers.IO) {
                val pending = glossaryRepository.getPendingPhrases(glossary.id)
                val remotePending = mutableListOf<Phrase>()
                val remoteReviewed = mutableListOf<Phrase>()

                glossary.remoteId?.let { glossaryRemoteId ->
                    val pendingResult = glossaryApi.getPendingPhrases(glossary.remoteId)
                    if (pendingResult is NetworkResult.Success) {
                        remotePending.addAll(
                            pendingResult.data
                                .filter { it.user.username == user?.username }
                                .map {
                                    it.phrase.copy(
                                        pending = true,
                                        status = ReviewStatus.UNREVIEWED
                                    )
                                }
                        )
                    }

                    val reviewedResult = glossaryApi.getReviewedPhrases(glossaryRemoteId)
                    if (reviewedResult is NetworkResult.Success) {
                        remoteReviewed.addAll(
                            reviewedResult.data.map {
                                it.phrase.copy(
                                    pending = true,
                                    status = it.status
                                )
                            }
                        )
                    }
                }

                (remoteReviewed + remotePending + pending).associateBy { it.phrase }
                    .values
                    .toList()
            }

            _model.update { state ->
                state.copy(
                    isRemoteLoading = false,
                    phrases = (state.phrases + pendingPhrases).associateBy { it.phrase }
                        .values
                        .toList()
                        .sortedBy { it.phrase.lowercase() }
                )
            }
        }
    }
}