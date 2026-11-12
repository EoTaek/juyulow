package eotaek.juyulow.core.data

import eotaek.juyulow.core.model.ComparisonFuel
import eotaek.juyulow.core.model.NaviLocation
import eotaek.juyulow.core.model.NaviPoint
import eotaek.juyulow.core.model.NaviSnapshot
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.core.model.Station
import eotaek.juyulow.core.model.VehicleEnergy
import eotaek.juyulow.core.model.resolveRefuelConditions
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class KakaoDetourEtaRepositoryTest {
    @Test
    fun retriesHttpFailuresAndPreservesBatchStopAfterExhaustion() = runTest {
        for (failures in listOf(0, 1, 2, 3)) {
            var calls = 0
            val api = object : KakaoDirectionsApi {
                override suspend fun directions(
                    key: String,
                    query: Map<String, String>
                ): KakaoResponse {
                    if (++calls <= failures) throw retrofit2.HttpException(
                        retrofit2.Response.error<KakaoResponse>(429, "quota".toResponseBody())
                    )
                    return KakaoResponse(
                        listOf(
                            KakaoRoute(
                                0,
                                KakaoSummary(1200, 300, KakaoFare(100))
                            )
                        )
                    )
                }
            }
            try {
                val result = KakaoDetourEtaRepository(api, "key").estimate(
                    origin, resolveRefuelConditions(RefuelSettings())
                )
                assertEquals(1200, result.meters)
                org.junit.Assert.assertTrue(failures < 3)
            } catch (error: KakaoRequestFailure) {
                assertEquals(3, failures)
                org.junit.Assert.assertTrue(error.stopBatch)
            }
            assertEquals(minOf(failures + 1, 3), calls)
        }
    }

    private val origin = NaviSnapshot(
        NaviLocation(37.4, 127.1, "", null, 0),
        NaviPoint(37.5, 127.2, "B", "", null),
        emptyList(),
        "ROUTE_GUIDING",
        true,
        "FREE",
        0
    )

    @Test
    fun sendsCoordinatesOptionsAndRealFuelIndependentOfSearchFuel() = runTest {
        var query = emptyMap<String, String>()
        val api = object : KakaoDirectionsApi {
            override suspend fun directions(
                key: String,
                values: Map<String, String>
            ): KakaoResponse {
                assertEquals("KakaoAK development", key); query = values
                return KakaoResponse(listOf(KakaoRoute(0, KakaoSummary(1200, 300, KakaoFare(100)))))
            }
        }
        val conditions =
            resolveRefuelConditions(RefuelSettings(comparisonFuel = ComparisonFuel.DIESEL)).copy(
                energy = VehicleEnergy(codes = listOf(1))
            )
        val station = Station(
            "s",
            "S",
            "brand",
            1700,
            37.45,
            127.15,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        )
        val result =
            KakaoDetourEtaRepository(api, "development").estimate(origin, conditions, station)
        assertEquals("127.1,37.4", query["origin"]); assertEquals(
        "127.2,37.5",
        query["destination"]
    )
        assertEquals("127.15,37.45", query["waypoints"]); assertEquals("toll", query["avoid"])
        assertEquals("GASOLINE", query["car_fuel"]); assertEquals("true", query["summary"])
        assertEquals(1200, result.meters); assertEquals(300, result.seconds); assertEquals(
        100,
        result.tollWon
    )
    }

    @Test
    fun missingKeyAndInvalidCoordinatesNeverReachApi() = runTest {
        var calls = 0
        val api = object : KakaoDirectionsApi {
            override suspend fun directions(
                key: String,
                query: Map<String, String>
            ): KakaoResponse {
                calls++; error("must not call")
            }
        }
        val conditions = resolveRefuelConditions(RefuelSettings())
        try {
            KakaoDetourEtaRepository(api, "").estimate(origin, conditions); fail()
        } catch (_: IllegalStateException) {
        }
        try {
            KakaoDetourEtaRepository(
                api,
                "key"
            ).estimate(
                origin.copy(destination = origin.destination!!.copy(latitude = 100.0)),
                conditions
            ); fail()
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(0, calls)
    }

    @Test
    fun routeFailureDoesNotBecomeZeroCostSuccess() = runTest {
        val api = object : KakaoDirectionsApi {
            override suspend fun directions(key: String, query: Map<String, String>) =
                KakaoResponse(listOf(KakaoRoute(104)))
        }
        try {
            KakaoDetourEtaRepository(api, "key").estimate(
                origin,
                resolveRefuelConditions(RefuelSettings())
            ); fail()
        } catch (e: KakaoRequestFailure) {
            assertFalse(e.stopBatch)
        }
    }
}
