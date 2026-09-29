package org.bibletranslationtools.glossary.ui.drawer.settings

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import io.github.vinceglb.filekit.PlatformFile
import io.mockk.coEvery
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
import org.bibletranslationtools.glossary.domain.GlossaryApi
import org.bibletranslationtools.glossary.domain.NetworkResult
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository
import org.bibletranslationtools.glossary.domain.usecases.ExportGlossary
import org.bibletranslationtools.glossary.platform.ResourceContainerAccessor
import org.bibletranslationtools.glossary.ui.drawer.DrawerContext
import org.bibletranslationtools.glossary.ui.state.AppStateStore
import org.bibletranslationtools.glossary.ui.state.AppStateStoreImpl
import org.bibletranslationtools.glossary.ui.state.GlossaryStateHolderImpl
import org.bibletranslationtools.glossary.ui.state.ResourceStateHolderImpl
import org.bibletranslationtools.glossary.ui.state.UserStateHolderImpl
import org.bibletranslationtools.glossary.waitForCondition
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
class GlossaryListComponentTest {

    private val testDispatcher = StandardTestDispatcher()

    private val glossaryRepository: GlossaryRepository = mockk()
    private val resourceContainerAccessor: ResourceContainerAccessor = mockk()
    private val exportGlossaryUseCase: ExportGlossary = mockk()
    private val glossaryApi: GlossaryApi = mockk()
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
                single { resourceContainerAccessor }
                single { exportGlossaryUseCase }
                single { glossaryApi }
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
    fun testInitializationAndLoadGlossaries() = runTest(testDispatcher) {
        val langEn = Language("en", "English", "ltr")
        val langEs = Language("es", "Spanish", "ltr")
        val glossary1 = Glossary("g1", langEn, langEs, 1, resourceId = 10L, id = "g1_id", remoteId = "rem1")
        val glossary2 = Glossary("g2", langEn, langEs, 1, resourceId = 20L, id = "g2_id")

        coEvery { glossaryRepository.getGlossaries() } returns listOf(glossary1, glossary2)
        coEvery { glossaryRepository.getPhrases("g1_id") } returns emptyList()
        coEvery { glossaryRepository.getPhrases("g2_id") } returns emptyList()
        coEvery { glossaryApi.getGlossaryUsers("rem1") } returns NetworkResult.Success(emptyList())

        val lifecycleRegistry = LifecycleRegistry()
        val componentContext = DefaultComponentContext(lifecycle = lifecycleRegistry)

        val component = DefaultGlossaryListComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            onNavigateImportGlossary = {},
            onNavigateCreateGlossary = {},
            onSelectGlossary = { _, _ -> },
            onSelectResource = {},
            onImportManually = {}
        )

        lifecycleRegistry.resume()

        testScheduler.advanceUntilIdle()

        // Wait for background threads to finish
        waitForCondition {
            component.model.value.glossaries.isNotEmpty() && !component.model.value.isLoading
        }

        val model = component.model.value
        assertFalse(model.isLoading)
        assertEquals(2, model.glossaries.size)
        assertEquals("g1", model.glossaries[0].glossary.code)
        assertEquals("g2", model.glossaries[1].glossary.code)
    }

    @Test
    fun testSelectAndSaveGlossary() = runTest(testDispatcher) {
        val langEn = Language("en", "English", "ltr")
        val langEs = Language("es", "Spanish", "ltr")
        val glossary = Glossary("g1", langEn, langEs, 1, resourceId = 10L, id = "g1_id", remoteId = "rem1")
        
        val dbResource = Resource(
            lang = "en",
            type = "ulb",
            version = "1.0",
            format = "usfm",
            url = "http://example.com/en_ulb.zip",
            filename = "en_ulb.zip",
            createdAt = LocalDateTime(2024, 1, 1, 0, 0),
            modifiedAt = LocalDateTime(2024, 1, 1, 0, 0),
            id = 10L
        )

        coEvery { glossaryRepository.getGlossaries() } returns emptyList()
        coEvery { glossaryRepository.getResource(10L) } returns dbResource
        every { resourceContainerAccessor.read("en_ulb.zip") } returns dbResource

        val lifecycleRegistry = LifecycleRegistry()
        val componentContext = DefaultComponentContext(lifecycle = lifecycleRegistry)

        var selectedGlossaryCalled: Glossary? = null
        var selectedResourceCalled: Resource? = null

        val component = DefaultGlossaryListComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            onNavigateImportGlossary = {},
            onNavigateCreateGlossary = {},
            onSelectGlossary = { g, flag -> 
                selectedGlossaryCalled = g
                assertTrue(flag)
            },
            onSelectResource = { r -> selectedResourceCalled = r },
            onImportManually = {}
        )

        lifecycleRegistry.resume()
        testScheduler.advanceUntilIdle()

        val glossaryItem = GlossaryItem(glossary, { 0 }, { 0 })
        component.selectGlossary(glossaryItem)

        // Wait for coroutine job inside component to complete
        testScheduler.advanceUntilIdle()

        // Wait for background threads to finish
        waitForCondition {
            component.model.value.selectedResource != null && !component.model.value.isLoading
        }

        val model = component.model.value
        assertEquals(glossaryItem, model.selectedGlossary)
        assertEquals(dbResource.copy(id = 10L, url = "http://example.com/en_ulb.zip"), model.selectedResource)

        // Test save glossary
        component.saveGlossary()
        testScheduler.advanceUntilIdle()

        assertEquals(glossary, selectedGlossaryCalled)
        assertEquals(dbResource.copy(id = 10L, url = "http://example.com/en_ulb.zip"), selectedResourceCalled)
    }

    @Test
    fun testExportGlossarySuccess() = runTest(testDispatcher) {
        val langEn = Language("en", "English", "ltr")
        val langEs = Language("es", "Spanish", "ltr")
        val glossary = Glossary("g1", langEn, langEs, 1, resourceId = 10L, id = "g1_id", remoteId = "rem1")
        val glossaryItem = GlossaryItem(glossary, { 0 }, { 0 })

        val dbResource = Resource(
            lang = "en",
            type = "ulb",
            version = "1.0",
            format = "usfm",
            url = "http://example.com/en_ulb.zip",
            filename = "en_ulb.zip",
            createdAt = LocalDateTime(2024, 1, 1, 0, 0),
            modifiedAt = LocalDateTime(2024, 1, 1, 0, 0),
            id = 10L
        )

        coEvery { glossaryRepository.getGlossaries() } returns emptyList()
        coEvery { glossaryRepository.getResource(10L) } returns dbResource
        every { resourceContainerAccessor.read("en_ulb.zip") } returns dbResource
        coEvery { exportGlossaryUseCase(glossary, any()) } returns Unit

        val lifecycleRegistry = LifecycleRegistry()
        val componentContext = DefaultComponentContext(lifecycle = lifecycleRegistry)

        val component = DefaultGlossaryListComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            onNavigateImportGlossary = {},
            onNavigateCreateGlossary = {},
            onSelectGlossary = { _, _ -> },
            onSelectResource = {},
            onImportManually = {}
        )

        lifecycleRegistry.resume()
        testScheduler.advanceUntilIdle()

        // Select glossary and wait for it to complete loading
        component.selectGlossary(glossaryItem)
        
        testScheduler.advanceUntilIdle()

        waitForCondition {
            component.model.value.selectedResource != null && !component.model.value.isLoading
        }

        // Mock PlatformFile
        val mockFile = mockk<PlatformFile>()

        component.exportGlossary(mockFile)

        testScheduler.advanceUntilIdle()

        waitForCondition {
            component.model.value.snackBarMessage != null && component.model.value.progress == null
        }

        val model = component.model.value
        assertNull(model.progress)
        assertNotNull(model.snackBarMessage)
        assertTrue(model.snackBarMessage.isNotEmpty())

        component.clearSnackBarMessage()
        assertNull(component.model.value.snackBarMessage)
    }

    @Test
    fun testNavigationCallbacks() = runTest(testDispatcher) {
        coEvery { glossaryRepository.getGlossaries() } returns emptyList()

        val lifecycleRegistry = LifecycleRegistry()
        val componentContext = DefaultComponentContext(lifecycle = lifecycleRegistry)

        var importCalled = false
        var createCalled = false
        var manualCalled = false

        val component = DefaultGlossaryListComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            onNavigateImportGlossary = { importCalled = true },
            onNavigateCreateGlossary = { createCalled = true },
            onSelectGlossary = { _, _ -> },
            onSelectResource = {},
            onImportManually = { manualCalled = true }
        )

        lifecycleRegistry.resume()
        testScheduler.advanceUntilIdle()

        component.navigateImportGlossary()
        assertTrue(importCalled)

        component.navigateCreateGlossary()
        assertTrue(createCalled)

        component.navigateImportManually()
        assertTrue(manualCalled)
    }
}
