package eotaek.juyulow.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationContextTest {
    @Test
    fun oneWayTripReuseIgnoresDestinationAndUsesFiftyMeterRadius() {
        val a = NaviSnapshot(
            NaviLocation(37.4, 127.0, "", 0, 1),
            null,
            emptyList(),
            "SAFE_GUIDING",
            false,
            "RECOMMENDED",
            1
        )
        val point = NaviPoint(37.42, 127.12, "주유소", "poi", "0")
        assertTrue(
            a.sameTripOriginAs(
                a.copy(
                    destination = point,
                    waypoints = listOf(point),
                    mode = "ROUTE_GUIDING",
                    routing = true,
                    location = a.location.copy(routeId = "new", routeIndex = 5)
                )
            )
        )
        assertTrue(a.sameTripOriginAs(a.copy(location = a.location.copy(latitude = 37.40044))))
        assertFalse(a.sameTripOriginAs(a.copy(location = a.location.copy(latitude = 37.40046))))
        assertFalse(a.sameTripOriginAs(a.copy(routeOption = "FAST")))
    }

    @Test
    fun contextComparisonIgnoresTimeAndUsesPositionTolerance() {
        val a = NaviSnapshot(
            NaviLocation(37.4, 127.0, "route", 0, 1),
            null,
            emptyList(),
            "SAFE_GUIDING",
            false,
            "RECOMMENDED",
            1
        )
        assertTrue(
            a.sameContextAs(
                a.copy(
                    observedAtMillis = 2,
                    location = a.location.copy(observedAtMillis = 2)
                )
            )
        )
        assertTrue(a.sameContextAs(a.copy(location = a.location.copy(latitude = 37.4000005))))
        assertFalse(a.sameContextAs(a.copy(location = a.location.copy(latitude = 37.400002))))
        assertFalse(a.sameContextAs(a.copy(location = a.location.copy(routeId = "other"))))
        assertFalse(a.sameContextAs(a.copy(location = a.location.copy(routeIndex = 1))))
        assertFalse(a.sameContextAs(a.copy(routeOption = "FAST")))
    }

    @Test
    fun waypointConfirmationChecksOrderAndCoordinatesExceptZeroPair() {
        val dest = NaviPoint(37.5, 127.1, "destination", "dest", null)
        val old = NaviPoint(0.0, 0.0, "existing", "old", null)
        val station = NaviPoint(37.4, 127.0, "station", "station", "0")
        val c = NaviCandidate(station, 37.4, 127.0)
        val expected = NaviSnapshot(
            NaviLocation(37.4, 127.0, "", 0, 1),
            dest,
            listOf(old),
            "ROUTE_GUIDING",
            true,
            "RECOMMENDED",
            1
        )
        val actual =
            expected.copy(waypoints = listOf(station.copy(latitude = 0.0, longitude = 0.0), old))
        assertTrue(actual.confirmsRoute(expected, c, true))
        assertFalse(
            actual.copy(waypoints = actual.waypoints.reversed()).confirmsRoute(expected, c, true)
        )
        assertFalse(
            actual.copy(destination = dest.copy(poiId = "wrong")).confirmsRoute(expected, c, true)
        )
        assertFalse(
            actual.copy(waypoints = listOf(station.copy(latitude = 37.9), old))
                .confirmsRoute(expected, c, true)
        )
        assertFalse(
            actual.copy(waypoints = listOf(station.copy(latitude = 0.0), old))
                .confirmsRoute(expected, c, true)
        )
        for (count in 0..2) {
            val journey = JourneyContext(
                dest.name,
                null,
                null,
                navigation = expected.copy(waypoints = List(count) { old }),
                destinationOnly = true
            )
            assertTrue((JourneyMode.WAYPOINT in journey.availableModes) == (count == 0))
            assertTrue(journey.defaultMode == JourneyMode.DESTINATION)
        }
    }
}
