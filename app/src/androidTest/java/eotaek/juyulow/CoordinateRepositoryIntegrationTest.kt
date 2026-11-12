package eotaek.juyulow

import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.RouteInfo
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import eotaek.juyulow.core.data.KatecCoordinates
import eotaek.juyulow.core.model.NaviCandidate
import eotaek.juyulow.core.model.NaviPoint
import eotaek.juyulow.core.model.NaviResult
import eotaek.juyulow.core.model.NaviSnapshot
import eotaek.juyulow.core.model.confirmsRoute
import eotaek.juyulow.navigation.NaviTripLedger
import eotaek.juyulow.navigation.PleosNaviClient
import eotaek.juyulow.navigation.PleosNavigationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in real repository test. Requires no existing waypoints and restores the original destination. */
class CoordinateRepositoryIntegrationTest {
    @Test
    fun convertedCoordinateTripAndDestination() = runBlocking {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("coordinateRepositoryProbe") == "true"
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = PleosNaviClient(context)
        val repo = PleosNavigationRepository(client, NaviTripLedger())
        var original: NaviSnapshot? = null
        var changed = false
        try {
            val initial = (repo.initialize() as NaviResult.Success).value
            original = initial
            assumeTrue(initial.waypoints.isEmpty())
            Log.i("CoordinateWaypointProbe", "initial=$initial")
            val point = KatecCoordinates().toWgs84(312623.2596234215, 548572.9766224765)
            val candidate = NaviCandidate(
                NaviPoint(
                    point.latitude, point.longitude,
                    "CoordinateRepositoryProbe", "", null
                ), point.latitude, point.longitude, coordinateOnly = true
            )
            val trips = repo.trips(listOf(candidate))
            assertTrue("Trip: $trips", trips is NaviResult.Success)
            assertTrue((trips as NaviResult.Success).value.single().meters > 0)
            // A fresh name token must also correlate a second request to the same coordinate.
            assertTrue(repo.trips(listOf(candidate)) is NaviResult.Success)
            var expected = initial
            if (expected.destination == null) {
                changed = true
                val seeded = repo.setRoute(expected, candidate, false)
                assertTrue("Seed: $seeded", seeded is NaviResult.Success)
                expected = (seeded as NaviResult.Success).value
            }
            if (InstrumentationRegistry.getArguments()
                    .getString("coordinateWaypointProbe") == "true"
            ) {
                val waypoint = KatecCoordinates().toWgs84(320800.1693972926, 535935.5214569089)
                val stop = NaviCandidate(
                    NaviPoint(
                        waypoint.latitude, waypoint.longitude,
                        "CoordinateWaypointProbe", "", null
                    ), waypoint.latitude, waypoint.longitude, coordinateOnly = true
                )
                changed = true
                val waypointResult = repo.setRoute(expected, stop, true)
                Log.i(
                    "CoordinateWaypointProbe",
                    "result=$waypointResult observed=${repo.state.value}"
                )
                assertTrue("Waypoint: $waypointResult", waypointResult is NaviResult.Success)
                expected = (waypointResult as NaviResult.Success).value
            }
            changed = true
            val result = repo.setRoute(expected, candidate, false)
            assertTrue("Destination: $result", result is NaviResult.Success)
            assertTrue(
                (result as NaviResult.Success).value.confirmsRoute(
                    expected,
                    candidate,
                    false
                )
            )
        } finally {
            try {
                if (changed) {
                    val saved = original!!
                    withContext(Dispatchers.Main) {
                        val d = saved.destination
                        if (d == null) client.cancelRoute()
                        else client.requestRoute(
                            RouteInfo(
                                latitude = d.latitude, longitude = d.longitude,
                                poiName = d.name, poiId = d.poiId, poiSubId = d.subId ?: "",
                                address = d.address, poiProvider = d.provider,
                                routeOption = RouteOption.valueOf(saved.routeOption)
                            )
                        )
                    }
                    withTimeout(30_000) {
                        while (true) {
                            delay(500)
                            val result = repo.refresh()
                            if (result is NaviResult.Success && result.value.destination == saved.destination && result.value.waypoints == saved.waypoints) {
                                Log.i("CoordinateWaypointProbe", "original route restored")
                                break
                            }
                        }
                    }
                }
            } finally {
                repo.release()
            }
        }
    }
}
