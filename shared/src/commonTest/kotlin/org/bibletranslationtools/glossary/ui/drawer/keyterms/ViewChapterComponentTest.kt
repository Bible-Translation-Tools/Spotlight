package org.bibletranslationtools.glossary.ui.drawer.keyterms

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import org.bibletranslationtools.glossary.data.Chapter
import org.bibletranslationtools.glossary.data.Language
import org.bibletranslationtools.glossary.data.Phrase
import org.bibletranslationtools.glossary.data.Ref
import org.bibletranslationtools.glossary.data.Resource
import org.bibletranslationtools.glossary.data.Verse
import org.bibletranslationtools.glossary.data.Workbook
import org.bibletranslationtools.glossary.ui.drawer.DrawerContext
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ViewChapterComponentTest {

    private val testDispatcher = StandardTestDispatcher()

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
    fun testOnResumeLoadsVerses() = runTest(testDispatcher) {
        val language = Language("en", "English", "ltr")
        
        // Setup resource with a specific book and chapter
        val verse1 = Verse("1", "In the beginning God created the heavens.")
        val verse2 = Verse("2", "The earth was formless and empty.")
        val chapter = Chapter(1) { listOf(verse1, verse2) }
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
        val ref = Ref("gen", "1", "1", phraseId = "p1")

        val lifecycleRegistry = LifecycleRegistry()
        val component = DefaultViewChapterComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycleRegistry),
            parentContext = parentContext,
            phrase = phrase,
            ref = ref
        )

        // Model initially empty
        assertNull(component.model.value.phrase)
        assertNull(component.model.value.ref)
        assertTrue(component.model.value.verses.isEmpty())

        // Trigger resume to load chapter verses
        lifecycleRegistry.resume()
        testScheduler.advanceUntilIdle()

        var attempts = 0
        while (component.model.value.isLoading && attempts < 100) {
            Thread.sleep(10)
            testScheduler.advanceTimeBy(10)
            attempts++
        }

        val model = component.model.value
        assertFalse(model.isLoading)
        assertEquals(phrase, model.phrase)
        assertEquals(ref, model.ref)
        assertEquals(2, model.verses.size)
        assertEquals(verse1, model.verses[0])
        assertEquals(verse2, model.verses[1])
    }
}
