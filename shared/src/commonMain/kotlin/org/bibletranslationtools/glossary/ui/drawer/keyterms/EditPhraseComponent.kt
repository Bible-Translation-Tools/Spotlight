package org.bibletranslationtools.glossary.ui.drawer.keyterms

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
import org.bibletranslationtools.glossary.Utils.getCurrentTime
import org.bibletranslationtools.glossary.data.Phrase
import org.bibletranslationtools.glossary.data.Ref
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository
import org.bibletranslationtools.glossary.normalize
import org.bibletranslationtools.glossary.ui.drawer.DrawerComponent
import org.bibletranslationtools.glossary.ui.drawer.DrawerContext
import org.bibletranslationtools.glossary.ui.main.SharedEvent
import org.bibletranslationtools.glossary.ui.state.AppStateStore
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.no_refs_found

interface EditPhraseComponent : DrawerContext {

    val model: Value<Model>

    data class Model(
        val isSaving: Boolean = false,
        val phrase: Phrase? = null,
        val error: String? = null,
        val isNewPhrase: Boolean = false,
        val justSaved: Boolean = false
    )

    fun savePendingPhrase(spelling: String, description: String)
    fun onNavigateToGlossary()
    fun reset()
}

class DefaultEditPhraseComponent(
    componentContext: ComponentContext,
    private val parentContext: DrawerContext,
    private val phrase: Phrase,
    private val onSendEvent: (SharedEvent) -> Unit,
    private val navigateToGlossary: () -> Unit
) : DrawerComponent(componentContext, parentContext), EditPhraseComponent, KoinComponent {

    private val appStateStore: AppStateStore by inject()
    private val glossaryRepository: GlossaryRepository by inject()

    private val _model = MutableValue(EditPhraseComponent.Model())
    override val model: Value<EditPhraseComponent.Model> = _model

    private val componentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val resourceState = appStateStore.resourceStateHolder.state
    private val glossaryState = appStateStore.glossaryStateHolder.state

    init {
        doOnResume {
            setFullscreen(true)
        }

        componentScope.launch {
            _model.update {
                it.copy(
                    phrase = phrase,
                    isNewPhrase = phrase.id == null
                )
            }
        }
    }

    override fun savePendingPhrase(spelling: String, description: String) {
        componentScope.launch {
            val glossaryId = glossaryState.value.glossary?.id ?: return@launch

            _model.value = _model.value.copy(
                isSaving = true,
                error = null
            )

            val error = withContext(Dispatchers.IO) {
                _model.value.phrase?.let { phrase ->
                    val phrase = phrase.copy(
                        phrase = phrase.phrase.normalize(),
                        spelling = spelling.normalize(),
                        description = description.normalize(),
                        updatedAt = getCurrentTime(),
                        id = phrase.id,
                        glossaryId = glossaryId
                    )

                    val refs = findRefs(phrase).distinctBy {
                        listOf(it.book, it.chapter, it.verse)
                    }

                    if (refs.isNotEmpty()) {
                        glossaryRepository.addPendingPhrase(phrase)
                        null
                    } else {
                        getString(Res.string.no_refs_found)
                    }
                }
            }

            _model.update {
                it.copy(
                    isSaving = false,
                    error = error
                )
            }

            if (error == null) {
                _model.update { it.copy(justSaved = true) }
                onSendEvent(SharedEvent.TriggerUpdate)
            }
        }
    }

    override fun onNavigateToGlossary() {
        navigateToGlossary()
    }

    override fun reset() {
        _model.update { it.copy(justSaved = false) }
    }

    override fun dismiss() {
        navigateBack()
    }

    override fun navigateBack() {
        parentContext.navigateBack()
    }

    private fun findRefs(phrase: Phrase): List<Ref> {
        val resource = resourceState.value.resource ?: return emptyList()

        val regex = Regex(
            pattern = "\\b${Regex.escape(phrase.phrase.normalize())}\\b",
            option = RegexOption.IGNORE_CASE
        )
        val refs = mutableListOf<Ref>()

        for (book in resource.books) {
            for (chapter in book.chapters) {
                for (verse in chapter.verses) {
                    val matchCount = regex.findAll(verse.text.normalize()).count()
                    if (matchCount > 0) {
                        repeat(matchCount) {
                            refs.add(
                                Ref(
                                    book = book.slug,
                                    chapter = chapter.number.toString(),
                                    verse = verse.number,
                                    phraseId = phrase.id
                                )
                            )
                        }
                    }
                }
            }
        }
        return refs
    }
}