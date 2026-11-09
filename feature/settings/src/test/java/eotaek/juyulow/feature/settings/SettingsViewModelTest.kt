package eotaek.juyulow.feature.settings

import androidx.lifecycle.ViewModelStore
import eotaek.juyulow.core.data.SettingsRepository
import eotaek.juyulow.core.model.RefuelSettings
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
            delay(100)
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
        SettingsViewModel(repository).also { store.put("settings", it) }

    @Test
    fun editingIsLocalAndClosingDiscardsDraft() = runTest(dispatcher) {
        val repository = Repository()
        val first = create(repository)
        runCurrent()
        first.edit { it.copy(liters = 70, includeTimeCost = true) }
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
        val changed = RefuelSettings(90, 21, 12, true)
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
}
