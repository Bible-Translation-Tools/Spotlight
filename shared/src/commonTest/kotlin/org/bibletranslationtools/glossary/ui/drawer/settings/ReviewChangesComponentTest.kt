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
import org.bibletranslationtools.glossary.data.Phrase
import org.bibletranslationtools.glossary.data.api.ErrorDetails
import org.bibletranslationtools.glossary.data.api.PendingPhrase
import org.bibletranslationtools.glossary.data.api.PhraseReview
import org.bibletranslationtools.glossary.data.api.ReviewStatus
import org.bibletranslationtools.glossary.data.api.User
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewChangesComponentTest {

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
    fun testLoadPendingPhrasesSuccess() = runTest(testDispatcher) {
        val langEn = Language("en", "English", "ltr")
        val langEs = Language("es", "Spanish", "ltr")
        val glossary = Glossary("g1", langEn, langEs, 1, remoteId = "rem1")

        val phrase1 = Phrase("phrase1", id = "p1")
        val phrase2 = Phrase("phrase2", id = "p2")
        val user = User("u1", "😀")
        
        // Let's have one phrase with original = null and one with original != null to test sort
        val pending1 = PendingPhrase(phrase1, user, original = Phrase("original1"))
        val pending2 = PendingPhrase(phrase2, user, original = null)

        // getPendingPhrases should return unsorted, and we assert they get sorted (original == null first)
        coEvery { glossaryApi.getPendingPhrases("rem1") } returns NetworkResult.Success(listOf(pending1, pending2))

        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultReviewChangesComponent(
            componentContext = componentContext,
            parentContext = parentContext
        )

        component.loadPendingPhrases(glossary, isRefreshing = false)

        testScheduler.advanceUntilIdle()

        waitForCondition {
            component.model.value.pendingPhrases.isNotEmpty() && !component.model.value.isLoading
        }

        val model = component.model.value
        assertFalse(model.isLoading)
        assertFalse(model.isRefreshing)
        assertNull(model.progress)
        
        assertEquals(2, model.pendingPhrases.size)
        // pending1 has original != null (false), pending2 has original == null (true).
        // Since false < true, pending1 should be first.
        assertEquals(pending1, model.pendingPhrases[0])
        assertEquals(pending2, model.pendingPhrases[1])
    }

    @Test
    fun testSaveReviewStatusSuccessNotEmpty() = runTest(testDispatcher) {
        val langEn = Language("en", "English", "ltr")
        val langEs = Language("es", "Spanish", "ltr")
        val glossary = Glossary("g1", langEn, langEs, 1, remoteId = "rem1")
        glossaryStateHolder.setGlossary(glossary)

        val phrase = Phrase("phrase1", id = "p1")
        val user = User("u1", "😀")
        val pending = PendingPhrase(phrase, user)

        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultReviewChangesComponent(
            componentContext = componentContext,
            parentContext = parentContext
        )

        // Prepopulate pending phrases in component state by loading
        coEvery { glossaryApi.getPendingPhrases("rem1") } returns NetworkResult.Success(listOf(pending))
        component.loadPendingPhrases(glossary, isRefreshing = false)
        testScheduler.advanceUntilIdle()

        waitForCondition {
            component.model.value.pendingPhrases.isNotEmpty() && !component.model.value.isLoading
        }

        assertEquals(1, component.model.value.pendingPhrases.size)

        // Mock saving review status returning some reviews
        val review = PhraseReview("phrase1", ReviewStatus.APPROVED, User("reviewer", "😎"))
        coEvery { glossaryApi.reviewPendingPhrase("rem1", any()) } returns NetworkResult.Success(listOf(review))

        component.saveReviewStatus(pending, ReviewStatus.APPROVED)
        testScheduler.advanceUntilIdle()

        waitForCondition {
            component.model.value.pendingPhrases.firstOrNull()?.reviews?.isNotEmpty() == true &&
                component.model.value.progress == null
        }

        val model = component.model.value
        assertNull(model.progress)
        assertEquals(1, model.pendingPhrases.size)
        assertEquals(listOf(review), model.pendingPhrases[0].reviews)
    }

    @Test
    fun testSaveReviewStatusSuccessEmptyRemovesPhrase() = runTest(testDispatcher) {
        val langEn = Language("en", "English", "ltr")
        val langEs = Language("es", "Spanish", "ltr")
        val glossary = Glossary("g1", langEn, langEs, 1, remoteId = "rem1")
        glossaryStateHolder.setGlossary(glossary)

        val phrase = Phrase("phrase1", id = "p1")
        val user = User("u1", "😀")
        val pending = PendingPhrase(phrase, user)

        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultReviewChangesComponent(
            componentContext = componentContext,
            parentContext = parentContext
        )

        coEvery { glossaryApi.getPendingPhrases("rem1") } returns NetworkResult.Success(listOf(pending))
        component.loadPendingPhrases(glossary, isRefreshing = false)
        testScheduler.advanceUntilIdle()

        waitForCondition {
            component.model.value.pendingPhrases.isNotEmpty() && !component.model.value.isLoading
        }

        assertEquals(1, component.model.value.pendingPhrases.size)

        // Mock saving review status returning empty reviews (phrase is fully reviewed/resolved)
        coEvery { glossaryApi.reviewPendingPhrase("rem1", any()) } returns NetworkResult.Success(emptyList())

        component.saveReviewStatus(pending, ReviewStatus.APPROVED)
        testScheduler.advanceUntilIdle()

        waitForCondition {
            component.model.value.pendingPhrases.isEmpty() && component.model.value.progress == null
        }

        val model = component.model.value
        assertNull(model.progress)
        assertTrue(model.pendingPhrases.isEmpty()) // The phrase was removed!
    }

    @Test
    fun testSaveReviewStatusFailure() = runTest(testDispatcher) {
        val langEn = Language("en", "English", "ltr")
        val langEs = Language("es", "Spanish", "ltr")
        val glossary = Glossary("g1", langEn, langEs, 1, remoteId = "rem1")
        glossaryStateHolder.setGlossary(glossary)

        val phrase = Phrase("phrase1", id = "p1")
        val user = User("u1", "😀")
        val pending = PendingPhrase(phrase, user)

        val componentContext = DefaultComponentContext(lifecycle = LifecycleRegistry())
        val component = DefaultReviewChangesComponent(
            componentContext = componentContext,
            parentContext = parentContext
        )

        coEvery { glossaryApi.getPendingPhrases("rem1") } returns NetworkResult.Success(listOf(pending))
        component.loadPendingPhrases(glossary, isRefreshing = false)
        testScheduler.advanceUntilIdle()

        waitForCondition {
            component.model.value.pendingPhrases.isNotEmpty() && !component.model.value.isLoading
        }

        coEvery { glossaryApi.reviewPendingPhrase("rem1", any()) } returns NetworkResult.Error(500, ErrorDetails("Review failed", "Details"))

        component.saveReviewStatus(pending, ReviewStatus.APPROVED)
        testScheduler.advanceUntilIdle()

        waitForCondition {
            component.model.value.snackBarMessage != null && component.model.value.progress == null
        }

        val model = component.model.value
        assertNull(model.progress)
        assertEquals("Review failed", model.snackBarMessage)
        assertEquals(1, model.pendingPhrases.size) // Phrase not removed on failure

        component.clearSnackBarMessage()
        assertNull(component.model.value.snackBarMessage)
    }
}
