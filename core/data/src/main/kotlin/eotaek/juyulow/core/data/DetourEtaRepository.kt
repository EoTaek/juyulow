package eotaek.juyulow.core.data

import eotaek.juyulow.core.model.NaviSnapshot
import eotaek.juyulow.core.model.RefuelConditions
import eotaek.juyulow.core.model.Station
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.QueryMap
import java.util.concurrent.TimeUnit

interface DetourEtaRepository {
    suspend fun estimate(
        origin: NaviSnapshot,
        conditions: RefuelConditions,
        waypoint: Station? = null
    ): RouteEstimate
}

data class RouteEstimate(
    val meters: Int,
    val seconds: Int,
    val tollWon: Int,
    val assumptions: String
)

class KakaoRequestFailure(val stopBatch: Boolean) : IllegalStateException("카카오 경로 확인 불가")

interface KakaoDirectionsApi {
    @GET("v1/directions")
    suspend fun directions(
        @Header("Authorization") key: String,
        @QueryMap query: Map<String, String>
    ): KakaoResponse
}

@Serializable
data class KakaoResponse(val routes: List<KakaoRoute> = emptyList())
@Serializable
data class KakaoRoute(val result_code: Int, val summary: KakaoSummary? = null)
@Serializable
data class KakaoSummary(val distance: Int, val duration: Int, val fare: KakaoFare)
@Serializable
data class KakaoFare(val toll: Int)

class KakaoDetourEtaRepository(private val api: KakaoDirectionsApi, private val key: String) :
    DetourEtaRepository {
    override suspend fun estimate(
        origin: NaviSnapshot,
        conditions: RefuelConditions,
        waypoint: Station?
    ): RouteEstimate {
        check(key.isNotBlank()) { "KAKAO_KEY_MISSING" }
        val destination = requireNotNull(origin.destination)
        require(origin.waypoints.isEmpty())
        fun point(lat: Double, lon: Double): String {
            require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0 && (lat != 0.0 || lon != 0.0))
            return "${lon},${lat}"
        }

        val priority = when (origin.routeOption) {
            "SHORT_TIME" -> "TIME"; "SHORT_DISTANCE" -> "DISTANCE"; else -> "RECOMMEND"
        }
        val query = mutableMapOf(
            "origin" to point(origin.location.latitude, origin.location.longitude),
            "destination" to point(destination.latitude, destination.longitude),
            "priority" to priority,
            "summary" to "true",
            "alternatives" to "false",
            "car_type" to "1",
            "car_hipass" to "false"
        )
        if (origin.routeOption == "FREE") query["avoid"] = "toll"
        // AAOS FuelType: unleaded=1, leaded=2, diesel1=3, diesel2=4, LPG=7.
        val fuels = conditions.energy.codes.mapNotNull {
            when (it) {
                1, 2 -> "GASOLINE"; 3, 4 -> "DIESEL"; 7 -> "LPG"; else -> null
            }
        }.distinct()
        if (fuels.size == 1) query["car_fuel"] = fuels.single()
        waypoint?.let { query["waypoints"] = point(it.latitude, it.longitude) }
        ApiLog.event("Kakao", "directions", "request") { query }
        try {
            return retryApiRequest("Kakao") {
                val route = api.directions("KakaoAK ${key}", query).routes.singleOrNull()
                    ?: throw KakaoRequestFailure(false)
                ApiLog.event(
                    "Kakao",
                    "directions",
                    "result"
                ) { mapOf("resultCode" to route.result_code) }
                if (route.result_code != 0) throw KakaoRequestFailure(false)
                val summary = route.summary ?: throw KakaoRequestFailure(false)
                require(summary.distance >= 0 && summary.duration >= 0 && summary.fare.toll >= 0)
                ApiLog.event(
                    "Kakao",
                    "directions",
                    "completed",
                    "I"
                ) {
                    mapOf(
                        "distanceMeters" to summary.distance,
                        "durationSeconds" to summary.duration,
                        "tollWon" to summary.fare.toll
                    )
                }
                RouteEstimate(
                    summary.distance, summary.duration, summary.fare.toll,
                    "차종 1 · 하이패스 미장착 가정" + (if (fuels.size != 1) " · 유종 기본값" else "") +
                            (if (origin.routeOption !in listOf(
                                    "RECOMMENDED",
                                    "SHORT_TIME",
                                    "SHORT_DISTANCE",
                                    "FREE"
                                )
                            ) " · 추천 경로 가정" else "")
                )
            }
        } catch (e: CancellationException) {
            ApiLog.error("Kakao", "request", e)
            throw e
        } catch (e: HttpException) {
            ApiLog.error("Kakao", "request", e)
            throw KakaoRequestFailure(e.code() in listOf(401, 403, 429))
        } catch (e: KakaoRequestFailure) {
            ApiLog.error("Kakao", "request", e)
            throw e
        } catch (e: Exception) {
            ApiLog.error("Kakao", "request", e)
            throw KakaoRequestFailure(false)
        }
    }

    companion object {
        fun create(key: String): KakaoDetourEtaRepository {
            val client = OkHttpClient.Builder().addInterceptor(ApiHttpInterceptor("Kakao"))
                .connectTimeout(10, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false).build()
            val api =
                Retrofit.Builder().baseUrl("https://apis-navi.kakaomobility.com/").client(client)
                    .addConverterFactory(Json {
                        ignoreUnknownKeys = true
                    }.asConverterFactory("application/json".toMediaType()))
                    .build().create(KakaoDirectionsApi::class.java)
            return KakaoDetourEtaRepository(api, key)
        }
    }
}
