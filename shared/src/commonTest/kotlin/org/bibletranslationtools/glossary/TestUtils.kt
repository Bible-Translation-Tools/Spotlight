package org.bibletranslationtools.glossary

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope

/**
 * Advances the test scheduler until [condition] holds or [timeoutMs] passes.
 *
 * Components hop to real threads (withContext(Dispatchers.Default/IO), and compose-resources'
 * getString() since 1.12), which the test scheduler can't drive. Wait for the expected outcome
 * rather than for a busy flag to clear: the flag may not even be set yet when the wait starts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.waitForCondition(timeoutMs: Long = 5000, condition: () -> Boolean) {
    var waited = 0L
    while (!condition() && waited < timeoutMs) {
        Thread.sleep(10)
        testScheduler.advanceTimeBy(10)
        waited += 10
    }
}

/**
 * Lets work a test left behind finish while Koin and the test Main dispatcher are still set.
 *
 * Components launch in their own CoroutineScope, so follow-up work (e.g. a reload after an
 * upload) can outlive the test and otherwise hit stopKoin()/resetMain() of the next one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestDispatcher.settle(timeoutMs: Long = 50) {
    var waited = 0L
    while (waited < timeoutMs) {
        Thread.sleep(10)
        scheduler.advanceTimeBy(10)
        waited += 10
    }
}
