package eotaek.juyulow.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateRouteTest {
    private val point = NaviPoint(37.535225, 127.009125, "station", "", null)
    private val snapshot = NaviSnapshot(
        NaviLocation(37.4, 127.1, "", 0, 1), point,
        emptyList(), "ROUTE_GUIDING", false, "RECOMMENDED", 1
    )
    private val candidate =
        NaviCandidate(point, point.latitude, point.longitude, coordinateOnly = true)

    @Test
    fun coordinateTargetsHaveDistinctIdentity() {
        assertFalse(snapshot.sameRouteTargetsAs(snapshot.copy(destination = point.copy(latitude = 37.6))))
        assertTrue(snapshot.sameRouteTargetsAs(snapshot.copy(observedAtMillis = 20)))
    }

    @Test
    fun confirmsOnlyMatchingCoordinateAndName() {
        assertTrue(snapshot.confirmsRoute(snapshot, candidate, false))
        assertFalse(
            snapshot.copy(destination = point.copy(name = "other"))
                .confirmsRoute(snapshot, candidate, false)
        )
        assertFalse(
            snapshot.copy(destination = point.copy(longitude = 127.1))
                .confirmsRoute(snapshot, candidate, false)
        )
    }

    @Test
    fun coordinateWaypointWithMissingCoordinatesCannotBeConfirmed() {
        val actual = snapshot.copy(waypoints = listOf(point.copy(latitude = 0.0, longitude = 0.0)))
        assertFalse(actual.confirmsRoute(snapshot, candidate, true))
    }
}
