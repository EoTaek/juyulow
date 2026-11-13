package eotaek.juyulow.feature.settings

import androidx.lifecycle.ViewModelStore
import eotaek.juyulow.core.data.InMemoryVehicleInfoRepository
import eotaek.juyulow.core.data.SettingsRepository
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.core.model.VehicleProperty
import eotaek.juyulow.core.model.VehicleValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    private class Repository : SettingsRepository {
        override val settings = MutableStateFlow(RefuelSettings())
        var fail = false
        var writes = 0
        override suspend fun save(settings: RefuelSettings) {
            writes++
            delay(100.milliseconds)
            if (fail) throw IOException("disk full")
            this.settings.value = settings
        }
    }

    @Before
    fun prepare() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun close() {
        store.clear(); Dispatchers.resetMain()
    }

    private fun create(repository: SettingsRepository): SettingsViewModel =
        SettingsViewModel(repository, InMemoryVehicleInfoRepository()).also {
            store.put(
                "settings",
                it
            )
        }

    @Test
    fun editingIsLocalAndClosingDiscardsDraft() = runTest(dispatcher) {
        val repository = Repository()
        val first = create(repository)
        runCurrent()
        first.edit { it.copy(liters = 70) }
        assertEquals(RefuelSettings(), repository.settings.value)
        val second = create(repository)
        runCurrent()
        assertEquals(RefuelSettings(), second.state.value.draft)
        assertEquals(0, repository.writes)
    }

    @Test
    fun applyPersistsOnceAndNextOpeningLoadsSavedConditions() = runTest(dispatcher) {
        val repository = Repository()
        val first = create(repository)
        runCurrent()
        val changed = RefuelSettings(90, 21, 12)
        first.edit { changed }
        first.apply(); first.apply()
        runCurrent()
        assertTrue(first.state.value.saving)
        first.edit { it.copy(liters = 10) }
        advanceUntilIdle()
        assertEquals(1, repository.writes)
        assertTrue(first.state.value.saved)
        assertEquals(changed, repository.settings.value)
        val second = create(repository)
        runCurrent()
        assertEquals(changed, second.state.value.draft)
    }

    @Test
    fun writeFailureRetainsDraftAndRetryCanSucceed() = runTest(dispatcher) {
        val repository = Repository().apply { fail = true }
        val viewModel = create(repository)
        runCurrent()
        viewModel.edit { it.copy(liters = 60) }
        viewModel.apply(); advanceUntilIdle()
        assertFalse(viewModel.state.value.saved)
        assertEquals(60, viewModel.state.value.draft?.liters)
        assertEquals(40, repository.settings.value.liters)
        assertNotNull(viewModel.state.value.error)
        repository.fail = false
        viewModel.apply(); advanceUntilIdle()
        assertTrue(viewModel.state.value.saved)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun storedVolumeBeatsLateFetchAndApplyPersistsIt() = runTest(dispatcher) {
        val repository = Repository()
        val vehicle = InMemoryVehicleInfoRepository()
        val viewModel = SettingsViewModel(repository, vehicle).also { store.put("settings", it) }
        runCurrent()
        viewModel.edit { it.copy(liters = 20) }
        vehicle.complete(VehicleProperty.FUEL_CAPACITY, VehicleValue.Number(15_000.0), 1, null)
        vehicle.complete(VehicleProperty.FUEL_LEVEL, VehicleValue.Number(15_000.0), 1, null)
        runCurrent()
        assertEquals(20.0, requireNotNull(viewModel.state.value.conditions).liters, 0.0)
        assertFalse(requireNotNull(viewModel.state.value.conditions).automaticLiters)
        assertEquals(40, repository.settings.value.liters)
        viewModel.apply()
        advanceUntilIdle()
        assertEquals(20.0, repository.settings.value.liters.toDouble(), 0.0)
    }

    @Test
    fun automaticReadDuringEditingPreservesDraftAndNeverPersistsSdkValues() = runTest(dispatcher) {
        val repository = Repository()
        val vehicle = InMemoryVehicleInfoRepository()
        val viewModel = SettingsViewModel(repository, vehicle).also { store.put("settings", it) }
        runCurrent()
        viewModel.edit { it.copy(liters = 70, efficiencyKmPerL = 18) }
        vehicle.complete(VehicleProperty.FUEL_CAPACITY, VehicleValue.Number(50_000.0), 1, null)
        vehicle.complete(VehicleProperty.FUEL_LEVEL, VehicleValue.Number(12_345.0), 1, null)
        vehicle.complete(VehicleProperty.CURRENT_ECONOMY, VehicleValue.Number(8.5), 1, null)
        runCurrent()
        assertEquals(70.0, requireNotNull(viewModel.state.value.conditions).liters, 0.0)
        assertFalse(requireNotNull(viewModel.state.value.conditions).automaticEfficiency)
        viewModel.edit {
            it.copy(
                liters = 20,
                efficiencyKmPerL = 3,
                refuelMinutes = 9,
            )
        }
        assertEquals(20, viewModel.state.value.draft?.liters)
        assertEquals(3, viewModel.state.value.draft?.efficiencyKmPerL)
        viewModel.apply()
        advanceUntilIdle()
        assertEquals(RefuelSettings(20, 3, 9), repository.settings.value)
    }
}
