package org.bibletranslationtools.glossary.ui.read

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
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
import org.bibletranslationtools.glossary.data.RefOption
import org.bibletranslationtools.glossary.data.Resource
import org.bibletranslationtools.glossary.data.Workbook
import org.bibletranslationtools.glossary.ui.ParentContext
import org.bibletranslationtools.glossary.ui.state.AppStateStore
import org.bibletranslationtools.glossary.ui.state.AppStateStoreImpl
import org.bibletranslationtools.glossary.ui.state.ResourceStateHolderImpl
import org.bibletranslationtools.glossary.settle
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseComponentTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private lateinit var appStateStore: AppStateStore
    private lateinit var resourceStateHolder: ResourceStateHolderImpl
    private lateinit var parentContext: ParentContext

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        
        resourceStateHolder = ResourceStateHolderImpl()
        appStateStore = AppStateStoreImpl(
            resourceStateHolder = resourceStateHolder,
            glossaryStateHolder = mockk(),
            userStateHolder = mockk()
        )
        
        startKoin {
            modules(module {
                single<AppStateStore> { appStateStore }
            })
        }
        
        parentContext = mockk(relaxed = true)
    }

    @AfterTest
    fun tearDown() {
        testDispatcher.settle()
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun testBrowseComponentInitialStateAndNavigation() = runTest(testDispatcher) {
        val language = Language("en", "English", "ltr")
        val chapters = listOf(Chapter(1) { emptyList() })
        val books = listOf(Workbook(1, "tit", "Titus", language) { chapters })
        
        val resource = Resource(
            lang = "en",
            type = "ulb",
            version = "1.0",
            format = "usfm",
            url = "",
            filename = "en_ulb.zip",
            createdAt = LocalDateTime(2024, 1, 1, 0, 0),
            modifiedAt = LocalDateTime(2024, 1, 1, 0, 0),
            books = books
        )
        
        resourceStateHolder.setResource(resource)

        var navigateBackCalled = false
        var navigateRefCalled: RefOption? = null
        
        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        
        val component = DefaultBrowseComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            book = "tit",
            chapter = 1,
            onNavigateBack = { navigateBackCalled = true },
            onNavigateRef = { ref -> navigateRefCalled = ref }
        )
        
        // Wait for coroutine job inside component to complete
        testScheduler.advanceUntilIdle()
        
        // Wait for background thread (Dispatchers.Default) to finish loading
        var attempts = 0
        while (component.model.value.books.isEmpty() && attempts < 100) {
            Thread.sleep(10)
            testScheduler.advanceTimeBy(10)
            attempts++
        }
        
        val model = component.model.value
        assertEquals(books, model.books)
        assertNotNull(model.book)
        assertEquals("tit", model.book.slug)
        assertNotNull(model.chapter)
        assertEquals(1, model.chapter.number)
        
        // Test back navigation trigger
        component.onBackClick()
        assertTrue(navigateBackCalled)
        
        // Test ref navigation trigger
        val refOption = RefOption("tit", 1, "2")
        component.onRefClick(refOption)
        assertEquals(refOption, navigateRefCalled)
    }
}
