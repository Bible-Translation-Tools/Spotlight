package org.bibletranslationtools.glossary.ui.drawer.settings

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.bibletranslationtools.glossary.data.Language
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository
import org.bibletranslationtools.glossary.ui.drawer.DrawerContext
import org.bibletranslationtools.glossary.settle
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class SelectLanguageComponentTest {

    private val testDispatcher = StandardTestDispatcher()

    private val glossaryRepository: GlossaryRepository = mockk()
    private val parentContext: DrawerContext = mockk(relaxed = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        startKoin {
            modules(module {
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
    fun testInitializationLoadsLanguages() = runTest(testDispatcher) {
        val languages = listOf(
            Language("en", "English", "ltr"),
            Language("es", "Spanish", "ltr")
        )
        coEvery { glossaryRepository.getAllLanguages() } returns languages

        val sharedState = CreateGlossaryStateKeeper()
        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        
        val component = DefaultSelectLanguageComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            type = LanguageType.SOURCE,
            sharedState = sharedState
        )

        testScheduler.advanceUntilIdle()

        var attempts = 0
        while (component.model.value.isLoading && attempts < 100) {
            Thread.sleep(10)
            testScheduler.advanceTimeBy(10)
            attempts++
        }

        val model = component.model.value
        assertFalse(model.isLoading)
        assertEquals(LanguageType.SOURCE, model.type)
        assertEquals(languages, model.languages)
    }

    @Test
    fun testLanguageSelection() = runTest(testDispatcher) {
        val languages = listOf(
            Language("en", "English", "ltr"),
            Language("es", "Spanish", "ltr")
        )
        coEvery { glossaryRepository.getAllLanguages() } returns languages

        val sharedState = CreateGlossaryStateKeeper()
        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        
        val component = DefaultSelectLanguageComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            type = LanguageType.SOURCE,
            sharedState = sharedState
        )

        testScheduler.advanceUntilIdle()

        var attempts = 0
        while (component.model.value.isLoading && attempts < 100) {
            Thread.sleep(10)
            testScheduler.advanceTimeBy(10)
            attempts++
        }

        val selectedLanguage = languages[1] // Spanish
        component.onLanguageClick(selectedLanguage)

        assertEquals(selectedLanguage, sharedState.model.value.sourceLanguage)
        verify { parentContext.navigateBack() }
    }
}
