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
import org.bibletranslationtools.glossary.data.api.ErrorDetails
import org.bibletranslationtools.glossary.data.api.User
import org.bibletranslationtools.glossary.domain.GlossaryApi
import org.bibletranslationtools.glossary.domain.NetworkResult
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
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ChangeEmojiComponentTest {

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
        testDispatcher.settle()
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun testChangeEmojiSuccess() = runTest(testDispatcher) {
        val updatedUser = User("testuser", "😎", "token123")
        coEvery { glossaryApi.updateEmoji("😎") } returns NetworkResult.Success(updatedUser)

        var userUpdatedCalled: User? = null
        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultChangeEmojiComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            onUserUpdated = { u -> userUpdatedCalled = u }
        )

        component.changeEmoji("😎")

        // Wait for coroutine job inside component to complete
        testScheduler.advanceUntilIdle()

        // Wait for background thread
        waitForCondition {
            userUpdatedCalled != null && component.model.value.progress == null
        }

        val model = component.model.value
        assertNull(model.progress)
        assertEquals(updatedUser, userUpdatedCalled)
        verify { parentContext.navigateBack() }
    }

    @Test
    fun testChangeEmojiFailure() = runTest(testDispatcher) {
        val errorDetails = ErrorDetails("Emoji update failed", "Details")
        coEvery { glossaryApi.updateEmoji("😎") } returns NetworkResult.Error(400, errorDetails)

        var userUpdatedCalled: User? = null
        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultChangeEmojiComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            onUserUpdated = { u -> userUpdatedCalled = u }
        )

        component.changeEmoji("😎")

        // Wait for coroutine job inside component to complete
        testScheduler.advanceUntilIdle()

        // Wait for background thread
        waitForCondition {
            component.model.value.snackBarMessage != null && component.model.value.progress == null
        }

        val model = component.model.value
        assertNull(model.progress)
        assertNull(userUpdatedCalled)
        assertEquals("Emoji update failed", model.snackBarMessage)

        component.clearSnackBarMessage()
        assertNull(component.model.value.snackBarMessage)
    }
}
