package org.bibletranslationtools.glossary.ui.drawer.keyterms

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.bibletranslationtools.glossary.BaseTest
import org.bibletranslationtools.glossary.data.Glossary
import org.bibletranslationtools.glossary.data.Language
import org.bibletranslationtools.glossary.data.Phrase
import org.bibletranslationtools.glossary.data.api.GlossaryUpdate
import org.bibletranslationtools.glossary.data.api.GlossaryUser
import org.bibletranslationtools.glossary.data.api.GlossaryVersion
import org.bibletranslationtools.glossary.data.api.User
import org.bibletranslationtools.glossary.data.api.UserRole
import org.bibletranslationtools.glossary.domain.FileSystemProvider
import org.bibletranslationtools.glossary.domain.GlossaryApi
import org.bibletranslationtools.glossary.domain.NetworkResult
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository
import org.bibletranslationtools.glossary.domain.usecases.ExportGlossary
import org.bibletranslationtools.glossary.domain.usecases.ImportGlossary
import org.bibletranslationtools.glossary.ui.drawer.DrawerContext
import org.bibletranslationtools.glossary.ui.main.MainStateKeeper
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
class KeyTermsListComponentTest : BaseTest() {

    private val testDispatcher = StandardTestDispatcher()

    private val glossaryRepository: GlossaryRepository = mockk(relaxed = true)
    private val importGlossaryUseCase: ImportGlossary = mockk(relaxed = true)
    private val fileSystemProvider: FileSystemProvider = mockk(relaxed = true)
    private val glossaryApi: GlossaryApi = mockk(relaxed = true)
    private val exportGlossaryUseCase: ExportGlossary = mockk(relaxed = true)
    private val parentContext: DrawerContext = mockk(relaxed = true)

    private lateinit var appStateStore: AppStateStore
    private lateinit var glossaryStateHolder: GlossaryStateHolderImpl
    private lateinit var resourceStateHolder: ResourceStateHolderImpl
    private lateinit var userStateHolder: UserStateHolderImpl

    @BeforeTest
    override fun setUp() {
        super.setUp()
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
                single { importGlossaryUseCase }
                single { fileSystemProvider }
                single { glossaryApi }
                single { exportGlossaryUseCase }
            })
        }
    }

    @AfterTest
    override fun tearDown() {
        testDispatcher.settle()
        stopKoin()
        Dispatchers.resetMain()
        super.tearDown()
    }

    @Test
    fun testInitializationLoadsPhrases() = runTest(testDispatcher) {
        val langEn = Language("en", "English", "ltr")
        val langEs = Language("es", "Spanish", "ltr")
        val glossary = Glossary("g1", langEn, langEs, 1, remoteId = "rem1", id = "g1_id")
        glossaryStateHolder.setGlossary(glossary)

        val phrase1 = Phrase("tit", id = "p1")
        val phrase2 = Phrase("abc", id = "p2")

        coEvery { glossaryRepository.getPhrases("g1_id") } returns listOf(phrase1)
        coEvery { glossaryRepository.getPendingPhrases("g1_id") } returns listOf(phrase2)
        coEvery { glossaryApi.getPendingPhrases("rem1") } returns NetworkResult.Success(emptyList())
        coEvery { glossaryApi.getReviewedPhrases("rem1") } returns NetworkResult.Success(emptyList())

        val lifecycleRegistry = LifecycleRegistry()
        val component = DefaultKeyTermsListComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycleRegistry),
            parentContext = parentContext,
            onNavigateImportGlossary = {},
            onNavigateCreateGlossary = {},
            onNavigateSearchPhrases = {},
            onNavigateViewPhrase = {},
            onSelectGlossary = { _, _ -> },
            onSelectResource = {},
            sharedState = MainStateKeeper()
        )

        lifecycleRegistry.resume()
        testScheduler.advanceUntilIdle()

        // Wait for background IO and Default dispatchers to complete
        var attempts = 0
        while ((component.model.value.isLoading || component.model.value.isRemoteLoading) && attempts < 150) {
            Thread.sleep(10)
            testScheduler.advanceTimeBy(10)
            attempts++
        }

        val model = component.model.value
        assertFalse(model.isLoading)
        assertFalse(model.isRemoteLoading)
        assertEquals(2, model.phrases.size)
        // Sorted ascending: "abc", "tit"
        assertEquals("abc", model.phrases[0].phrase)
        assertEquals("tit", model.phrases[1].phrase)
    }

    @Test
    fun testUploadGlossarySuccess() = runTest(testDispatcher) {
        val langEn = Language("en", "English", "ltr")
        val langEs = Language("es", "Spanish", "ltr")
        val glossary = Glossary("g1", langEn, langEs, 1, remoteId = null, id = "g1_id")
        glossaryStateHolder.setGlossary(glossary)

        coEvery { glossaryRepository.getPhrases("g1_id") } returns emptyList()
        coEvery { glossaryRepository.getPendingPhrases("g1_id") } returns emptyList()

        val tempFile = kotlinx.io.files.Path(testRootDir, "upload.zip")
        coEvery { fileSystemProvider.createTempFile("upload", ".zip") } returns tempFile
        
        // Setup PlatformFile that points to our real file.
        // On JVM, PlatformFile(Path) works and reads directly from filesystem.
        // We write dummy bytes so exists() is true and size() > 0.
        coEvery { exportGlossaryUseCase(glossary, any()) } answers {
            java.io.File(tempFile.toString()).writeText("dummy text")
        }

        val glossaryVersion = GlossaryVersion("remote_id_123", 2)
        coEvery { glossaryApi.uploadGlossary(any()) } returns NetworkResult.Success(glossaryVersion)
        coEvery { glossaryRepository.addGlossary(any()) } returns "added_id"

        val lifecycleRegistry = LifecycleRegistry()
        val component = DefaultKeyTermsListComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycleRegistry),
            parentContext = parentContext,
            onNavigateImportGlossary = {},
            onNavigateCreateGlossary = {},
            onNavigateSearchPhrases = {},
            onNavigateViewPhrase = {},
            onSelectGlossary = { _, _ -> },
            onSelectResource = {},
            sharedState = MainStateKeeper()
        )

        lifecycleRegistry.resume()
        testScheduler.advanceUntilIdle()

        component.uploadGlossary()
        testScheduler.advanceUntilIdle()

        waitForCondition {
            component.model.value.snackBarMessage != null && component.model.value.progress == null
        }

        val model = component.model.value
        assertNull(model.progress)
        assertNotNull(model.snackBarMessage)
        assertTrue(model.snackBarMessage.isNotEmpty())

        val updatedGlossary = glossaryStateHolder.state.value.glossary
        assertNotNull(updatedGlossary)
        assertEquals("remote_id_123", updatedGlossary.remoteId)
        assertEquals(2, updatedGlossary.version)
    }

    @Test
    fun testCheckForUpdatesSuccess() = runTest(testDispatcher) {
        val langEn = Language("en", "English", "ltr")
        val langEs = Language("es", "Spanish", "ltr")
        val glossary = Glossary("g1", langEn, langEs, 1, remoteId = "rem1", id = "g1_id")
        glossaryStateHolder.setGlossary(glossary)

        coEvery { glossaryRepository.getPhrases("g1_id") } returns emptyList()
        coEvery { glossaryRepository.getPendingPhrases("g1_id") } returns emptyList()

        val update = GlossaryUpdate("rem1", 1, 0, 0)
        coEvery { glossaryApi.checkUpdates(any()) } returns NetworkResult.Success(listOf(update))

        val lifecycleRegistry = LifecycleRegistry()
        val component = DefaultKeyTermsListComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycleRegistry),
            parentContext = parentContext,
            onNavigateImportGlossary = {},
            onNavigateCreateGlossary = {},
            onNavigateSearchPhrases = {},
            onNavigateViewPhrase = {},
            onSelectGlossary = { _, _ -> },
            onSelectResource = {},
            sharedState = MainStateKeeper()
        )

        lifecycleRegistry.resume()
        testScheduler.advanceUntilIdle()

        component.checkForUpdates()
        testScheduler.advanceUntilIdle()

        waitForCondition {
            component.model.value.snackBarMessage != null && component.model.value.progress == null
        }

        val model = component.model.value
        assertNull(model.progress)
        assertTrue(model.glossaryHasUpdate)
        assertNotNull(model.snackBarMessage)

        // Test clearHasUpdate
        component.clearHasUpdate()
        assertFalse(component.model.value.glossaryHasUpdate)
    }

    @Test
    fun testJoinGlossarySuccess() = runTest(testDispatcher) {
        val langEn = Language("en", "English", "ltr")
        val langEs = Language("es", "Spanish", "ltr")
        val glossary = Glossary("g1", langEn, langEs, 1, remoteId = "rem1", id = "g1_id")
        glossaryStateHolder.setGlossary(glossary)

        coEvery { glossaryRepository.getPhrases("g1_id") } returns emptyList()
        coEvery { glossaryRepository.getPendingPhrases("g1_id") } returns emptyList()

        val glossaryUser = GlossaryUser(User("u1", "😀"), UserRole.EDITOR)
        coEvery { glossaryApi.joinGlossary("rem1") } returns NetworkResult.Success(listOf(glossaryUser))

        val lifecycleRegistry = LifecycleRegistry()
        val component = DefaultKeyTermsListComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycleRegistry),
            parentContext = parentContext,
            onNavigateImportGlossary = {},
            onNavigateCreateGlossary = {},
            onNavigateSearchPhrases = {},
            onNavigateViewPhrase = {},
            onSelectGlossary = { _, _ -> },
            onSelectResource = {},
            sharedState = MainStateKeeper()
        )

        lifecycleRegistry.resume()
        testScheduler.advanceUntilIdle()

        component.joinGlossary()
        testScheduler.advanceUntilIdle()

        waitForCondition {
            component.model.value.snackBarMessage != null && component.model.value.progress == null
        }

        val model = component.model.value
        assertNull(model.progress)
        assertEquals(listOf(glossaryUser), glossaryStateHolder.state.value.users)
        assertNotNull(model.snackBarMessage)
    }

    @Test
    fun testClearReviewedPhrasesSuccess() = runTest(testDispatcher) {
        val langEn = Language("en", "English", "ltr")
        val langEs = Language("es", "Spanish", "ltr")
        val glossary = Glossary("g1", langEn, langEs, 1, remoteId = "rem1", id = "g1_id")
        glossaryStateHolder.setGlossary(glossary)

        coEvery { glossaryRepository.getPhrases("g1_id") } returns emptyList()
        coEvery { glossaryRepository.getPendingPhrases("g1_id") } returns emptyList()
        coEvery { glossaryApi.deleteReviewedPhrases("rem1") } returns NetworkResult.Success(true)

        val lifecycleRegistry = LifecycleRegistry()
        val component = DefaultKeyTermsListComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycleRegistry),
            parentContext = parentContext,
            onNavigateImportGlossary = {},
            onNavigateCreateGlossary = {},
            onNavigateSearchPhrases = {},
            onNavigateViewPhrase = {},
            onSelectGlossary = { _, _ -> },
            onSelectResource = {},
            sharedState = MainStateKeeper()
        )

        lifecycleRegistry.resume()
        testScheduler.advanceUntilIdle()

        component.clearReviewedPhrases()
        testScheduler.advanceUntilIdle()

        var attempts = 0
        while (component.model.value.isLoading && attempts < 100) {
            Thread.sleep(10)
            testScheduler.advanceTimeBy(10)
            attempts++
        }

        val model = component.model.value
        assertFalse(model.isLoading)
        assertNotNull(model.snackBarMessage)
    }

    @Test
    fun testNavigationCallbacks() = runTest(testDispatcher) {
        val lifecycleRegistry = LifecycleRegistry()
        var importCalled = false
        var createCalled = false
        var searchCalled = false
        var viewPhraseCalled: Phrase? = null

        val component = DefaultKeyTermsListComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycleRegistry),
            parentContext = parentContext,
            onNavigateImportGlossary = { importCalled = true },
            onNavigateCreateGlossary = { createCalled = true },
            onNavigateSearchPhrases = { searchCalled = true },
            onNavigateViewPhrase = { p -> viewPhraseCalled = p },
            onSelectGlossary = { _, _ -> },
            onSelectResource = {},
            sharedState = MainStateKeeper()
        )

        lifecycleRegistry.resume()
        testScheduler.advanceUntilIdle()

        component.navigateImportGlossary()
        assertTrue(importCalled)

        component.navigateCreateGlossary()
        assertTrue(createCalled)

        component.navigateSearchPhrases()
        assertTrue(searchCalled)

        val phrase = Phrase("test")
        component.navigateViewPhrase(phrase)
        assertEquals(phrase, viewPhraseCalled)
    }
}
