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
        require(x.isFinite() && y.isFinite() && radiusKm in 1..5)
        if (key.isBlank()) throw OpinetException("KEY_MISSING")
        try {
            return api.around(key, x, y, radiusKm * 1000, fuel.opinetCode).use {
                decode(it.string())
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: OpinetException) {
            throw error
        } catch (error: HttpException) {
            throw OpinetException(
                when (error.code()) {
                    401, 403 -> "AUTH"
                    429 -> "LIMIT"
                    else -> "HTTP"
                }
            )
        } catch (_: SocketTimeoutException) {
            throw OpinetException("TIMEOUT")
        } catch (_: IOException) {
            // Do not propagate a request URL or authentication code in a nested exception.
            throw OpinetException("NETWORK")
        }
    }

    companion object {
        fun create(key: String): OpinetClient {
            // Deliberately no HTTP logger: code is an authentication query parameter.
            val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
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
            return oil.mapNotNull { element ->
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
            }.distinctBy { it.id }
        }
    }
}
