package eotaek.juyulow.core.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import eotaek.juyulow.core.model.RefuelSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreferencesSettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun savedSettingsSurviveNewStoreInstance() = runTest {
        val file = folder.root.resolve("refuel.preferences_pb")
        val firstJob = Job()
        val first = PreferencesSettingsRepository(
            SettingsStorage(
                file,
                CoroutineScope(coroutineContext + firstJob)
            )
        )
        assertEquals(RefuelSettings(), first.settings.first())
        val next = RefuelSettings(
            70, 18, 9,
            comparisonFuel = eotaek.juyulow.core.model.ComparisonFuel.DIESEL
        )
        first.save(next)
        assertEquals(next, first.settings.first())
        firstJob.cancelAndJoin()
        val second = PreferencesSettingsRepository(SettingsStorage(file, backgroundScope))
        assertEquals(next, second.settings.first())
    }

    @Test
    fun legacyRadiusIsIgnoredAndRemovedOnSave() = runTest {
        val storage = SettingsStorage(folder.root.resolve("legacy.preferences_pb"), backgroundScope)
        val radius = intPreferencesKey("search_radius_km")
        storage.store.edit { it[radius] = 5 }
        val repository = PreferencesSettingsRepository(storage)

        assertEquals(3, repository.settings.first().searchRadiusKm)
        repository.save(RefuelSettings(liters = 70, searchRadiusKm = 1))
        assertEquals(3, repository.settings.first().searchRadiusKm)
        assertEquals(70, repository.settings.first().liters)
        assertEquals(null, storage.store.data.first()[radius])
    }
}
