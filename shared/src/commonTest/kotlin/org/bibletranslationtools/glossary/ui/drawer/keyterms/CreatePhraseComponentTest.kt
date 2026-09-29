package org.bibletranslationtools.glossary.ui.drawer.keyterms

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import io.mockk.coEvery
import io.mockk.mockk
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreatePhraseComponentTest {

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
    fun testInitializationAndSearch() = runTest(testDispatcher) {
        val language = Language("en", "English", "ltr")
        
        // Setup resource with a verse
        val verse1 = Verse("1", "In the beginning God created the heavens and the earth.")
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

        // Setup glossary and existing phrases
        val glossary = Glossary("g1", language, language, 1, remoteId = "rem1", id = "g1_id")
        glossaryStateHolder.setGlossary(glossary)

        val existingPhrase = Phrase("beginning", id = "p1", glossaryId = "g1_id")
        coEvery { glossaryRepository.getPhrases("g1_id") } returns listOf(existingPhrase)
        // Set in the same step that stores the loaded phrases, after the verses are loaded
        var phrasesLoaded = false
        coEvery { glossaryRepository.getPendingPhrases("g1_id") } answers {
            phrasesLoaded = true
            emptyList()
        }

        var navigatedEditPhrase: Phrase? = null
        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultCreatePhraseComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            onNavigateEdit = { p -> navigatedEditPhrase = p }
        )

        testScheduler.advanceUntilIdle()

        // Init loads verses on a background thread, and the debounced empty query searches too
        waitForCondition {
            phrasesLoaded && !component.model.value.isSearching
        }

        // 1. Initial State
        assertEquals("", component.model.value.searchQuery)
        assertFalse(component.model.value.isSearching)
        assertTrue(component.model.value.results.isEmpty())

        // 2. Search for "creat" - should match "created" in verse1
        component.onSearchQueryChanged("creat")
        
        // Wait for debounce (300ms) and background search
        testScheduler.advanceTimeBy(350L)
        testScheduler.advanceUntilIdle()

        waitForCondition {
            component.model.value.results.firstOrNull()?.phrase == "created" && !component.model.value.isSearching
        }

        val modelAfterSearch = component.model.value
        assertFalse(modelAfterSearch.isSearching)
        assertEquals("creat", modelAfterSearch.searchQuery)
        assertEquals(1, modelAfterSearch.results.size)
        // Regex should extract the full matched word from "created"
        assertEquals("created", modelAfterSearch.results[0].phrase)
        assertEquals("g1_id", modelAfterSearch.results[0].glossaryId)
        assertNull(modelAfterSearch.results[0].id) // New phrase, so id is null

        // 3. Search for "beginning" - should match existing phrase "beginning" with id "p1"
        component.onSearchQueryChanged("beginning")
        testScheduler.advanceTimeBy(350L)
        testScheduler.advanceUntilIdle()

        waitForCondition {
            component.model.value.results.firstOrNull()?.phrase == "beginning" && !component.model.value.isSearching
        }

        val modelAfterSearch2 = component.model.value
        assertEquals(1, modelAfterSearch2.results.size)
        assertEquals("beginning", modelAfterSearch2.results[0].phrase)
        assertEquals("p1", modelAfterSearch2.results[0].id) // Returns the preloaded existing phrase

        // 4. Edit click
        component.onEditClick(existingPhrase)
        assertEquals(existingPhrase, navigatedEditPhrase)
    }
}
