package eotaek.juyulow.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleInfoTest {
    @Test
    fun validatesBoundaryValuesWithoutInventingMissingData() {
        for (property in VehicleProperty.entries) assertFalse(property.accepts(null))
        for (value in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.MIN_VALUE))
            assertFalse(VehicleProperty.CURRENT_ECONOMY.accepts(VehicleValue.Number(value)))
        assertTrue(VehicleProperty.FUEL_PERCENT.accepts(VehicleValue.Number(0.0)))
        assertTrue(VehicleProperty.FUEL_PERCENT.accepts(VehicleValue.Number(100.0)))
        assertFalse(VehicleProperty.FUEL_PERCENT.accepts(VehicleValue.Number(100.1)))
        assertTrue(VehicleProperty.FUEL_LEVEL.accepts(VehicleValue.Number(0.0)))
        assertFalse(VehicleProperty.FUEL_CAPACITY.accepts(VehicleValue.Number(0.0)))
        assertFalse(VehicleProperty.FUEL_TYPES.accepts(VehicleValue.FuelTypes(emptyList())))
        assertTrue(VehicleProperty.FUEL_TYPES.accepts(VehicleValue.FuelTypes(listOf(0, 1, 10, 99))))
    }

    @Test
    fun economyConversionDoesNotRound() {
        val field = VehicleField(value = VehicleValue.Number(7.3))
        assertEquals(100.0 / 7.3, field.efficiencyKmPerL!!, 0.0)
    }
}
