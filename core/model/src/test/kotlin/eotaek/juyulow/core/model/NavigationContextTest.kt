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
}
