package org.bibletranslationtools.glossary.ui.read

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.bibletranslationtools.glossary.data.Phrase
import org.bibletranslationtools.glossary.data.RefOption
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository
import org.bibletranslationtools.glossary.ui.ParentContext
import org.bibletranslationtools.glossary.ui.main.MainStateKeeper
import org.bibletranslationtools.glossary.ui.main.ReadIntent
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
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ReadComponentTest {

    private val testDispatcher = StandardTestDispatcher()

    private val glossaryRepository: GlossaryRepository = mockk(relaxed = true)
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
    fun testInitializationWithReferenceIntent() = runTest(testDispatcher) {
        val dummyRef = mockk<RefOption>(relaxed = true)
        val intent = ReadIntent.Reference(dummyRef)
        val sharedState = MainStateKeeper()

        val component = DefaultReadComponent(
            componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry()),
            parentContext = parentContext,
            intent = intent,
            sharedState = sharedState,
            onNavigateViewPhrase = {},
            onNavigateEditPhrase = {}
        )

        testScheduler.advanceUntilIdle()

        // Verify the active child is Config.Index with the correct reference
        val activeChild = component.childStack.value.active.instance
        assertIs<ReadComponent.Child.Index>(activeChild)
        // DefaultReadIndexComponent has model containing currentRef equal to dummyRef
        assertEquals(dummyRef, activeChild.component.model.value.currentRef)
    }

    @Test
    fun testInitializationWithIndexIntent() = runTest(testDispatcher) {
        val intent = ReadIntent.Index
        val sharedState = MainStateKeeper()

        val component = DefaultReadComponent(
            componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry()),
            parentContext = parentContext,
            intent = intent,
            sharedState = sharedState,
            onNavigateViewPhrase = {},
            onNavigateEditPhrase = {}
        )

        testScheduler.advanceUntilIdle()

        val activeChild = component.childStack.value.active.instance
        assertIs<ReadComponent.Child.Index>(activeChild)
        assertNull(activeChild.component.model.value.currentRef)
    }

    @Test
    fun testNavigationFlow() = runTest(testDispatcher) {
        val sharedState = MainStateKeeper()
        var viewPhraseCalled: Phrase? = null
        var editPhraseCalled: Phrase? = null

        val component = DefaultReadComponent(
            componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry()),
            parentContext = parentContext,
            intent = ReadIntent.Index,
            sharedState = sharedState,
            onNavigateViewPhrase = { viewPhraseCalled = it },
            onNavigateEditPhrase = { editPhraseCalled = it }
        )

        testScheduler.advanceUntilIdle()

        // 1. Initially on Index screen
        var activeChild = component.childStack.value.active.instance
        assertIs<ReadComponent.Child.Index>(activeChild)

        // 2. Navigate to Browse screen from Index component
        activeChild.component.onBrowseClick("gen", 2)
        testScheduler.advanceUntilIdle()

        // Verify active child is now Browse screen
        var browseChild = component.childStack.value.active.instance
        assertIs<ReadComponent.Child.Browse>(browseChild)

        // 3. Trigger navigate back (onBackClick) on Browse screen
        browseChild.component.onBackClick()
        testScheduler.advanceUntilIdle()

        // Verify we are back to Index screen
        activeChild = component.childStack.value.active.instance
        assertIs<ReadComponent.Child.Index>(activeChild)

        // 4. Navigate back to Browse screen
        activeChild.component.onBrowseClick("gen", 2)
        testScheduler.advanceUntilIdle()

        browseChild = component.childStack.value.active.instance
        assertIs<ReadComponent.Child.Browse>(browseChild)

        // 5. Trigger onRefClick on Browse screen -> should replace stack with Index(ref)
        val dummyRef = mockk<RefOption>(relaxed = true)
        browseChild.component.onRefClick(dummyRef)
        testScheduler.advanceUntilIdle()

        // Verify active child is now Index screen with the new ref
        activeChild = component.childStack.value.active.instance
        assertIs<ReadComponent.Child.Index>(activeChild)
        assertEquals(dummyRef, activeChild.component.model.value.currentRef)
    }
}
