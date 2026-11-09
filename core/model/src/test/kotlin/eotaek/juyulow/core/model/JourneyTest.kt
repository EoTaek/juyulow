package eotaek.juyulow.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyTest {
    private val context = ComparisonContext(JourneyContext(), RefuelSettings(), 1750)
    private val station =
        Station("a", "주유소", "S-OIL", 1600, 37.6, 127.0, 1800, 300, 2300, 420, 30200, 2760, 500)

    @Test
    fun waypointUsesIncrementalRouteAndOneStop() {
        val result = compareStation(context, station, JourneyMode.WAYPOINT)
        assertEquals(1800, result.costDistanceMeters)
        assertEquals(540, result.costDurationSeconds)
        assertEquals(3060, result.totalSeconds)
        assertEquals(6000.0, result.fuelSavingWon, .0001)
        assertEquals(262.5, result.drivingCostWon, .0001)
        assertEquals(5237.5, result.netSavingWon, .0001)
    }

    @Test
    fun directAndAsymmetricRoundTripChargeFullRoute() {
        val direct = compareStation(context, station, JourneyMode.DESTINATION)
        val round = compareStation(context, station, JourneyMode.ROUND_TRIP)
        assertEquals(1800, direct.distanceMeters)
        assertEquals(600, direct.totalSeconds)
        assertEquals(4100, round.distanceMeters)
        assertEquals(720, round.drivingSeconds)
        assertEquals(1020, round.totalSeconds)
        assertEquals(round.totalSeconds, round.costDurationSeconds)
    }

    @Test
    fun optionalTimeCostIncludesStopAndCanProduceLoss() {
        val timed = context.copy(settings = RefuelSettings(includeTimeCost = true))
        val result = compareStation(timed, station, JourneyMode.WAYPOINT)
        assertEquals(1800.0, result.timeCostWon, .0001)
        assertEquals(3437.5, result.netSavingWon, .0001)
        assertTrue(
            compareStation(
                timed,
                station.copy(priceWonPerL = 1750),
                JourneyMode.ROUND_TRIP
            ).netSavingWon < 0
        )
    }

    @Test
    fun roundingDoesNotEnterCalculationAndFasterViaRemainsSigned() {
        val result = compareStation(
            context.copy(settings = RefuelSettings(efficiencyKmPerL = 13)),
            station,
            JourneyMode.WAYPOINT
        )
        assertEquals(1800 / 1000.0 / 13 * 1750, result.drivingCostWon, .0000001)
        assertEquals(
            -400,
            compareStation(
                context,
                station.copy(viaMeters = 28000),
                JourneyMode.WAYPOINT
            ).costDistanceMeters
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun waypointRequiresDestination() {
        compareStation(
            context.copy(journey = JourneyContext(destination = null)),
            station,
            JourneyMode.WAYPOINT
        )
    }

    @Test
    fun settingsRejectOutOfRangeAndOffStepValues() {
        listOf<() -> RefuelSettings>(
            { RefuelSettings(liters = 15) },
            { RefuelSettings(liters = 110) },
            { RefuelSettings(efficiencyKmPerL = 0) },
            { RefuelSettings(refuelMinutes = 21) }).forEach { factory ->
            assertThrows(IllegalArgumentException::class.java) { factory() }
        }
        assertEquals(
            listOf(JourneyMode.DESTINATION, JourneyMode.ROUND_TRIP),
            JourneyContext(destination = null).availableModes
        )
    }
}
