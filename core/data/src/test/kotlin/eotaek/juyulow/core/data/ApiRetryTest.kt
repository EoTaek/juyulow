package eotaek.juyulow.core.data

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ApiRetryTest {
    @Test
    fun succeedsOnThirdAttemptWithBackoff() = runTest {
        var calls = 0
        val result = retryApiRequest("test") {
            if (++calls < 3) throw IOException()
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, calls)
        assertEquals(1500L, testScheduler.currentTime)
    }

    @Test
    fun stopsAfterThirdFailureAndPreservesError() = runTest {
        var calls = 0
        val failure = IOException()
        try {
            retryApiRequest("test") { calls++; throw failure }
            fail()
        } catch (error: IOException) {
            assertSame(failure, error)
        }
        assertEquals(3, calls)
    }

    @Test
    fun cancellationIsNotRetried() = runTest {
        var calls = 0
        val job = launch {
            retryApiRequest("test") { calls++; awaitCancellation() }
        }
        runCurrent()
        job.cancel()
        job.join()
        assertEquals(1, calls)
    }

    @Test
    fun cancellationDuringBackoffPreventsNextRequest() = runTest {
        var calls = 0
        val job = launch {
            retryApiRequest("test") { calls++; throw IOException() }
        }
        runCurrent()
        job.cancel()
        job.join()
        testScheduler.advanceUntilIdle()
        assertEquals(1, calls)
    }
}
