package org.bibletranslationtools.glossary.ui.drawer.settings

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import io.github.vinceglb.filekit.PlatformFile
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
import org.bibletranslationtools.glossary.data.Glossary
import org.bibletranslationtools.glossary.data.Resource
import org.bibletranslationtools.glossary.domain.FileSystemProvider
import org.bibletranslationtools.glossary.domain.GlossaryApi
import org.bibletranslationtools.glossary.domain.usecases.ImportGlossary
import org.bibletranslationtools.glossary.ui.components.OtpAction
import org.bibletranslationtools.glossary.ui.drawer.DrawerContext
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ImportGlossaryComponentTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val importGlossary: ImportGlossary = mockk()
    private val glossaryApi: GlossaryApi = mockk()
    private val fileSystemProvider: FileSystemProvider = mockk()
    private val parentContext: DrawerContext = mockk(relaxed = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        
        startKoin {
            modules(module {
                single { importGlossary }
                single { glossaryApi }
                single { fileSystemProvider }
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
    fun testOtpActions() {
        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultImportGlossaryComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            autoImportManually = false,
            onSelectGlossary = { _, _ -> },
            onSelectResource = {},
            onImportFinished = {}
        )
        
        // Initial state
        assertEquals(listOf(null, null, null, null, null), component.model.value.otpCode)
        assertNull(component.model.value.focusedIndex)
        
        // Enter character at index 0
        component.onOtpAction(OtpAction.OnChangeFieldFocused(0))
        component.onOtpAction(OtpAction.OnEnterChar("A", 0))
        assertEquals(listOf("A", null, null, null, null), component.model.value.otpCode)
        assertEquals(1, component.model.value.focusedIndex)
        
        // Enter character at index 1
        component.onOtpAction(OtpAction.OnEnterChar("B", 1))
        assertEquals(listOf("A", "B", null, null, null), component.model.value.otpCode)
        assertEquals(2, component.model.value.focusedIndex)
        
        // Simulate backspace when focus is at index 2 (value at index 2 is null)
        component.onOtpAction(OtpAction.OnKeyboardBack)
        assertEquals(listOf("A", null, null, null, null), component.model.value.otpCode)
        assertEquals(1, component.model.value.focusedIndex)
    }

    @Test
    fun testOnImportClicked() = runTest(testDispatcher) {
        val file: PlatformFile = mockk()
        val glossary: Glossary = mockk()
        val resource: Resource = mockk()
        val importResult = ImportGlossary.Result(glossary, resource)
        
        coEvery { importGlossary.findExisting(file) } returns null
        coEvery { importGlossary(file) } returns importResult
        
        var selectedGlossary: Glossary? = null
        var selectedResource: Resource? = null
        var importFinished = false
        
        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultImportGlossaryComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            autoImportManually = false,
            onSelectGlossary = { g, _ -> selectedGlossary = g },
            onSelectResource = { r -> selectedResource = r },
            onImportFinished = { importFinished = true }
        )
        
        component.onImportClicked(file)
        
        // Wait for coroutines to yield/complete
        testScheduler.advanceUntilIdle()
        
        // Wait for Dispatchers.Default inside import logic to finish
        waitForCondition {
            importFinished && component.model.value.progress == null
        }
        
        val model = component.model.value
        assertNull(model.progress)
        assertEquals(glossary, selectedGlossary)
        assertEquals(resource, selectedResource)
        assertTrue(importFinished)
        
        coVerify { importGlossary(file) }
    }

    @Test
    fun testOnImportClickedExistingGlossaryRequestsOverwrite() = runTest(testDispatcher) {
        val file: PlatformFile = mockk()
        val existing: Glossary = mockk()
        val glossary: Glossary = mockk()
        val resource: Resource = mockk()

        every { existing.code } returns "G1"
        coEvery { importGlossary.findExisting(file) } returns existing
        coEvery { importGlossary(file) } returns ImportGlossary.Result(glossary, resource)

        var importFinished = false

        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultImportGlossaryComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            autoImportManually = false,
            onSelectGlossary = { _, _ -> },
            onSelectResource = {},
            onImportFinished = { importFinished = true }
        )

        component.onImportClicked(file)
        testScheduler.advanceUntilIdle()

        waitForCondition { component.model.value.overwriteRequest != null }

        assertEquals(existing, component.model.value.overwriteRequest)
        assertFalse(importFinished)
        coVerify(exactly = 0) { importGlossary(file) }

        component.onOverwriteConfirmed()
        testScheduler.advanceUntilIdle()

        waitForCondition {
            importFinished && component.model.value.progress == null
        }

        assertNull(component.model.value.overwriteRequest)
        coVerify(exactly = 1) { importGlossary(file) }
    }

    @Test
    fun testOverwriteDismissedSkipsImport() = runTest(testDispatcher) {
        val file: PlatformFile = mockk()
        val existing: Glossary = mockk()

        coEvery { importGlossary.findExisting(file) } returns existing

        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultImportGlossaryComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            autoImportManually = false,
            onSelectGlossary = { _, _ -> },
            onSelectResource = {},
            onImportFinished = {}
        )

        component.onImportClicked(file)
        testScheduler.advanceUntilIdle()

        waitForCondition { component.model.value.overwriteRequest != null }

        component.onOverwriteDismissed()
        // Confirm after dismiss must be a no-op
        component.onOverwriteConfirmed()
        testScheduler.advanceUntilIdle()

        assertNull(component.model.value.overwriteRequest)
        assertNull(component.model.value.progress)
        coVerify(exactly = 0) { importGlossary(file) }
    }
}
