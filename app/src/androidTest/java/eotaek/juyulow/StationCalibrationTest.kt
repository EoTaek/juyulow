package eotaek.juyulow

import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.RoutePointType
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointInfo
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import eotaek.juyulow.navigation.NaviClientListener
import eotaek.juyulow.navigation.PleosNaviClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** Opt-in measurement tool. Requires an empty original route and restores it after every candidate. */
class StationCalibrationTest {
    @Test
    fun captureCoordinates() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("stationCalibration") == "true")
        val inputs = JSONArray(
            String(
                android.util.Base64.decode(
                    requireNotNull(args.getString("inputs")), android.util.Base64.DEFAULT
                ), Charsets.UTF_8
            )
        )
        check(inputs.length() == 8)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val car = Car.createCar(context)
        val client = PleosNaviClient(context)
        var connected = CompletableDeferred<Unit>()
        var destination = CompletableDeferred<DestinationInfo>()
        var routeState = CompletableDeferred<RouteStateInfo>()
        var waypoints = CompletableDeferred<List<WaypointInfo>>()
        var trip = CompletableDeferred<TripInfo>()
        var changed = false
        suspend fun read(): Pair<DestinationInfo, RouteStateInfo> {
            destination = CompletableDeferred(); routeState = CompletableDeferred(); waypoints =
                CompletableDeferred()
            withContext(Dispatchers.Main) { client.getDestinationInfo(); client.getRouteStateInfo(); client.getWaypointInfo() }
            return withTimeout(10.seconds) {
                check(
                    waypoints.await().isEmpty()
                ); destination.await() to routeState.await()
            }
        }

        suspend fun emptyRoute() {
            withContext(Dispatchers.Main) { client.cancelRoute() }
            withTimeout(40.seconds) {
                while (true) {
                    delay(500)
                    val (d, r) = read()
                    if (d.poiId.isBlank() && d.name.isBlank() && r.naviMode == NaviMode.SAFE_GUIDING) break
                }
            }
            Log.i("StationCalibration", "original empty route restored")
        }
        try {
            val restrictions =
                car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            check(!restrictions.currentCarUxRestrictions.isRequiresDistractionOptimization)
            withContext(Dispatchers.Main) {
                client.initialize(object : NaviClientListener {
                    override fun onConnected() {
                        connected.complete(Unit)
                    }

                    override fun onConnectionFailed(cause: Throwable) {
                        connected.completeExceptionally(cause)
                    }

                    override fun onDestinationInfo(destinationInfo: DestinationInfo) {
                        destination.complete(destinationInfo)
                    }

                    override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
                        routeState.complete(routeStateInfo)
                    }

                    override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) {
                        waypoints.complete(waypointInfoList)
                    }

                    override fun onTripInfo(tripInfo: TripInfo) {
                        trip.complete(tripInfo)
                    }
                })
            }
            withTimeout(10.seconds) { connected.await() }
            val original = read()
            check(original.first.poiId.isBlank() && original.first.name.isBlank() && original.second.naviMode == NaviMode.SAFE_GUIDING) {
                "An empty original route is required; refusing to overwrite it"
            }
            for (index in 0 until inputs.length()) {
                val input = inputs.getJSONObject(index)
                val id = input.getString("id")
                val name = input.getString("name")
                val lat = input.getDouble("lat")
                val lon = input.getDouble("lon")
                trip = CompletableDeferred()
                withContext(Dispatchers.Main) {
                    client.requestTrip(
                        TripRequest(
                            lat,
                            lon,
                            id,
                            name,
                            "0"
                        )
                    )
                }
                val t = withTimeout(30.seconds) { trip.await() }
                val point = t.summary.routePoints.single()
                check(point.poiId == id && point.pointType == RoutePointType.DESTINATION && t.distance >= 0 && t.eta >= 0)
                changed = true
                withContext(Dispatchers.Main) {
                    client.requestRoute(
                        RouteInfo(
                            latitude = lat,
                            longitude = lon,
                            poiName = name,
                            poiId = id,
                            poiSubId = "0",
                            address = "",
                            poiProvider = "",
                            routeOption = original.second.routeOption
                        )
                    )
                }
                var observed: DestinationInfo? = null
                withTimeout(40.seconds) {
                    while (observed == null) {
                        delay(500)
                        val (d, r) = read()
                        if (d.poiId == id && r.naviMode == NaviMode.ROUTE_GUIDING) observed = d
                    }
                }
                val d = checkNotNull(observed)
                check(d.latitude.isFinite() && d.longitude.isFinite() && d.latitude != 0.0 && d.longitude != 0.0)
                Log.i(
                    "StationCalibration",
                    JSONObject().put("id", id).put("name", name).put("lat", lat).put("lon", lon)
                        .put("tripLat", point.latitude).put("tripLon", point.longitude)
                        .put("routeLat", d.latitude).put("routeLon", d.longitude)
                        .put("meters", t.distance).put("seconds", t.eta).toString()
                )
                emptyRoute(); changed = false
            }
        } finally {
            try {
                if (changed) withContext(NonCancellable) { emptyRoute() }
            } finally {
                withContext(Dispatchers.Main) { client.release() }; car.disconnect()
            }
        }
    }
}
