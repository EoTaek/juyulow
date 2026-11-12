package eotaek.juyulow.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.time.Duration.Companion.milliseconds

/** Two retries after the initial request; cancellation never starts another attempt. */
internal suspend fun <T> retryApiRequest(provider: String, request: suspend () -> T): T {
    repeat(3) { attempt ->
        currentCoroutineContext().ensureActive()
        try {
            return request()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (attempt == 2) throw error
            val delayMillis = 500L * (attempt + 1)
            ApiLog.event(provider, "request", "retry", "W") {
                mapOf("retry" to attempt + 1, "delayMillis" to delayMillis)
            }
            delay(delayMillis.milliseconds)
        }
    }
    error("Unreachable")
}
