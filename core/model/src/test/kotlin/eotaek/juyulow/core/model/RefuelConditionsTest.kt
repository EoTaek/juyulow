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

    @Test
    fun savedVolumeAndFetchedCumulativeEconomySupplyActiveCalculation() {
        val settings = RefuelSettings(liters = 70, efficiencyKmPerL = 18)
        val fields = mapOf(
            VehicleProperty.FUEL_CAPACITY to fetched(50_000.0),
            VehicleProperty.FUEL_LEVEL to fetched(12_345.0),
            VehicleProperty.CUMULATIVE_ECONOMY to fetched(8.5),
            VehicleProperty.CURRENT_ECONOMY to fetched(5.0),
        )
        val result = resolveRefuelConditions(settings, fields)
        assertEquals(70.0, result.liters, 0.0)
        assertEquals(100.0 / 8.5, result.efficiencyKmPerL, 0.0)
        assertFalse(result.automaticLiters)
        assertEquals(RefuelValueSource.CUMULATIVE_ECONOMY, result.efficiencySource)
        val station = Station("a", "a", "a", 1600, 0.0, 0.0, 1800, 300, 2300, 420, 30200, 2760, 0)
        val comparison = compareStation(
            ComparisonContext(JourneyContext(), result, 1750),
            station,
            JourneyMode.DESTINATION
        )
        assertEquals(150 * 70.0, comparison.fuelSavingWon, 0.0)
        assertEquals(1.8 / (100.0 / 8.5) * 1750, comparison.drivingCostWon, 0.0)
        assertEquals(
            comparison.fuelSavingWon - comparison.drivingCostWon,
            comparison.netSavingWon,
            0.0
        )
    }

    @Test
    fun missingFailedDefaultOrInvalidCumulativeUsesSavedEconomyAndIgnoresCurrent() {
        val settings = RefuelSettings(efficiencyKmPerL = 18)
        val invalidFields = listOf(
            null,
            fetched(8.5).copy(status = VehicleFetchStatus.PENDING),
            fetched(8.5).copy(status = VehicleFetchStatus.FAILED),
            fetched(8.5).copy(origin = VehicleValueOrigin.DEFAULT),
        ) + listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0).map(::fetched)
        invalidFields.forEach { cumulative ->
            val fields = buildMap {
                put(VehicleProperty.CURRENT_ECONOMY, fetched(5.0))
                cumulative?.let { put(VehicleProperty.CUMULATIVE_ECONOMY, it) }
            }
            val result = resolveRefuelConditions(settings, fields)
            assertEquals(18.0, result.efficiencyKmPerL, 0.0)
            assertFalse(result.automaticEfficiency)
        }
    }

    private val manual = RefuelSettings()

    @Test
    fun fractionalAutomaticInputsAreUsedWithoutClampingOrRounding() {
        val conditions = resolveVehicleRefuelConditions(
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
        val conditions = resolveVehicleRefuelConditions(
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
        val percent = resolveVehicleRefuelConditions(manual, fields)
        assertEquals(32.8125, percent.liters, 0.0)
        assertEquals(RefuelValueSource.FUEL_PERCENT, percent.litersSource)
        assertEquals(
            percent,
            resolveVehicleRefuelConditions(
                manual,
                fields + (VehicleProperty.FUEL_LEVEL to fetched(60_000.0))
            )
        )
        assertEquals(
            40.0,
            resolveVehicleRefuelConditions(manual, fields - VehicleProperty.FUEL_CAPACITY).liters,
            0.0
        )
        assertEquals(
            40.0,
            resolveVehicleRefuelConditions(
                manual,
                fields + (VehicleProperty.FUEL_PERCENT to fetched(101.0))
            ).liters,
            0.0
        )
        val level = resolveVehicleRefuelConditions(
            manual,
            fields + (VehicleProperty.FUEL_LEVEL to fetched(20_000.0))
        )
        assertEquals(32.5, level.liters, 0.0)
        assertEquals(RefuelValueSource.TANK_LEVEL, level.litersSource)
    }

    @Test
    fun currentEconomyDoesNotReplaceCumulativeDefaultAndInvalidValuesUseManual() {
        val default = fetched(1.0).copy(
            status = VehicleFetchStatus.FAILED,
            origin = VehicleValueOrigin.DEFAULT
        )
        val conditions = resolveVehicleRefuelConditions(
            manual, mapOf(
                VehicleProperty.CUMULATIVE_ECONOMY to default,
                VehicleProperty.CURRENT_ECONOMY to fetched(7.7),
                VehicleProperty.FUEL_CAPACITY to default,
                VehicleProperty.FUEL_LEVEL to fetched(0.0),
            )
        )
        assertEquals(12.0, conditions.efficiencyKmPerL, 0.0)
        assertEquals(RefuelValueSource.MANUAL, conditions.efficiencySource)
        assertFalse(conditions.automaticLiters)
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0).forEach {
            val invalid = resolveVehicleRefuelConditions(
                manual,
                mapOf(VehicleProperty.CUMULATIVE_ECONOMY to fetched(it))
            )
            assertEquals(12.0, invalid.efficiencyKmPerL, 0.0)
        }
    }

    @Test
    fun energyCodesAreCopiedWithoutInferringVehicleTypeOrChangingComparisonFuel() {
        val codes = mutableListOf(10, 1, 999, 10)
        val conditions = resolveVehicleRefuelConditions(
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
