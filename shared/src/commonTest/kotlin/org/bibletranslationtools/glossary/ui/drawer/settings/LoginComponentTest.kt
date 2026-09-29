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
class LoginComponentTest {

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
    fun testLoginSuccess() = runTest(testDispatcher) {
        val user = User("testuser", "😀", "token123")
        coEvery { glossaryApi.login("testuser", "password123") } returns NetworkResult.Success(user)
        
        var userUpdated: User? = null
        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        
        val component = DefaultLoginComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            onUserUpdated = { u -> userUpdated = u }
        )
        
        component.login("testuser", "password123")
        
        // Wait for coroutine job inside component to complete
        testScheduler.advanceUntilIdle()
        
        // Wait for background thread (Dispatchers.Default) to finish
        waitForCondition {
            userUpdated != null && component.model.value.progress == null
        }
        
        val model = component.model.value
        assertNull(model.progress)
        assertEquals(user, userUpdated)
        assertEquals("Logged in successfully", model.snackBarMessage)
    }

    @Test
    fun testLoginFailure() = runTest(testDispatcher) {
        val errorDetails = ErrorDetails("Invalid credentials", "details")
        coEvery { glossaryApi.login("testuser", "wrong") } returns NetworkResult.Error(401, errorDetails)
        
        var userUpdated: User? = null
        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        
        val component = DefaultLoginComponent(
            componentContext = componentContext,
            parentContext = parentContext,
            onUserUpdated = { u -> userUpdated = u }
        )
        
        component.login("testuser", "wrong")
        
        // Wait for coroutine job inside component to complete
        testScheduler.advanceUntilIdle()
        
        // Wait for background thread (Dispatchers.Default) to finish
        waitForCondition {
            component.model.value.snackBarMessage != null && component.model.value.progress == null
        }
        
        val model = component.model.value
        assertNull(model.progress)
        assertNull(userUpdated)
        assertEquals("Invalid credentials", model.snackBarMessage)
        
        // Test clear snackbar
        component.clearSnackBarMessage()
        assertNull(component.model.value.snackBarMessage)
    }
}
