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
import org.bibletranslationtools.glossary.data.Language
import org.bibletranslationtools.glossary.data.api.ErrorDetails
import org.bibletranslationtools.glossary.data.api.GlossaryUser
import org.bibletranslationtools.glossary.data.api.User
import org.bibletranslationtools.glossary.data.api.UserRole
import org.bibletranslationtools.glossary.domain.GlossaryApi
import org.bibletranslationtools.glossary.domain.NetworkResult
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
class EditPermissionsComponentTest {

    private val testDispatcher = StandardTestDispatcher()

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
    fun testLoadGlossaryUsersSuccess() = runTest(testDispatcher) {
        val langEn = Language("en", "English", "ltr")
        val langEs = Language("es", "Spanish", "ltr")
        val glossary = Glossary("g1", langEn, langEs, 1, remoteId = "rem1")

        val glossaryUser = GlossaryUser(User("u1", "😀"), UserRole.EDITOR)
        coEvery { glossaryApi.getGlossaryUsers("rem1") } returns NetworkResult.Success(listOf(glossaryUser))

        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultEditPermissionsComponent(
            componentContext = componentContext,
            parentContext = parentContext
        )

        component.loadGlossaryUsers(glossary)
        testScheduler.advanceUntilIdle()

        waitForCondition {
            glossaryStateHolder.state.value.users.isNotEmpty() && !component.model.value.isRefreshing
        }

        val model = component.model.value
        assertFalse(model.isRefreshing)
        assertEquals(listOf(glossaryUser), glossaryStateHolder.state.value.users)
    }

    @Test
    fun testUpdateUserRoleSuccess() = runTest(testDispatcher) {
        val langEn = Language("en", "English", "ltr")
        val langEs = Language("es", "Spanish", "ltr")
        val glossary = Glossary("g1", langEn, langEs, 1, remoteId = "rem1")

        val user = User("u1", "😀")
        val glossaryUser = GlossaryUser(user, UserRole.VIEWER)
        val updatedGlossaryUser = GlossaryUser(user, UserRole.ADMIN)

        coEvery { glossaryApi.updateUserRole("rem1", "u1", UserRole.ADMIN) } returns NetworkResult.Success(listOf(updatedGlossaryUser))

        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultEditPermissionsComponent(
            componentContext = componentContext,
            parentContext = parentContext
        )

        component.updateUserRole(glossary, glossaryUser, UserRole.ADMIN)
        testScheduler.advanceUntilIdle()

        waitForCondition {
            component.model.value.snackBarMessage != null && component.model.value.progress == null
        }

        val model = component.model.value
        assertNull(model.progress)
        assertNotNull(model.snackBarMessage)
        assertTrue(model.snackBarMessage.isNotEmpty())
        assertEquals(listOf(updatedGlossaryUser), glossaryStateHolder.state.value.users)
    }

    @Test
    fun testUpdateUserRoleFailure() = runTest(testDispatcher) {
        val langEn = Language("en", "English", "ltr")
        val langEs = Language("es", "Spanish", "ltr")
        val glossary = Glossary("g1", langEn, langEs, 1, remoteId = "rem1")

        val user = User("u1", "😀")
        val glossaryUser = GlossaryUser(user, UserRole.VIEWER)

        coEvery { glossaryApi.updateUserRole("rem1", "u1", UserRole.ADMIN) } returns NetworkResult.Error(400, ErrorDetails("Failed to update role", "Details"))

        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultEditPermissionsComponent(
            componentContext = componentContext,
            parentContext = parentContext
        )

        component.updateUserRole(glossary, glossaryUser, UserRole.ADMIN)
        testScheduler.advanceUntilIdle()

        waitForCondition {
            component.model.value.snackBarMessage != null && component.model.value.progress == null
        }

        val model = component.model.value
        assertNull(model.progress)
        assertEquals("Failed to update role", model.snackBarMessage)
        assertTrue(glossaryStateHolder.state.value.users.isEmpty())

        component.clearSnackBarMessage()
        assertNull(component.model.value.snackBarMessage)
    }
}
