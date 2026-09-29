package org.bibletranslationtools.glossary.ui.drawer.settings

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.bibletranslationtools.glossary.domain.CatalogApi
import org.bibletranslationtools.glossary.domain.FileSystemProvider
import org.bibletranslationtools.glossary.domain.GlossaryApi
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository
import org.bibletranslationtools.glossary.domain.usecases.ExportGlossary
import org.bibletranslationtools.glossary.domain.usecases.ImportGlossary
import org.bibletranslationtools.glossary.platform.ResourceContainerAccessor
import org.bibletranslationtools.glossary.ui.ParentContext
import org.bibletranslationtools.glossary.ui.main.SettingsIntent
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsComponentTest {

    private val testDispatcher = StandardTestDispatcher()

    private val glossaryRepository: GlossaryRepository = mockk(relaxed = true)
    private val catalogApi: CatalogApi = mockk(relaxed = true)
    private val fileSystemProvider: FileSystemProvider = mockk(relaxed = true)
    private val resourceContainerAccessor: ResourceContainerAccessor = mockk(relaxed = true)
    private val exportGlossaryUseCase: ExportGlossary = mockk(relaxed = true)
    private val importGlossaryUseCase: ImportGlossary = mockk(relaxed = true)
    private val glossaryApi: GlossaryApi = mockk(relaxed = true)
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
                single { catalogApi }
                single { fileSystemProvider }
                single { resourceContainerAccessor }
                single { exportGlossaryUseCase }
                single { importGlossaryUseCase }
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
    fun testInitialConfigurations() = runTest(testDispatcher) {
        val lifecycle = LifecycleRegistry()
        
        // 1. Index configuration
        val componentIndex = DefaultSettingsComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            parentContext = parentContext,
            intent = SettingsIntent.Index,
            onSelectResource = {},
            onSelectGlossary = { _, _ -> },
            onFullscreen = {},
            onImportFinished = {},
            onUserUpdated = {},
            onLogout = {}
        )
        assertTrue(componentIndex.childStack.value.active.instance is SettingsComponent.Child.Settings)

        // 2. ImportGlossary configuration
        val componentImport = DefaultSettingsComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            parentContext = parentContext,
            intent = SettingsIntent.ImportGlossary,
            onSelectResource = {},
            onSelectGlossary = { _, _ -> },
            onFullscreen = {},
            onImportFinished = {},
            onUserUpdated = {},
            onLogout = {}
        )
        assertTrue(componentImport.childStack.value.active.instance is SettingsComponent.Child.ImportGlossary)

        // 3. CreateGlossary configuration
        val componentCreate = DefaultSettingsComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            parentContext = parentContext,
            intent = SettingsIntent.CreateGlossary,
            onSelectResource = {},
            onSelectGlossary = { _, _ -> },
            onFullscreen = {},
            onImportFinished = {},
            onUserUpdated = {},
            onLogout = {}
        )
        assertTrue(componentCreate.childStack.value.active.instance is SettingsComponent.Child.CreateGlossary)
    }

    @Test
    fun testDismissAndNavigateBack() = runTest(testDispatcher) {
        val lifecycle = LifecycleRegistry()
        
        val component = DefaultSettingsComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            parentContext = parentContext,
            intent = SettingsIntent.Index,
            onSelectResource = {},
            onSelectGlossary = { _, _ -> },
            onFullscreen = {},
            onImportFinished = {},
            onUserUpdated = {},
            onLogout = {}
        )

        // Initial back stack is empty, so navigateBack should dismiss
        component.navigateBack()
        verify { parentContext.dismissDrawer() }

        // Test explicit dismiss
        component.dismiss()
        verify(exactly = 2) { parentContext.dismissDrawer() }
    }
}
