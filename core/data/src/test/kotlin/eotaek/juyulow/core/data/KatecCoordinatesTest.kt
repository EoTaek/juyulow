package eotaek.juyulow.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class KatecCoordinatesTest {
    private val coordinates = KatecCoordinates()

    @Test
    fun matchesIndependentProjFixturesAndRoundTrips() {
        // PROJ/pyproj, always_xy=true, identical explicit KATEC datum parameters.
        val fixtures = listOf(
            doubleArrayOf(37.535225, 127.009125, 312623.2596234215, 548572.9766224765),
            doubleArrayOf(37.42210277, 127.10299722, 320800.1693972926, 535935.5214569089),
            doubleArrayOf(35.1796, 129.0756, 498164.7156408286, 287275.00646455586)
        )
        fixtures.forEach { (lat, lon, x, y) ->
            val projected = coordinates.toKatec(lat, lon)
            assertEquals(x, projected.x, 0.05)
            assertEquals(y, projected.y, 0.05)
            val restored = coordinates.toWgs84(x, y)
            assertEquals(lat, restored.latitude, 0.000001)
            assertEquals(lon, restored.longitude, 0.000001)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSwappedGeographicAxes() {
        coordinates.toKatec(127.0, 37.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidProjectedCoordinates() {
        coordinates.toWgs84(Double.NaN, 0.0)
    }
}
