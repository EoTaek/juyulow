package eotaek.juyulow.core.data

import eotaek.juyulow.core.model.ComparisonFuel
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OpinetClientTest {
    @Test
    fun retrofitSendsAuthenticationAsCodeOnTheWire() = runTest {
        val http = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("test-key", request.url.queryParameter("code"))
            assertNull(request.url.queryParameter("certkey"))
            assertEquals("3000", request.url.queryParameter("radius"))
            assertEquals("B027", request.url.queryParameter("prodcd"))
            assertEquals("/api/aroundAll.do", request.url.encodedPath)
            okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK").body("""{"RESULT":{"OIL":[]}}""".toResponseBody()).build()
        }.build()
        try {
            val api = retrofit2.Retrofit.Builder().baseUrl("https://www.opinet.co.kr/api/")
                .client(http).build().create(OpinetApi::class.java)
            assertTrue(
                OpinetClient(api, "test-key").around(
                    314681.8, 544837.0, 3,
                    ComparisonFuel.GASOLINE
                ).isEmpty()
            )
        } finally {
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
    }

    @Test
    fun sendsSelectedRadiusAndEveryFuelCode() = runTest {
        val requests = mutableListOf<Pair<Int, String>>()
        val api = object : OpinetApi {
            override suspend fun around(
                key: String, x: Double, y: Double, radiusMeters: Int,
                fuel: String, format: String, sort: Int
            ): ResponseBody {
                assertEquals("test-key", key)
                assertEquals("json", format)
                assertEquals(2, sort)
                requests += radiusMeters to fuel
                return """{"RESULT":{"OIL":[]}}""".toResponseBody()
            }
        }
        val client = OpinetClient(api, "test-key")
        ComparisonFuel.entries.forEach { client.around(314681.8, 544837.0, 5, it) }
        assertEquals(listOf(5000 to "B027", 5000 to "B034", 5000 to "D047"), requests)
    }

    @Test
    fun emptyResponse() {
        assertTrue(OpinetClient.decode("""{"RESULT":{"OIL":[]}}""").isEmpty())
    }

    @Test
    fun normalizesNumbersAliasesDuplicatesAndInvalidPrices() {
        val station =
            """{"UNI_ID":"a","OS_NM":"station","POLL_DIV_CO":"GSC","PRICE":"1700","GIS_X_COOR":314681.8,"GIS_Y_COOR":"544837"}"""
        val invalid = station.replace("1700", "0").replace("\"a\"", "\"b\"")
        val result = OpinetClient.decode("""{"RESULT":{"OIL":[$station,$station,$invalid]}}""")
        assertEquals(1, result.size)
        assertEquals("GSC", result.single().brandCode)
        assertEquals(1700, result.single().priceWonPerL)
    }

    @Test
    fun rejectsApiErrorsWithoutLeakingResponse() {
        try {
            OpinetClient.decode("""{"RESULT":{"CODE":"secret"}}""")
            fail("Expected API error")
        } catch (error: OpinetException) {
            assertEquals("API_ERROR", error.category)
            assertFalse(error.message!!.contains("secret"))
        }
    }
}
