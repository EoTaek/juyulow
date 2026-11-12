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
    fun coordinateWaypointWithMissingCoordinatesStillRequiresIdentityAndDestination() {
        val actual = snapshot.copy(waypoints = listOf(point.copy(latitude = 0.0, longitude = 0.0)))
        assertTrue(actual.confirmsRoute(snapshot, candidate, true))
        assertFalse(
            actual.copy(waypoints = listOf(actual.waypoints.single().copy(name = "other")))
                .confirmsRoute(snapshot, candidate, true)
        )
        assertFalse(
            actual.copy(waypoints = listOf(actual.waypoints.single().copy(poiId = "other")))
                .confirmsRoute(snapshot, candidate, true)
        )
        assertFalse(
            actual.copy(destination = point.copy(name = "other destination"))
                .confirmsRoute(snapshot, candidate, true)
        )
        assertFalse(
            actual.copy(waypoints = actual.waypoints + point)
                .confirmsRoute(snapshot, candidate, true)
        )
        assertFalse(
            actual.copy(waypoints = listOf(point.copy(latitude = 37.6)))
                .confirmsRoute(snapshot, candidate, true)
        )
    }
}
