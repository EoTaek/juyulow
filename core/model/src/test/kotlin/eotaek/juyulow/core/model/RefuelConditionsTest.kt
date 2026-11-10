package eotaek.juyulow.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefuelConditionsTest {
    private fun fetched(value: Double) = VehicleField(
        status = VehicleFetchStatus.SUCCESS,
        value = VehicleValue.Number(value),
        origin = VehicleValueOrigin.FETCH,
    )

    private val manual = RefuelSettings()

    @Test
    fun fractionalAutomaticInputsAreUsedWithoutClampingOrRounding() {
        val conditions = resolveRefuelConditions(
            manual, mapOf(
                VehicleProperty.FUEL_CAPACITY to fetched(150_000.5),
                VehicleProperty.FUEL_LEVEL to fetched(12_345.25),
                VehicleProperty.CUMULATIVE_ECONOMY to fetched(2.345),
                VehicleProperty.CURRENT_ECONOMY to fetched(8.0),
            )
        )
        assertEquals(137.65525, conditions.liters, 1e-10)
        assertEquals(100.0 / 2.345, conditions.efficiencyKmPerL, 0.0)
        assertEquals(RefuelValueSource.CUMULATIVE_ECONOMY, conditions.efficiencySource)
        val station = Station("a", "a", "a", 1600, 0.0, 0.0, 1800, 300, 2300, 420, 30200, 2760, 0)
        val result = compareStation(
            ComparisonContext(JourneyContext(), conditions, 1750),
            station,
            JourneyMode.DESTINATION
        )
        assertEquals(150 * conditions.liters, result.fuelSavingWon, 0.0)
        assertEquals(1.8 / conditions.efficiencyKmPerL * 1750, result.drivingCostWon, 0.0)
    }

    @Test
    fun fullTankIsZeroAndStillCalculatesDrivingCost() {
        val conditions = resolveRefuelConditions(
            manual, mapOf(
                VehicleProperty.FUEL_CAPACITY to fetched(15_000.0),
                VehicleProperty.FUEL_LEVEL to fetched(15_000.0),
            )
        )
        assertTrue(conditions.automaticLiters)
        assertEquals(0.0, conditions.liters, 0.0)
        val station = Station("a", "a", "a", 1600, 0.0, 0.0, 1800, 300, 2300, 420, 30200, 2760, 0)
        val result = compareStation(
            ComparisonContext(JourneyContext(), conditions, 1750),
            station,
            JourneyMode.DESTINATION
        )
        assertEquals(0.0, result.fuelSavingWon, 0.0)
        assertTrue(result.drivingCostWon > 0)
        assertTrue(result.netSavingWon < 0)
    }

    @Test
    fun missingOrInconsistentLevelUsesPercentThenManual() {
        val fields = mapOf(
            VehicleProperty.FUEL_CAPACITY to fetched(52_500.0),
            VehicleProperty.FUEL_PERCENT to fetched(37.5),
        )
        val percent = resolveRefuelConditions(manual, fields)
        assertEquals(32.8125, percent.liters, 0.0)
        assertEquals(RefuelValueSource.FUEL_PERCENT, percent.litersSource)
        assertEquals(
            percent,
            resolveRefuelConditions(
                manual,
                fields + (VehicleProperty.FUEL_LEVEL to fetched(60_000.0))
            )
        )
        assertEquals(
            40.0,
            resolveRefuelConditions(manual, fields - VehicleProperty.FUEL_CAPACITY).liters,
            0.0
        )
        assertEquals(
            40.0,
            resolveRefuelConditions(
                manual,
                fields + (VehicleProperty.FUEL_PERCENT to fetched(101.0))
            ).liters,
            0.0
        )
        val level = resolveRefuelConditions(
            manual,
            fields + (VehicleProperty.FUEL_LEVEL to fetched(20_000.0))
        )
        assertEquals(32.5, level.liters, 0.0)
        assertEquals(RefuelValueSource.TANK_LEVEL, level.litersSource)
    }

    @Test
    fun currentEconomyBeatsCumulativeDefaultAndInvalidValuesUseManual() {
        val default = fetched(1.0).copy(
            status = VehicleFetchStatus.FAILED,
            origin = VehicleValueOrigin.DEFAULT
        )
        val conditions = resolveRefuelConditions(
            manual, mapOf(
                VehicleProperty.CUMULATIVE_ECONOMY to default,
                VehicleProperty.CURRENT_ECONOMY to fetched(7.7),
                VehicleProperty.FUEL_CAPACITY to default,
                VehicleProperty.FUEL_LEVEL to fetched(0.0),
            )
        )
        assertEquals(100.0 / 7.7, conditions.efficiencyKmPerL, 0.0)
        assertEquals(RefuelValueSource.CURRENT_ECONOMY, conditions.efficiencySource)
        assertFalse(conditions.automaticLiters)
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0).forEach {
            val invalid = resolveRefuelConditions(
                manual,
                mapOf(VehicleProperty.CUMULATIVE_ECONOMY to fetched(it))
            )
            assertEquals(12.0, invalid.efficiencyKmPerL, 0.0)
        }
    }

    @Test
    fun energyCodesAreCopiedWithoutInferringVehicleTypeOrChangingComparisonFuel() {
        val codes = mutableListOf(10, 1, 999, 10)
        val conditions = resolveRefuelConditions(
            manual, mapOf(
                VehicleProperty.FUEL_TYPES to VehicleField(
                    status = VehicleFetchStatus.SUCCESS,
                    value = VehicleValue.FuelTypes(codes),
                    origin = VehicleValueOrigin.FETCH,
                )
            )
        )
        codes.clear()
        assertEquals(listOf(1, 10, 999), conditions.energy.codes)
        assertTrue(conditions.energy.includesElectric)
        assertEquals(ComparisonFuel.GASOLINE, conditions.comparisonFuel)
    }
}
