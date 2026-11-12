package eotaek.juyulow

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import eotaek.juyulow.core.model.NaviResult
import eotaek.juyulow.core.model.NaviSnapshot
import eotaek.juyulow.navigation.NaviClientListener
import eotaek.juyulow.navigation.NaviTripLedger
import eotaek.juyulow.navigation.PleosNaviClient
import eotaek.juyulow.navigation.PleosNavigationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in; a single coordinate-only route mutation, restored in finally. */
class CoordinateOnlyNavigationPocTest {
    @Test
    fun probe() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("coordinateOnlyProbe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val car = Car.createCar(context)
        val reader = PleosNavigationRepository(PleosNaviClient(context), NaviTripLedger())
        val original: NaviSnapshot
        try {
            val ux = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            check(!ux.currentCarUxRestrictions.isRequiresDistractionOptimization) { "Parking required" }
            original = (reader.initialize() as NaviResult.Success).value
            check(original.waypoints.isEmpty()) { "PoC requires no existing waypoints" }
            original.destination?.let { check(it.subId != null) { "Cannot restore original" } }
        } finally {
            reader.release(); car.disconnect()
        }

        val latitude = args.getString("latitude")?.toDouble() ?: 37.535225
        val longitude = args.getString("longitude")?.toDouble() ?: 127.009125
        require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0)
        val client = PleosNaviClient(context)
        val connected = CompletableDeferred<Unit>()
        val events = Channel<Any>(Channel.UNLIMITED)
        var mutated = false
        suspend fun destination(): DestinationInfo {
            while (events.tryReceive().isSuccess) { /* discard previous observations */
            }
            withContext(Dispatchers.Main) { client.getDestinationInfo() }
            return withTimeout(10_000) {
                var found: DestinationInfo? = null
                while (found == null) {
                    val e = events.receive(); if (e is DestinationInfo) found = e
                }
                found
            }
        }
        try {
            withContext(Dispatchers.Main) {
                client.initialize(object : NaviClientListener {
                    override fun onConnected() {
                        connected.complete(Unit)
                    }

                    override fun onConnectionFailed(cause: Throwable) {
                        connected.completeExceptionally(cause)
                    }

                    override fun onTripInfo(tripInfo: TripInfo) {
                        events.trySend(tripInfo)
                    }

                    override fun onDestinationInfo(destinationInfo: DestinationInfo) {
                        events.trySend(destinationInfo)
                    }

                    override fun onError(errorCode: NaviErrorCode) {
                        Log.i("CoordinateOnlyPoc", "SDK_ERROR=$errorCode")
                        events.trySend(errorCode)
                    }
                })
            }
            withTimeout(10_000) { connected.await() }
            Log.i(
                "CoordinateOnlyPoc",
                "INPUT lat=$latitude lon=$longitude poiId=empty tripSubId=null routeSubId=empty"
            )
            withContext(Dispatchers.Main) {
                client.requestTrip(TripRequest(latitude, longitude, "", "CoordinateOnlyPoc", null))
            }
            val trip = withTimeoutOrNull(30_000) { events.receive() }
            if (trip is TripInfo) {
                Log.i(
                    "CoordinateOnlyPoc",
                    "TRIP distance=${trip.distance} eta=${trip.eta} points=${trip.summary.routePoints}"
                )
            } else Log.i("CoordinateOnlyPoc", "TRIP_NO_RESULT=$trip")

            mutated = true
            withContext(Dispatchers.Main) {
                client.requestRoute(
                    RouteInfo(
                        latitude = latitude, longitude = longitude,
                        poiName = "CoordinateOnlyPoc", poiId = "", poiSubId = "", address = "",
                        routeOption = RouteOption.valueOf(original.routeOption)
                    )
                )
            }
            var applied = false
            repeat(30) {
                if (!applied) {
                    delay(1_000)
                    val d = destination()
                    Log.i("CoordinateOnlyPoc", "ROUTE_OBSERVATION=$d")
                    applied = d.name == "CoordinateOnlyPoc" && d.poiId.isBlank() &&
                            kotlin.math.abs(d.latitude - latitude) <= 0.000001 &&
                            kotlin.math.abs(d.longitude - longitude) <= 0.000001 && d.remainDistance > 0
                }
            }
            assertTrue(
                "No coordinate-only Trip with distance and ETA",
                trip is TripInfo && trip.distance > 0 && trip.eta > 0
            )
            assertTrue("Coordinate-only destination was not observed", applied)
        } finally {
            try {
                if (mutated) {
                    val saved = original.destination
                    withContext(Dispatchers.Main) {
                        if (saved == null) client.cancelRoute()
                        else client.requestRoute(
                            RouteInfo(
                                latitude = saved.latitude,
                                longitude = saved.longitude,
                                poiName = saved.name,
                                poiId = saved.poiId,
                                poiSubId = requireNotNull(saved.subId),
                                address = saved.address,
                                poiProvider = saved.provider,
                                routeOption = RouteOption.valueOf(original.routeOption)
                            )
                        )
                    }
                    var restored = false
                    repeat(15) {
                        if (!restored) {
                            delay(1_000)
                            val d = destination()
                            restored = if (saved == null) d.poiId.isBlank() && d.name.isBlank()
                            else d.poiId == saved.poiId && d.poiSubId == saved.subId && d.name == saved.name
                        }
                    }
                    Log.i("CoordinateOnlyPoc", "RESTORED=$restored")
                    assertTrue("Original destination was not restored", restored)
                }
            } finally {
                withContext(Dispatchers.Main) { client.release() }; events.close()
            }
        }
    }
}
