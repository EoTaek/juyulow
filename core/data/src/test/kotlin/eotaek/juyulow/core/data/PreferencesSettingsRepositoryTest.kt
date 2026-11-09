package eotaek.juyulow.core.data

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
        val next = RefuelSettings(70, 18, 9, true)
        first.save(next)
        assertEquals(next, first.settings.first())
        firstJob.cancelAndJoin()
        val second = PreferencesSettingsRepository(SettingsStorage(file, backgroundScope))
        assertEquals(next, second.settings.first())
    }
}
