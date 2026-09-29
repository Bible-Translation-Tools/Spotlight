package org.bibletranslationtools.glossary.ui.drawer.keyterms

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import org.bibletranslationtools.glossary.data.Chapter
import org.bibletranslationtools.glossary.data.Glossary
import org.bibletranslationtools.glossary.data.Language
import org.bibletranslationtools.glossary.data.Phrase
import org.bibletranslationtools.glossary.data.Resource
import org.bibletranslationtools.glossary.data.Verse
import org.bibletranslationtools.glossary.data.Workbook
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository
import org.bibletranslationtools.glossary.ui.drawer.DrawerContext
import org.bibletranslationtools.glossary.ui.main.SharedEvent
import org.bibletranslationtools.glossary.ui.state.AppStateStore
import org.bibletranslationtools.glossary.ui.state.AppStateStoreImpl
import org.bibletranslationtools.glossary.ui.state.GlossaryStateHolderImpl
import org.bibletranslationtools.glossary.ui.state.ResourceStateHolderImpl
import org.bibletranslationtools.glossary.ui.state.UserStateHolderImpl
import org.bibletranslationtools.glossary.settle
import org.bibletranslationtools.glossary.waitForCondition
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditPhraseComponentTest {

    private val testDispatcher = StandardTestDispatcher()

    private val glossaryRepository: GlossaryRepository = mockk(relaxed = true)
    private val parentContext: DrawerContext = mockk(relaxed = true)

    private lateinit var appStateStore: AppStateStore
    private lateinit var glossaryStateHolder: GlossaryStateHolderImpl
    private lateinit var resourceStateHolder: ResourceStateHolderImpl
    private lateinit var userStateHolder: UserStateHolderImpl

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        glossaryStateHolder = GlossaryStateHolderImpl()
        resourceStateHolder = ResourceStateHolderImpl()
        userStateHolder = UserStateHolderImpl()

        appStateStore = AppStateStoreImpl(
            resourceStateHolder = resourceStateHolder,
            glossaryStateHolder = glossaryStateHolder,
            userStateHolder = userStateHolder
        )

        startKoin {
            modules(module {
                single { appStateStore }
                single { glossaryRepository }
            })
        }
    }

    @AfterTest
    fun tearDown() {
        testDispatcher.settle()
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun testInitializationAndProperties() = runTest(testDispatcher) {
        val phrase = Phrase("God", id = "p1")
        
        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultEditPhraseComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            phrase = phrase,
            onSendEvent = {},
            navigateToGlossary = {}
        )

        testScheduler.advanceUntilIdle()

        val model = component.model.value
        assertEquals(phrase, model.phrase)
        assertFalse(model.isNewPhrase)
        assertFalse(model.isSaving)
        assertFalse(model.justSaved)
        assertNull(model.error)
    }

    @Test
    fun testSavePendingPhraseSuccess() = runTest(testDispatcher) {
        val language = Language("en", "English", "ltr")
        val glossary = Glossary("g1", language, language, 1, remoteId = "rem1", id = "g1_id")
        glossaryStateHolder.setGlossary(glossary)

        // Setup resource containing the phrase "God"
        val verse1 = Verse("1", "In the beginning God created the heavens.")
        val chapter = Chapter(1) { listOf(verse1) }
        val workbook = Workbook(1, "gen", "Genesis", language) { listOf(chapter) }
        val resource = Resource(
            lang = "en",
            type = "ulb",
            version = "1.0",
            format = "usfm",
            url = "",
            filename = "en_ulb.zip",
            createdAt = LocalDateTime(2024, 1, 1, 0, 0),
            modifiedAt = LocalDateTime(2024, 1, 1, 0, 0),
            books = listOf(workbook)
        )
        resourceStateHolder.setResource(resource)

        val phrase = Phrase("God", id = "p1")
        var eventSent: SharedEvent? = null

        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultEditPhraseComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            phrase = phrase,
            onSendEvent = { e -> eventSent = e },
            navigateToGlossary = {}
        )

        testScheduler.advanceUntilIdle()

        component.savePendingPhrase(spelling = "God", description = "Creator")
        testScheduler.advanceUntilIdle()

        waitForCondition {
            component.model.value.justSaved && eventSent != null && !component.model.value.isSaving
        }

        val model = component.model.value
        assertFalse(model.isSaving)
        assertNull(model.error)
        assertTrue(model.justSaved)
        assertEquals(SharedEvent.TriggerUpdate, eventSent)

        coVerify { glossaryRepository.addPendingPhrase(any()) }
    }

    @Test
    fun testSavePendingPhraseFailureNoRefs() = runTest(testDispatcher) {
        val language = Language("en", "English", "ltr")
        val glossary = Glossary("g1", language, language, 1, remoteId = "rem1", id = "g1_id")
        glossaryStateHolder.setGlossary(glossary)

        // Setup resource WITHOUT the phrase "unmatched"
        val verse1 = Verse("1", "In the beginning God created the heavens.")
        val chapter = Chapter(1) { listOf(verse1) }
        val workbook = Workbook(1, "gen", "Genesis", language) { listOf(chapter) }
        val resource = Resource(
            lang = "en",
            type = "ulb",
            version = "1.0",
            format = "usfm",
            url = "",
            filename = "en_ulb.zip",
            createdAt = LocalDateTime(2024, 1, 1, 0, 0),
            modifiedAt = LocalDateTime(2024, 1, 1, 0, 0),
            books = listOf(workbook)
        )
        resourceStateHolder.setResource(resource)

        val phrase = Phrase("unmatched", id = "p1")
        var eventSent: SharedEvent? = null

        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultEditPhraseComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            phrase = phrase,
            onSendEvent = { e -> eventSent = e },
            navigateToGlossary = {}
        )

        testScheduler.advanceUntilIdle()

        component.savePendingPhrase(spelling = "unmatched", description = "Test")
        testScheduler.advanceUntilIdle()

        waitForCondition {
            component.model.value.error != null && !component.model.value.isSaving
        }

        val model = component.model.value
        assertFalse(model.isSaving)
        assertNotNull(model.error)
        assertTrue(model.error.isNotEmpty())
        assertFalse(model.justSaved)
        assertNull(eventSent)

        coVerify(exactly = 0) { glossaryRepository.addPendingPhrase(any()) }
    }

    @Test
    fun testNavigationAndReset() = runTest(testDispatcher) {
        val phrase = Phrase("God", id = "p1")
        var navigatedToGlossary = false

        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultEditPhraseComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            phrase = phrase,
            onSendEvent = {},
            navigateToGlossary = { navigatedToGlossary = true }
        )

        testScheduler.advanceUntilIdle()

        // Test navigate to glossary
        component.onNavigateToGlossary()
        assertTrue(navigatedToGlossary)

        // Test navigate back & dismiss
        component.dismiss()
        verify { parentContext.navigateBack() }

        // Test reset
        component.reset()
        assertFalse(component.model.value.justSaved)
    }
}
