package eotaek.juyulow.core.data

import eotaek.juyulow.core.model.ComparisonFuel
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

interface OpinetApi {
    @GET("aroundAll.do")
    suspend fun around(
        @Query("code") key: String,
        @Query("x") x: Double,
        @Query("y") y: Double,
        @Query("radius") radiusMeters: Int,
        @Query("prodcd") fuel: String,
        @Query("out") format: String = "json",
        @Query("sort") sort: Int = 2,
    ): ResponseBody
}

/** KATEC coordinates only. These are neither WGS84 nor navigation POI identifiers. */
data class OpinetStation(
    val id: String, val name: String, val brandCode: String,
    val priceWonPerL: Int, val x: Double, val y: Double,
)

class OpinetException(val category: String) : IOException("OPINET_$category")

class OpinetClient(private val api: OpinetApi, private val key: String) {
    suspend fun around(
        x: Double,
        y: Double,
        radiusKm: Int,
        fuel: ComparisonFuel
    ): List<OpinetStation> {
        ApiLog.event("Opinet", "around", "request") {
            mapOf(
                "x" to x,
                "y" to y,
                "coordinateSystem" to "KATEC",
                "radiusKm" to radiusKm,
                "fuel" to fuel.opinetCode
            )
        }
        require(x.isFinite() && y.isFinite() && radiusKm in 1..5)
        if (key.isBlank()) {
            ApiLog.event("Opinet", "around", "blocked", "W") { mapOf("reason" to "KEY_MISSING") }
            throw OpinetException("KEY_MISSING")
        }
        try {
            return api.around(key, x, y, radiusKm * 1000, fuel.opinetCode).use {
                decode(it.string()).also { stations ->
                    ApiLog.event(
                        "Opinet",
                        "around",
                        "completed",
                        "I"
                    ) { mapOf("validCount" to stations.size) }
                    stations.forEachIndexed { index, station ->
                        ApiLog.event(
                            "Opinet",
                            "around",
                            "item"
                        ) {
                            mapOf(
                                "index" to index,
                                "id" to station.id,
                                "name" to station.name,
                                "priceWonPerL" to station.priceWonPerL,
                                "x" to station.x,
                                "y" to station.y
                            )
                        }
                    }
                }
            }
        } catch (error: CancellationException) {
            ApiLog.error("Opinet", "request", error)
            throw error
        } catch (error: OpinetException) {
            ApiLog.error("Opinet", "request", error)
            throw error
        } catch (error: HttpException) {
            ApiLog.error("Opinet", "request", error)
            throw OpinetException(
                when (error.code()) {
                    401, 403 -> "AUTH"
                    429 -> "LIMIT"
                    else -> "HTTP"
                }
            )
        } catch (error: SocketTimeoutException) {
            ApiLog.error("Opinet", "around", error)
            throw OpinetException("TIMEOUT")
        } catch (error: IOException) {
            ApiLog.error("Opinet", "around", error)
            // Do not propagate a request URL or authentication code in a nested exception.
            throw OpinetException("NETWORK")
        }
    }

    companion object {
        fun create(key: String): OpinetClient {
            // Metadata only: authentication query and bodies are excluded.
            val http = OkHttpClient.Builder().addInterceptor(ApiHttpInterceptor("Opinet"))
                .connectTimeout(10, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
            val api = Retrofit.Builder().baseUrl("https://www.opinet.co.kr/api/")
                .client(http).build().create(OpinetApi::class.java)
            return OpinetClient(api, key)
        }

        internal fun decode(body: String): List<OpinetStation> {
            val root = try {
                Json.parseToJsonElement(body).jsonObject
            } catch (_: Exception) {
                throw OpinetException("INVALID_RESPONSE")
            }
            val result = root["RESULT"] as? JsonObject ?: throw OpinetException("INVALID_RESPONSE")
            val oil = result["OIL"] as? JsonArray ?: throw OpinetException("API_ERROR")
            val valid = oil.mapNotNull { element ->
                val row = element as? JsonObject ?: return@mapNotNull null
                fun value(name: String) = (row[name] as? JsonPrimitive)?.contentOrNull
                val id = value("UNI_ID")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val name = value("OS_NM")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val price =
                    value("PRICE")?.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                val x = value("GIS_X_COOR")?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
                    ?: return@mapNotNull null
                val y = value("GIS_Y_COOR")?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
                    ?: return@mapNotNull null
                OpinetStation(
                    id,
                    name,
                    value("POLL_DIV_CD") ?: value("POLL_DIV_CO") ?: "ETC",
                    price,
                    x,
                    y
                )
            }
            return valid.distinctBy { it.id }.also { unique ->
                ApiLog.event("Opinet", "decode", "summary") {
                    mapOf(
                        "receivedCount" to oil.size,
                        "invalidCount" to oil.size - valid.size,
                        "duplicateCount" to valid.size - unique.size
                    )
                }
            }
        }
    }
}
