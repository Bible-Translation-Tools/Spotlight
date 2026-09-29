package org.bibletranslationtools.glossary.ui.splash

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import org.bibletranslationtools.glossary.data.Glossary
import org.bibletranslationtools.glossary.data.Language
import org.bibletranslationtools.glossary.data.Resource
import org.bibletranslationtools.glossary.domain.InitApp
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository
import org.bibletranslationtools.glossary.platform.ResourceContainerAccessor
import org.bibletranslationtools.glossary.ui.state.AppStateStore
import org.bibletranslationtools.glossary.ui.state.AppStateStoreImpl
import org.bibletranslationtools.glossary.ui.state.GlossaryStateHolderImpl
import org.bibletranslationtools.glossary.ui.state.ResourceStateHolderImpl
import org.bibletranslationtools.glossary.settle
import org.bibletranslationtools.glossary.waitForCondition
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SplashComponentTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val initApp: InitApp = mockk()
    private val resourceContainerAccessor: ResourceContainerAccessor = mockk()
    private val glossaryRepository: GlossaryRepository = mockk()
    
    private lateinit var resourceStateHolder: ResourceStateHolderImpl
    private lateinit var glossaryStateHolder: GlossaryStateHolderImpl
    private lateinit var appStateStore: AppStateStore

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        
        resourceStateHolder = ResourceStateHolderImpl()
        glossaryStateHolder = GlossaryStateHolderImpl()
        appStateStore = AppStateStoreImpl(
            resourceStateHolder = resourceStateHolder,
            glossaryStateHolder = glossaryStateHolder,
            userStateHolder = mockk(relaxed = true)
        )
        
        startKoin {
            modules(module {
                single { initApp }
                single { appStateStore }
                single { resourceContainerAccessor }
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
    fun testSplashComponentInitialization() = runTest(testDispatcher) {
        // Stub InitApp invoke to trigger progress messages
        coEvery { initApp.invoke(any()) } coAnswers {
            val progressCallback = firstArg<(String) -> Unit>()
            progressCallback("Initializing SQLite...")
        }
        
        // Mock Resource retrieval and parsing
        val language = Language("en", "English", "ltr")
        val dbResource = Resource(
            lang = "en",
            type = "ulb",
            version = "1.0",
            format = "usfm",
            url = "http://example.com/en_ulb.zip",
            filename = "en_ulb.zip",
            createdAt = LocalDateTime(2024, 1, 1, 0, 0),
            modifiedAt = LocalDateTime(2024, 1, 1, 0, 0),
            id = 123L
        )
        val parsedResource = dbResource.copy()
        
        coEvery { glossaryRepository.getResource("en", "ulb") } returns dbResource
        coEvery { resourceContainerAccessor.read("en_ulb.zip") } returns parsedResource
        
        // Mock Glossary retrieval
        val glossary = Glossary(
            id = "g123",
            code = "G123",
            sourceLanguage = language,
            targetLanguage = language,
            version = 1,
            resourceId = 123L,
            createdAt = LocalDateTime(2024, 1, 1, 0, 0),
            updatedAt = LocalDateTime(2024, 1, 1, 0, 0)
        )
        coEvery { glossaryRepository.getGlossary("g123") } returns glossary
        
        var initDoneCalled = false
        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        
        val component = DefaultSplashComponent(
            componentContext = componentContext,
            onInitDone = { initDoneCalled = true }
        )
        
        component.initializeApp("en_ulb", "g123")
        
        // Wait for coroutines to yield/complete (delay(2000) inside component will be virtually advanced)
        testScheduler.advanceUntilIdle()
        
        // Wait for Dispatchers.Default inside loadResource/loadGlossary logic to finish
        waitForCondition {
            initDoneCalled
        }
        
        // Assertions
        val model = component.model.value
        assertNull(model.message)
        assertTrue(initDoneCalled)
        
        // Verify state store was populated
        assertEquals(parsedResource, resourceStateHolder.state.value.resource)
        assertEquals(glossary, glossaryStateHolder.state.value.glossary)
        
        coVerify {
            initApp.invoke(any())
            glossaryRepository.getResource("en", "ulb")
            resourceContainerAccessor.read("en_ulb.zip")
            glossaryRepository.getGlossary("g123")
        }
    }
}
