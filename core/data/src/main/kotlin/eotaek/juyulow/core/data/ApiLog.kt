package eotaek.juyulow.core.data

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicLong

fun interface ApiLogger {
    fun log(
        level: String,
        provider: String,
        operation: String,
        event: String,
        fields: () -> Map<String, Any?>
    )
}

object NoOpApiLogger : ApiLogger {
    override fun log(
        level: String,
        provider: String,
        operation: String,
        event: String,
        fields: () -> Map<String, Any?>
    ) = Unit
}

/** Configured once before startup. Never supply authentication or raw exception messages. */
object ApiLog {
    @Volatile
    var logger: ApiLogger = NoOpApiLogger

    /** Enable temporarily in a debugger when investigating individual calls/callbacks. */
    @Volatile
    var detailed: Boolean = false
    private val ids = AtomicLong()
    private fun shouldLog(
        level: String,
        provider: String,
        operation: String,
        event: String
    ): Boolean {
        return detailed || level == "W" || level == "E" || when (event) {
            "completed" -> operation != "refresh"
            "candidates_selected" -> true
            "returned" -> operation == "initialize" && provider != "Navi"
            else -> false
        }
    }

    fun id(): Long = ids.incrementAndGet()
    fun event(
        provider: String,
        operation: String,
        event: String,
        level: String = "D",
        fields: () -> Map<String, Any?> = { emptyMap() }
    ) {
        if (!shouldLog(level, provider, operation, event)) return
        try {
            logger.log(level, provider, operation, event, fields)
        } catch (_: Exception) {
        }
    }

    fun error(provider: String, operation: String, error: Throwable) = event(
        provider,
        operation,
        when (error) {
            is kotlinx.coroutines.TimeoutCancellationException, is java.net.SocketTimeoutException -> "timeout"; is kotlinx.coroutines.CancellationException -> "cancelled"; else -> "failed"
        },
        if (error is kotlinx.coroutines.CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) "D" else "W"
    ) {
        mapOf(
            "exceptionType" to error.javaClass.simpleName,
            "reason" to when (error) {
                is OpinetException -> error.category; is KakaoRequestFailure -> "KAKAO_FAILURE"; else -> null
            },
            "stopBatch" to (error as? KakaoRequestFailure)?.stopBatch,
            "frames" to if (detailed) error.stackTrace.take(8).joinToString(" | ") else null
        )
    }

    inline fun <T> call(
        provider: String,
        operation: String,
        crossinline fields: () -> Map<String, Any?> = { emptyMap() },
        block: () -> T
    ): T {
        val id = id()
        val start = System.nanoTime()
        event(provider, operation, "request") { fields() + ("callId" to id) }
        try {
            return block().also {
                event(provider, operation, "returned") {
                    mapOf(
                        "callId" to id,
                        "elapsedMs" to (System.nanoTime() - start) / 1_000_000
                    )
                }
            }
        } catch (error: Exception) {
            error(provider, operation, error); throw error
        }
    }
}

/** Does not consume bodies, headers or query strings. */
class ApiHttpInterceptor(private val provider: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val id = ApiLog.id()
        val start = System.nanoTime()
        ApiLog.event(provider, "http", "request") {
            mapOf(
                "callId" to id,
                "method" to request.method,
                "host" to request.url.host,
                "path" to request.url.encodedPath
            )
        }
        try {
            return chain.proceed(request).also { response ->
                ApiLog.event(
                    provider,
                    "http",
                    "response",
                    if (response.isSuccessful) "D" else "W"
                ) {
                    mapOf(
                        "callId" to id,
                        "status" to response.code,
                        "elapsedMs" to (System.nanoTime() - start) / 1_000_000
                    )
                }
            }
        } catch (error: Exception) {
            ApiLog.event(provider, "http", "failed", "W") {
                mapOf(
                    "callId" to id,
                    "exceptionType" to error.javaClass.simpleName
                )
            }
            throw error
        }
    }
}
