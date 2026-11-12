package eotaek.juyulow.core.data

import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiLogTest {
    @Before
    fun details() {
        ApiLog.detailed = true
    }

    @After
    fun reset() {
        ApiLog.logger = NoOpApiLogger; ApiLog.detailed = false
    }

    @Test
    fun summaryKeepsResultsAndErrorsWithoutEvaluatingSuppressedFields() {
        ApiLog.detailed = false
        val events = mutableListOf<String>()
        ApiLog.logger = ApiLogger { _, _, _, event, fields -> fields(); events += event }
        for (event in listOf(
            "request",
            "returned",
            "callback",
            "item",
            "converted",
            "cache_hit",
            "cancelled"
        )) {
            ApiLog.event("Navi", "trip", event) { error("suppressed fields evaluated") }
        }
        ApiLog.event("Navi", "refresh", "completed", "I") { error("refresh fields evaluated") }
        ApiLog.event("Navi", "trips", "completed", "I")
        ApiLog.event("Journey", "search", "candidates_selected", "I")
        ApiLog.event("Location", "initialize", "returned", "I")
        ApiLog.event("Kakao", "http", "response", "W")
        ApiLog.error("Navi", "trip", java.net.SocketTimeoutException())
        ApiLog.error("Navi", "trip", CancellationException())
        assertEquals(
            listOf("completed", "candidates_selected", "returned", "response", "timeout"),
            events
        )
    }

    @Test
    fun noOpDoesNotEvaluateFieldsAndBrokenLoggerDoesNotBreakCalls() {
        ApiLog.logger = NoOpApiLogger
        ApiLog.event("Test", "read", "request") { error("must not evaluate") }
        ApiLog.logger = ApiLogger { _, _, _, _, _ -> error("logger unavailable") }
        assertEquals(42, ApiLog.call("Test", "read") { 42 })
    }

    @Test
    fun httpMetadataOmitsCredentialsAndDoesNotConsumeBody() {
        val lines = mutableListOf<String>()
        ApiLog.logger = ApiLogger { _, _, _, event, fields -> lines += event + fields() }
        val http = OkHttpClient.Builder().addInterceptor(ApiHttpInterceptor("Opinet"))
            .addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(429).message("secret-response")
                    .body("body-still-readable".toResponseBody()).build()
            }.build()
        try {
            http.newCall(
                Request.Builder().url("https://example.com/api/aroundAll.do?code=secret-query")
                    .header("Authorization", "secret-header").build()
            ).execute().use {
                assertEquals("body-still-readable", it.body!!.string())
            }
            assertTrue(lines.joinToString().contains("429"))
            assertFalse(lines.joinToString().contains("secret"))
            assertFalse(lines.joinToString().contains("body-still-readable"))
        } finally {
            http.dispatcher.executorService.shutdown(); http.connectionPool.evictAll()
        }
    }

    @Test
    fun errorsExcludeMessagesAndCausesAndCancellationPropagates() {
        val lines = mutableListOf<String>()
        ApiLog.logger = ApiLogger { _, _, _, event, fields -> lines += event + fields() }
        ApiLog.error("Test", "read", IllegalStateException("secret-url", Exception("secret-key")))
        val cancel = CancellationException("secret-cancel")
        try {
            ApiLog.call("Test", "read") { throw cancel }
        } catch (actual: CancellationException) {
            assertSame(cancel, actual)
        }
        assertTrue(lines.joinToString().contains("cancelled"))
        assertFalse(lines.joinToString().contains("secret"))
    }
}
