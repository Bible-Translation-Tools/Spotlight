package org.bibletranslationtools.glossary.ui

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
import kotlinx.datetime.LocalDateTime
import org.bibletranslationtools.glossary.data.Resource
import org.bibletranslationtools.glossary.domain.GlossaryApi
import org.bibletranslationtools.glossary.domain.InitApp
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository
import org.bibletranslationtools.glossary.platform.ResourceContainerAccessor
import org.bibletranslationtools.glossary.ui.state.AppStateStore
import org.bibletranslationtools.glossary.ui.state.AppStateStoreImpl
import org.bibletranslationtools.glossary.ui.state.GlossaryStateHolderImpl
import org.bibletranslationtools.glossary.ui.state.ResourceStateHolderImpl
import org.bibletranslationtools.glossary.ui.state.UserStateHolderImpl
import org.bibletranslationtools.glossary.settle
import org.bibletranslationtools.glossary.waitForCondition
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class RootComponentTest {

    private val testDispatcher = StandardTestDispatcher()

    private val glossaryApi: GlossaryApi = mockk(relaxed = true)
    private val glossaryRepository: GlossaryRepository = mockk(relaxed = true)
    private val initApp: InitApp = mockk(relaxed = true)
    private val resourceContainerAccessor: ResourceContainerAccessor = mockk(relaxed = true)

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
                single { initApp }
                single { resourceContainerAccessor }
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
    fun testRootComponentNavigationFlow() = runTest(testDispatcher) {
        var finishedCalled = false
        val component = DefaultRootComponent(
            componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry()),
            onFinished = { finishedCalled = true }
        )

        testScheduler.advanceUntilIdle()

        // 1. Initially child stack contains Splash child
        var activeChild = component.stack.value.active.instance
        assertIs<RootComponent.Child.Splash>(activeChild)

        // Setup mock configurations for initializeApp
        val resource = Resource(
            lang = "en",
            type = "ulb",
            version = "1.0",
            format = "usfm",
            url = "",
            filename = "en_ulb.zip",
            createdAt = LocalDateTime(2024, 1, 1, 0, 0),
            modifiedAt = LocalDateTime(2024, 1, 1, 0, 0),
            books = emptyList()
        )
        coEvery { initApp.invoke(any()) } coAnswers {
            firstArg<(String) -> Unit>().invoke("Init...")
        }
        coEvery { glossaryRepository.getResource("en", "ulb") } returns resource
        coEvery { resourceContainerAccessor.read("en_ulb.zip") } returns resource

        // 2. Trigger initializeApp on Splash screen -> will transition stack to Main child
        activeChild.component.initializeApp("en_ulb", null)
        testScheduler.advanceUntilIdle()
        waitForCondition { component.stack.value.active.instance is RootComponent.Child.Main }

        // Verify the stack transitioned to Main child
        activeChild = component.stack.value.active.instance
        assertIs<RootComponent.Child.Main>(activeChild)
    }
}
