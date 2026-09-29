package org.bibletranslationtools.glossary.ui.drawer.settings

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
import org.bibletranslationtools.glossary.domain.CatalogApi
import org.bibletranslationtools.glossary.domain.FileSystemProvider
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository
import org.bibletranslationtools.glossary.platform.ResourceContainerAccessor
import org.bibletranslationtools.glossary.ui.drawer.DrawerContext
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

@OptIn(ExperimentalCoroutinesApi::class)
class CreateGlossaryComponentTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val glossaryRepository: GlossaryRepository = mockk()
    private val catalogApi: CatalogApi = mockk()
    private val fileSystemProvider: FileSystemProvider = mockk()
    private val resourceContainerAccessor: ResourceContainerAccessor = mockk()
    private val parentContext: DrawerContext = mockk(relaxed = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        
        startKoin {
            modules(module {
                single { glossaryRepository }
                single { catalogApi }
                single { fileSystemProvider }
                single { resourceContainerAccessor }
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
    fun testCreateGlossaryResourceMissing() = runTest(testDispatcher) {
        val sharedState = CreateGlossaryStateKeeper()
        val language = Language("en", "English", "ltr")
        sharedState.onLanguageSelected(LanguageType.SOURCE, language)
        sharedState.onLanguageSelected(LanguageType.TARGET, language)
        
        // Mock getResources to return empty list (resource missing)
        coEvery { glossaryRepository.getResources("en") } returns emptyList()
        
        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultCreateGlossaryComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            sharedState = sharedState,
            onResourceDownloaded = {},
            onGlossaryCreated = { _, _ -> },
            onSelectLanguage = {}
        )
        
        component.createGlossary("G123")
        
        // Wait for coroutines to complete
        testScheduler.advanceUntilIdle()
        
        // Wait for background threads to complete
        waitForCondition {
            component.model.value.resourceRequest != null && !component.model.value.isSaving
        }
        
        val model = component.model.value
        assertFalse(model.isSaving)
        assertNotNull(model.resourceRequest)
        assertEquals("G123", model.resourceRequest.code)
        assertEquals("en", model.resourceRequest.lang)
    }

    @Test
    fun testCreateGlossarySuccess() = runTest(testDispatcher) {
        val sharedState = CreateGlossaryStateKeeper()
        val language = Language("en", "English", "ltr")
        sharedState.onLanguageSelected(LanguageType.SOURCE, language)
        sharedState.onLanguageSelected(LanguageType.TARGET, language)
        
        val dbResource = Resource(
            lang = "en",
            type = "ulb",
            version = "1.0",
            format = "usfm",
            url = "",
            filename = "en_ulb.zip",
            createdAt = LocalDateTime(2024, 1, 1, 0, 0),
            modifiedAt = LocalDateTime(2024, 1, 1, 0, 0),
            id = 123L
        )
        
        // Mock getResources returning database resource
        coEvery { glossaryRepository.getResources("en") } returns listOf(dbResource)
        every { resourceContainerAccessor.read("en_ulb.zip") } returns dbResource
        coEvery { glossaryRepository.addGlossary(any()) } returns "new_glossary_id"
        coEvery { glossaryRepository.addPhrase(any()) } returns "new_phrase_id"
        
        var createdGlossary: Glossary? = null
        var createdResource: Resource? = null
        
        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultCreateGlossaryComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            sharedState = sharedState,
            onResourceDownloaded = {},
            onGlossaryCreated = { r, g -> 
                createdResource = r
                createdGlossary = g
            },
            onSelectLanguage = {}
        )
        
        component.createGlossary("G123")
        
        // Wait for coroutines to complete
        testScheduler.advanceUntilIdle()
        
        // Wait for background threads to complete
        waitForCondition {
            createdGlossary != null && !component.model.value.isSaving
        }
        
        val model = component.model.value
        assertFalse(model.isSaving)
        assertNull(model.resourceRequest)
        
        assertNotNull(createdGlossary)
        assertEquals("G123", createdGlossary.code)
        assertEquals("new_glossary_id", createdGlossary.id)
        assertEquals(dbResource, createdResource)
        
        coVerify {
            glossaryRepository.getResources("en")
            resourceContainerAccessor.read("en_ulb.zip")
            glossaryRepository.addGlossary(any())
        }
    }
}
