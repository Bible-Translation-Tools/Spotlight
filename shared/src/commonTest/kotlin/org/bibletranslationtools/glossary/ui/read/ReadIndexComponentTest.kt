package org.bibletranslationtools.glossary.ui.read

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import org.bibletranslationtools.glossary.ui.ParentContext
import org.bibletranslationtools.glossary.ui.components.PhraseNavDir
import org.bibletranslationtools.glossary.ui.main.MainStateKeeper
import org.bibletranslationtools.glossary.ui.state.AppStateStore
import org.bibletranslationtools.glossary.ui.state.AppStateStoreImpl
import org.bibletranslationtools.glossary.ui.state.GlossaryStateHolderImpl
import org.bibletranslationtools.glossary.ui.state.ResourceStateHolderImpl
import org.bibletranslationtools.glossary.ui.state.UserStateHolderImpl
import org.bibletranslationtools.glossary.settle
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
class ReadIndexComponentTest {

    private val testDispatcher = StandardTestDispatcher()

    private val glossaryRepository: GlossaryRepository = mockk(relaxed = true)
    private val parentContext: ParentContext = mockk(relaxed = true)

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

    private fun TestScope.waitForCondition(condition: () -> Boolean) {
        var attempts = 0
        while (!condition() && attempts < 100) {
            Thread.sleep(10)
            testScheduler.advanceTimeBy(10)
            attempts++
        }
    }

    @Test
    fun testInitialSetupAndSharedStateSubscriptions() = runTest(testDispatcher) {
        val sharedState = MainStateKeeper()
        val component = DefaultReadIndexComponent(
            componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry()),
            parentContext = parentContext,
            ref = null,
            sharedState = sharedState,
            onNavigateViewPhrase = {},
            onNavigateEditPhrase = {},
            onNavigateBrowse = { _, _ -> }
        )

        testScheduler.advanceUntilIdle()

        // Verify initial state drawer states
        assertFalse(component.model.value.keyTermsDrawerOpen)
        assertFalse(component.model.value.settingsDrawerOpen)

        // Trigger updates in sharedState model
        sharedState.setKeyTermsDrawerOpen(true)
        sharedState.setSettingsDrawerOpen(true)
        testScheduler.advanceUntilIdle()

        // Verify component model receives updates
        assertTrue(component.model.value.keyTermsDrawerOpen)
        assertTrue(component.model.value.settingsDrawerOpen)
    }

    @Test
    fun testBookChapterNavigationAndPhraseClicks() = runTest(testDispatcher) {
        val language = Language("en", "English", "ltr")
        val sharedState = MainStateKeeper()

        // Setup resource:
        // Book 1: Genesis (gen), has 2 chapters
        // Book 2: Exodus (exo), has 1 chapter
        val book1Verse1 = Verse("1", "In the beginning God created.")
        val book1Chapter1 = Chapter(1) { listOf(book1Verse1) }
        val book1Verse2 = Verse("1", "Thus the heavens and the earth were finished.")
        val book1Chapter2 = Chapter(2) { listOf(book1Verse2) }
        val book1 = Workbook(1, "gen", "Genesis", language) { listOf(book1Chapter1, book1Chapter2) }

        val book2Verse1 = Verse("1", "Now these are the names.")
        val book2Chapter1 = Chapter(1) { listOf(book2Verse1) }
        val book2 = Workbook(2, "exo", "Exodus", language) { listOf(book2Chapter1) }

        val resource = Resource(
            lang = "en",
            type = "ulb",
            version = "1.0",
            format = "usfm",
            url = "",
            filename = "en_ulb.zip",
            createdAt = LocalDateTime(2024, 1, 1, 0, 0),
            modifiedAt = LocalDateTime(2024, 1, 1, 0, 0),
            books = listOf(book1, book2)
        )
        resourceStateHolder.setResource(resource)

        // Setup active glossary
        val glossary = Glossary("g1", language, language, 1, id = "g1_id")
        glossaryStateHolder.setGlossary(glossary)

        val phrase1 = Phrase("God", id = "p1", glossaryId = "g1_id")
        val phrase2 = Phrase("heavens", id = "p2", glossaryId = "g1_id")
        val phrase3 = Phrase("names", id = "p3", glossaryId = "g1_id")

        coEvery { glossaryRepository.getPhrases("g1_id") } returns listOf(phrase1, phrase2, phrase3)
        coEvery { glossaryRepository.getPendingPhrases("g1_id") } returns emptyList()

        var viewPhraseCalled: Phrase? = null
        var editPhraseCalled: Phrase? = null
        var browseCalled: Pair<String, Int>? = null

        val component = DefaultReadIndexComponent(
            componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry()),
            parentContext = parentContext,
            ref = null,
            sharedState = sharedState,
            onNavigateViewPhrase = { p -> viewPhraseCalled = p },
            onNavigateEditPhrase = { p -> editPhraseCalled = p },
            onNavigateBrowse = { b, c -> browseCalled = b to c }
        )

        testScheduler.advanceUntilIdle()

        // 1. Initial State (no active book or chapter)
        assertNull(component.model.value.activeBook)
        assertNull(component.model.value.activeChapter)

        // 2. Navigate to Book 1 Chapter 1
        component.navigateBookChapter("gen", 1)
        testScheduler.advanceUntilIdle()
        waitForCondition { component.model.value.chapterPhrases.isNotEmpty() }

        assertEquals(book1, component.model.value.activeBook)
        assertEquals(book1Chapter1, component.model.value.activeChapter)
        // Chapter 1 has phrase1 "God" in its verses
        assertEquals(1, component.model.value.chapterPhrases.size)
        assertEquals(phrase1, component.model.value.chapterPhrases[0])

        // 3. Test phrase details click
        component.onPhraseClick(phrase1, "1")
        testScheduler.advanceUntilIdle()
        waitForCondition { component.model.value.phraseDetails != null }

        val details = component.model.value.phraseDetails
        assertNotNull(details)
        assertEquals(phrase1, details.phrase)
        assertEquals("gen", details.ref?.book)
        assertEquals("1", details.ref?.chapter)
        assertEquals("1", details.ref?.verse)

        // Clear details
        component.clearPhraseDetails()
        assertNull(component.model.value.phraseDetails)

        // 4. Navigate to nextChapter (same book, gen:2)
        component.nextChapter()
        testScheduler.advanceUntilIdle()
        waitForCondition { 
            component.model.value.activeChapter?.number == 2 && 
            component.model.value.chapterPhrases.isNotEmpty() && 
            component.model.value.chapterPhrases[0] == phrase2 
        }

        assertEquals(book1, component.model.value.activeBook)
        assertEquals(book1Chapter2, component.model.value.activeChapter)
        assertEquals(1, component.model.value.chapterPhrases.size)
        assertEquals(phrase2, component.model.value.chapterPhrases[0])

        // 5. Navigate to nextChapter (crosses book boundary from gen to exo)
        component.nextChapter()
        testScheduler.advanceUntilIdle()
        waitForCondition { 
            component.model.value.activeBook?.slug == "exo" && 
            component.model.value.chapterPhrases.isNotEmpty() && 
            component.model.value.chapterPhrases[0] == phrase3 
        }

        assertEquals(book2, component.model.value.activeBook)
        assertEquals(book2Chapter1, component.model.value.activeChapter)

        // 6. Navigate to prevChapter (crosses book boundary back to gen:2)
        component.prevChapter()
        testScheduler.advanceUntilIdle()
        waitForCondition { 
            component.model.value.activeBook?.slug == "gen" && 
            component.model.value.activeChapter?.number == 2 && 
            component.model.value.chapterPhrases.isNotEmpty() && 
            component.model.value.chapterPhrases[0] == phrase2 
        }

        assertEquals(book1, component.model.value.activeBook)
        assertEquals(book1Chapter2, component.model.value.activeChapter)

        // 7. Test select phrase (navigates to edit screen, uses Dispatchers.IO)
        coEvery { glossaryRepository.getPendingPhrase("God", "g1_id") } returns null
        coEvery { glossaryRepository.getPhrase("God", "g1_id") } returns phrase1

        component.onPhraseSelected("God")
        testScheduler.advanceUntilIdle()
        waitForCondition { editPhraseCalled != null }
        assertEquals(phrase1, editPhraseCalled)

        // 8. Test browse click
        component.onBrowseClick("gen", 1)
        assertEquals("gen", browseCalled?.first)
        assertEquals(1, browseCalled?.second)
    }

    @Test
    fun testUtilityOperationsAndPhraseNavigation() = runTest(testDispatcher) {
        val language = Language("en", "English", "ltr")
        val sharedState = MainStateKeeper()

        val book1Verse1 = Verse("1", "In the beginning God created.")
        val book1Chapter1 = Chapter(1) { listOf(book1Verse1) }
        val book1 = Workbook(1, "gen", "Genesis", language) { listOf(book1Chapter1) }

        val resource = Resource(
            lang = "en",
            type = "ulb",
            version = "1.0",
            format = "usfm",
            url = "",
            filename = "en_ulb.zip",
            createdAt = LocalDateTime(2024, 1, 1, 0, 0),
            modifiedAt = LocalDateTime(2024, 1, 1, 0, 0),
            books = listOf(book1)
        )
        resourceStateHolder.setResource(resource)

        val glossary = Glossary("g1", language, language, 1, id = "g1_id")
        glossaryStateHolder.setGlossary(glossary)

        val phrase1 = Phrase("God", id = "p1", glossaryId = "g1_id")
        val phrase2 = Phrase("beginning", id = "p2", glossaryId = "g1_id")

        // Chapter 1 has both phrases matching
        coEvery { glossaryRepository.getPhrases("g1_id") } returns listOf(phrase1, phrase2)
        coEvery { glossaryRepository.getPendingPhrases("g1_id") } returns emptyList()

        var viewPhraseCalled: Phrase? = null

        val component = DefaultReadIndexComponent(
            componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry()),
            parentContext = parentContext,
            ref = null,
            sharedState = sharedState,
            onNavigateViewPhrase = { p -> viewPhraseCalled = p },
            onNavigateEditPhrase = {},
            onNavigateBrowse = { _, _ -> }
        )

        testScheduler.advanceUntilIdle()

        // Navigate to load phrases
        component.navigateBookChapter("gen", 1)
        testScheduler.advanceUntilIdle()
        waitForCondition { component.model.value.chapterPhrases.size == 2 }

        // Click phrase 1 to load details
        component.onPhraseClick(phrase1, "1")
        testScheduler.advanceUntilIdle()
        waitForCondition { component.model.value.phraseDetails != null }

        // Test navigatePhrase (NEXT) -> should go to phrase2
        component.navigatePhrase(PhraseNavDir.NEXT)
        testScheduler.advanceUntilIdle()
        waitForCondition { component.model.value.phraseDetails?.phrase == phrase2 }

        assertEquals(phrase2, component.model.value.phraseDetails?.phrase)

        // Test navigatePhrase (PREV) -> should go back to phrase1
        component.navigatePhrase(PhraseNavDir.PREV)
        testScheduler.advanceUntilIdle()
        waitForCondition { component.model.value.phraseDetails?.phrase == phrase1 }

        assertEquals(phrase1, component.model.value.phraseDetails?.phrase)

        // Test onViewPhraseClick
        component.onViewPhraseClick(phrase1)
        assertEquals(phrase1, viewPhraseCalled)

        // Test loadRef and clearRef
        assertNull(component.model.value.currentRef)
        val dummyRef = mockk<org.bibletranslationtools.glossary.data.RefOption>()
        component.loadRef(dummyRef)
        assertEquals(dummyRef, component.model.value.currentRef)

        component.clearRef()
        assertNull(component.model.value.currentRef)
    }
}
