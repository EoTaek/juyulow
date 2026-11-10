package eotaek.juyulow.core.data

import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.core.model.VehicleProperty
import eotaek.juyulow.core.model.VehicleValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VehicleDefaultsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun defaultsSurviveReloadWithoutChangingManualSettings() = runTest {
        val file = folder.root.resolve("refuel.preferences_pb")
        val job = Job()
        val storage = SettingsStorage(file, CoroutineScope(coroutineContext + job))
        val settings = PreferencesSettingsRepository(storage)
        settings.save(RefuelSettings(efficiencyKmPerL = 18))
        val defaults = PreferencesVehicleDefaultsRepository(storage, settings)
        assertEquals(
            VehicleValue.Number(100.0 / 18),
            defaults.defaults.first()[VehicleProperty.CURRENT_ECONOMY]
        )
        assertNull(defaults.defaults.first()[VehicleProperty.FUEL_LEVEL])
        defaults.save(VehicleProperty.FUEL_CAPACITY, VehicleValue.Number(52_500.5))
        defaults.save(VehicleProperty.FUEL_TYPES, VehicleValue.FuelTypes(listOf(1, 10, 99)))
        defaults.save(VehicleProperty.CURRENT_ECONOMY, VehicleValue.Number(6.2))
        job.cancelAndJoin()
        val restoredStorage = SettingsStorage(file, backgroundScope)
        val restoredSettings = PreferencesSettingsRepository(restoredStorage)
        val restored = PreferencesVehicleDefaultsRepository(restoredStorage, restoredSettings)
        assertEquals(
            VehicleValue.Number(52_500.5),
            restored.defaults.first()[VehicleProperty.FUEL_CAPACITY]
        )
        assertEquals(
            VehicleValue.FuelTypes(listOf(1, 10, 99)),
            restored.defaults.first()[VehicleProperty.FUEL_TYPES]
        )
        assertEquals(18, restoredSettings.settings.first().efficiencyKmPerL)
        restored.save(VehicleProperty.CURRENT_ECONOMY, null)
        assertEquals(
            VehicleValue.Number(100.0 / 18),
            restored.defaults.first()[VehicleProperty.CURRENT_ECONOMY]
        )
    }

    @Test
    fun invalidDefaultsAreRejected() = runTest {
        val storage = SettingsStorage(folder.root.resolve("refuel.preferences_pb"), backgroundScope)
        val repo =
            PreferencesVehicleDefaultsRepository(storage, PreferencesSettingsRepository(storage))
        for ((property, value) in listOf(
            VehicleProperty.FUEL_PERCENT to VehicleValue.Number(101.0),
            VehicleProperty.CURRENT_ECONOMY to VehicleValue.Number(Double.NaN),
            VehicleProperty.FUEL_TYPES to VehicleValue.Number(1.0),
        )) {
            try {
                repo.save(property, value); fail("Should reject $property")
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}
