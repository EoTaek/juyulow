package eotaek.juyulow

import androidx.test.platform.app.InstrumentationRegistry
import eotaek.juyulow.core.data.KatecCoordinates
import eotaek.juyulow.core.model.NaviCandidate
import eotaek.juyulow.core.model.NaviPoint
import eotaek.juyulow.core.model.NaviResult
import eotaek.juyulow.core.model.confirmsRoute
import eotaek.juyulow.navigation.NaviTripLedger
import eotaek.juyulow.navigation.PleosNaviClient
import eotaek.juyulow.navigation.PleosNavigationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in real repository test. Requires no existing route and restores that state. */
class CoordinateRepositoryIntegrationTest {
    @Test
    fun convertedCoordinateTripAndDestination() = runBlocking {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("coordinateRepositoryProbe") == "true"
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = PleosNaviClient(context)
        val repo = PleosNavigationRepository(client, NaviTripLedger())
        var changed = false
        try {
            val initial = (repo.initialize() as NaviResult.Success).value
            assumeTrue(initial.destination == null && initial.waypoints.isEmpty())
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
            changed = true
            val result = repo.setRoute(initial, candidate, false)
            assertTrue("Route: $result", result is NaviResult.Success)
            assertTrue(
                (result as NaviResult.Success).value.confirmsRoute(
                    initial,
                    candidate,
                    false
                )
            )
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
                val waypointResult = repo.setRoute(result.value, stop, true)
                assertEquals(NaviResult.Failure("COORDINATE_WAYPOINT_UNSUPPORTED"), waypointResult)
            }
        } finally {
            try {
                if (changed) {
                    withContext(Dispatchers.Main) { client.cancelRoute() }
                    withTimeout(30_000) {
                        while (true) {
                            delay(500)
                            val result = repo.refresh()
                            if (result is NaviResult.Success && result.value.destination == null && result.value.waypoints.isEmpty()) break
                        }
                    }
                }
            } finally {
                repo.release()
            }
        }
    }
}
