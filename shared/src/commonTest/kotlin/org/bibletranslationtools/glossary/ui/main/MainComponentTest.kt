package org.bibletranslationtools.glossary.ui.main

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.bibletranslationtools.glossary.data.Glossary
import org.bibletranslationtools.glossary.data.Language
import org.bibletranslationtools.glossary.data.api.GlossaryUser
import org.bibletranslationtools.glossary.data.api.User
import org.bibletranslationtools.glossary.data.api.UserRole
import org.bibletranslationtools.glossary.domain.GlossaryApi
import org.bibletranslationtools.glossary.domain.NetworkResult
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MainComponentTest {

    private val testDispatcher = StandardTestDispatcher()

    private val glossaryApi: GlossaryApi = mockk(relaxed = true)
    private val glossaryRepository: GlossaryRepository = mockk(relaxed = true)

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

    private fun TestScope.waitForCondition(condition: () -> Boolean) {
        var attempts = 0
        while (!condition() && attempts < 100) {
            Thread.sleep(10)
            testScheduler.advanceTimeBy(10)
            attempts++
        }
    }

    @Test
    fun testInitialization() = runTest(testDispatcher) {
        var finishedCalled = false
        val component = DefaultMainComponent(
            componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry()),
            onFinished = { finishedCalled = true }
        )

        testScheduler.advanceUntilIdle()

        // 1. Initially child stack contains Read child
        val activeChild = component.childStack.value.active.instance
        assertIs<MainComponent.Child.Read>(activeChild)

        // 2. Drawer is initially closed / null
        assertNull(component.drawerSlot.value.child)
        assertFalse(component.model.value.keyTermsDrawerOpen)
        assertFalse(component.model.value.settingsDrawerOpen)
        assertFalse(component.model.value.fullscreenDrawer)
    }

    @Test
    fun testDrawerActivationAndDismissal() = runTest(testDispatcher) {
        val component = DefaultMainComponent(
            componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry()),
            onFinished = {}
        )

        testScheduler.advanceUntilIdle()

        // 1. Open settings drawer
        component.openSettings()
        testScheduler.advanceUntilIdle()

        val settingsDrawer = component.drawerSlot.value.child
        assertNotNull(settingsDrawer)
        assertIs<DrawerConfig.Settings>(settingsDrawer.configuration)

        // 2. Open key terms drawer (replaces settings drawer)
        component.openKeyTerms()
        testScheduler.advanceUntilIdle()

        val keyTermsDrawer = component.drawerSlot.value.child
        assertNotNull(keyTermsDrawer)
        assertIs<DrawerConfig.KeyTerms>(keyTermsDrawer.configuration)

        // 3. Dismiss drawer
        component.dismissDrawer()
        testScheduler.advanceUntilIdle()

        assertNull(component.drawerSlot.value.child)
    }

    @Test
    fun testVerifyLoginSuccess() = runTest(testDispatcher) {
        val component = DefaultMainComponent(
            componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry()),
            onFinished = {}
        )

        val dummyUser = User("testuser", "😎", "token123")
        coEvery { glossaryApi.verifyLogin("token123") } returns (NetworkResult.Success(dummyUser) as NetworkResult<User>)

        userStateHolder.setUser(null)

        component.verifyLogin("token123")
        testScheduler.advanceUntilIdle()
        waitForCondition { userStateHolder.state.value.user != null }

        val activeUser = userStateHolder.state.value.user
        assertNotNull(activeUser)
        assertEquals("testuser", activeUser.username)
        assertEquals("token123", activeUser.token)
    }

    @Test
    fun testVerifyLoginFailure() = runTest(testDispatcher) {
        val component = DefaultMainComponent(
            componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry()),
            onFinished = {}
        )

        coEvery { glossaryApi.verifyLogin("invalid_token") } returns (NetworkResult.Error(401, mockk(relaxed = true)) as NetworkResult<User>)

        userStateHolder.setUser(User("olduser", "😎", "old_token"))

        component.verifyLogin("invalid_token")
        testScheduler.advanceUntilIdle()
        waitForCondition { userStateHolder.state.value.user == null }

        assertNull(userStateHolder.state.value.user)
    }

    @Test
    fun testGetGlossaryUsers() = runTest(testDispatcher) {
        val component = DefaultMainComponent(
            componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry()),
            onFinished = {}
        )

        val language = Language("en", "English", "ltr")
        val glossary = Glossary("g1", language, language, 1, remoteId = "rem1", id = "g1_id")

        val dummyUsers = listOf(
            GlossaryUser(User("user1", "😀", ""), UserRole.VIEWER),
            GlossaryUser(User("user2", "🎉", ""), UserRole.ADMIN)
        )
        coEvery { glossaryApi.getGlossaryUsers("rem1") } returns (NetworkResult.Success(dummyUsers) as NetworkResult<List<GlossaryUser>>)

        component.getGlossaryUsers(glossary)
        testScheduler.advanceUntilIdle()
        waitForCondition { glossaryStateHolder.state.value.users.isNotEmpty() }

        assertEquals(dummyUsers, glossaryStateHolder.state.value.users)
    }

    @Test
    fun testFullscreenDrawerUpdate() = runTest(testDispatcher) {
        val component = DefaultMainComponent(
            componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry()),
            onFinished = {}
        )

        assertFalse(component.model.value.fullscreenDrawer)

        component.setFullscreenDrawer(true)
        testScheduler.advanceUntilIdle()

        assertTrue(component.model.value.fullscreenDrawer)
    }
}
