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
import org.bibletranslationtools.glossary.data.api.PhraseReview
import org.bibletranslationtools.glossary.data.api.ReviewStatus
import org.bibletranslationtools.glossary.data.api.User
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
import spotlight.shared.generated.resources.loading_wait
import spotlight.shared.generated.resources.sending_review

interface ReviewChangesComponent : DrawerContext {
    val model: Value<Model>

    data class Model(
        val pendingPhrases: List<PendingPhrase> = emptyList(),
        val isRefreshing: Boolean = false,
        val isLoading: Boolean = false,
        val progress: Progress? = null,
        val snackBarMessage: String? = null
    )

    fun loadPendingPhrases(glossary: Glossary, isRefreshing: Boolean)
    fun saveReviewStatus(phrase: PendingPhrase, status: ReviewStatus)
    fun clearSnackBarMessage()
}

class DefaultReviewChangesComponent(
    componentContext: ComponentContext,
    parentContext: DrawerContext,
) : DrawerComponent(componentContext, parentContext), ReviewChangesComponent, KoinComponent {

    private val glossaryApi: GlossaryApi by inject()
    private val appStateStore: AppStateStore by inject()
    private val glossaryStateHolder = appStateStore.glossaryStateHolder

    private val _model = MutableValue(ReviewChangesComponent.Model())
    override val model: Value<ReviewChangesComponent.Model> = _model

    private val componentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        doOnResume {
            setFullscreen(true)
        }
    }

    override fun loadPendingPhrases(glossary: Glossary, isRefreshing: Boolean) {
        componentScope.launch {
            val glossaryRemoteId = glossary.remoteId ?: return@launch

            if (isRefreshing) {
                _model.update { it.copy(isRefreshing = true) }
            } else {
                val progress = Progress(
                    value = -1f,
                    message = getString(Res.string.loading_wait)
                )
                _model.update { it.copy(progress = progress, isLoading = true) }
            }
            val result = withContext(Dispatchers.Default) {
                glossaryApi.getPendingPhrases(glossaryRemoteId)
            }
            when (result) {
                is NetworkResult.Success -> {
                    _model.update { state ->
                        state.copy(
                            pendingPhrases = result.data.sortedBy { it.original == null }
                        )
                    }
                }
                is NetworkResult.Error -> {
                    this@DefaultReviewChangesComponent.logE("Load pending phrases failed: $result")
                }
            }
            _model.update { it.copy(isRefreshing = false, progress = null, isLoading = false) }
        }
    }

    override fun saveReviewStatus(phrase: PendingPhrase, status: ReviewStatus) {
        componentScope.launch {
            val glossaryRemoteId = glossaryStateHolder.state.value.glossary?.remoteId ?: return@launch

            val progress = Progress(
                value = -1f,
                message = getString(Res.string.sending_review)
            )
            _model.update { it.copy(progress = progress) }

            val result = withContext(Dispatchers.Default) {
                val phraseReview = PhraseReview(
                    phrase = phrase.phrase.phrase,
                    status = status,
                    user = User("", "")
                )
                glossaryApi.reviewPendingPhrase(glossaryRemoteId, phraseReview)
            }
            when (result) {
                is NetworkResult.Success -> {
                    _model.update {
                        it.copy(
                            pendingPhrases = it.pendingPhrases.mapNotNull { pendingPhrase ->
                                if (pendingPhrase.phrase.id == phrase.phrase.id) {
                                    if (result.data.isNotEmpty()) {
                                        pendingPhrase.copy(reviews = result.data)
                                    } else null
                                } else pendingPhrase
                            }
                        )
                    }
                }
                is NetworkResult.Error -> {
                    _model.update { it.copy(snackBarMessage = result.message.error) }
                    this@DefaultReviewChangesComponent.logE("Save review status failed: $result")
                }
            }

            _model.update { it.copy(progress = null) }
        }
    }

    override fun clearSnackBarMessage() {
        _model.update { it.copy(snackBarMessage = null) }
    }
}