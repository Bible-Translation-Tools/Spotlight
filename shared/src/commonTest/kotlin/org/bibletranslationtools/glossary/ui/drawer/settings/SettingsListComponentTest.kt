package org.bibletranslationtools.glossary.ui.drawer.settings

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
import org.bibletranslationtools.glossary.data.Glossary
import org.bibletranslationtools.glossary.data.Phrase
import org.bibletranslationtools.glossary.data.api.ErrorDetails
import org.bibletranslationtools.glossary.data.api.PendingPhrase
import org.bibletranslationtools.glossary.data.api.User
import org.bibletranslationtools.glossary.domain.GlossaryApi
import org.bibletranslationtools.glossary.domain.NetworkResult
import org.bibletranslationtools.glossary.ui.drawer.DrawerContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsListComponentTest {

    private val testDispatcher = StandardTestDispatcher()

    private val glossaryApi: GlossaryApi = mockk()
    private val parentContext: DrawerContext = mockk(relaxed = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        startKoin {
            modules(module {
                single { glossaryApi }
            })
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun testNavigationCallbacks() {
        var onCreateGlossaryCalled = false
        var onViewGlossariesCalled = false
        var onNavigateLoginCalled = false
        var onNavigateChangeEmojiCalled = false
        var onLogoutCalled = false
        var onEditPermissionsCalled = false
        var onReviewChangesCalled = false
        var onAdvancedSettingsCalled = false

        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultSettingsListComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            onCreateGlossary = { onCreateGlossaryCalled = true },
            onViewGlossaries = { onViewGlossariesCalled = true },
            onNavigateLogin = { onNavigateLoginCalled = true },
            onNavigateChangeEmoji = { onNavigateChangeEmojiCalled = true },
            onLogout = { onLogoutCalled = true },
            onEditPermissions = { onEditPermissionsCalled = true },
            onReviewChanges = { onReviewChangesCalled = true },
            onAdvancedSettings = { onAdvancedSettingsCalled = true }
        )

        component.createGlossary()
        assertTrue(onCreateGlossaryCalled)

        component.viewGlossaries()
        assertTrue(onViewGlossariesCalled)

        component.navigateLogin()
        assertTrue(onNavigateLoginCalled)

        component.navigateChangeEmoji()
        assertTrue(onNavigateChangeEmojiCalled)

        component.logout()
        assertTrue(onLogoutCalled)

        component.editPermissions()
        assertTrue(onEditPermissionsCalled)

        component.reviewChanges()
        assertTrue(onReviewChangesCalled)

        component.advancedSettings()
        assertTrue(onAdvancedSettingsCalled)
    }

    @Test
    fun testLoadPendingPhrasesSuccess() = runTest(testDispatcher) {
        val langEn = org.bibletranslationtools.glossary.data.Language("en", "English", "ltr")
        val langEs = org.bibletranslationtools.glossary.data.Language("es", "Spanish", "ltr")
        val glossary = Glossary("g1", langEn, langEs, 1, remoteId = "remote123")
        val phrase = Phrase("Hello")
        val user = User("u1", "😀")
        val pendingPhrases = listOf(PendingPhrase(phrase, user))

        coEvery { glossaryApi.getPendingPhrases("remote123") } returns NetworkResult.Success(pendingPhrases)

        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultSettingsListComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            onCreateGlossary = {},
            onViewGlossaries = {},
            onNavigateLogin = {},
            onNavigateChangeEmoji = {},
            onLogout = {},
            onEditPermissions = {},
            onReviewChanges = {},
            onAdvancedSettings = {}
        )

        component.loadPendingPhrases(glossary)

        // Wait for coroutine job inside component to complete
        testScheduler.advanceUntilIdle()

        // Wait for background thread
        var attempts = 0
        while (component.model.value.pendingPhrasesLoading && attempts < 100) {
            Thread.sleep(10)
            testScheduler.advanceTimeBy(10)
            attempts++
        }

        val model = component.model.value
        assertFalse(model.pendingPhrasesLoading)
        assertEquals(pendingPhrases, model.pendingPhrases)
    }

    @Test
    fun testLoadPendingPhrasesFailure() = runTest(testDispatcher) {
        val langEn = org.bibletranslationtools.glossary.data.Language("en", "English", "ltr")
        val langEs = org.bibletranslationtools.glossary.data.Language("es", "Spanish", "ltr")
        val glossary = Glossary("g1", langEn, langEs, 1, remoteId = "remote123")

        coEvery { glossaryApi.getPendingPhrases("remote123") } returns NetworkResult.Error(500, ErrorDetails("Error message", "Details"))

        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultSettingsListComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            onCreateGlossary = {},
            onViewGlossaries = {},
            onNavigateLogin = {},
            onNavigateChangeEmoji = {},
            onLogout = {},
            onEditPermissions = {},
            onReviewChanges = {},
            onAdvancedSettings = {}
        )

        component.loadPendingPhrases(glossary)

        // Wait for coroutine job inside component to complete
        testScheduler.advanceUntilIdle()

        // Wait for background thread
        var attempts = 0
        while (component.model.value.pendingPhrasesLoading && attempts < 100) {
            Thread.sleep(10)
            testScheduler.advanceTimeBy(10)
            attempts++
        }

        val model = component.model.value
        assertFalse(model.pendingPhrasesLoading)
        assertTrue(model.pendingPhrases.isEmpty())
    }
}
