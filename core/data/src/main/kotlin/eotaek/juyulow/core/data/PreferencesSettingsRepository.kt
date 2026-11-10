package eotaek.juyulow.core.data

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import eotaek.juyulow.core.model.RefuelSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException

/** Android supplies only the file. A single application-scoped instance owns this store. */
class SettingsStorage(
    file: File,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    internal val store = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { file },
    )
}

class PreferencesSettingsRepository(storage: SettingsStorage) : SettingsRepository {
    private val store = storage.store
    private val litersOverride = MutableStateFlow<Double?>(null)
    override val settings = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { values ->
        RefuelSettings(
            liters = (values[Liters] ?: 40).takeIf { it in 10..100 && it % 10 == 0 } ?: 40,
            efficiencyKmPerL = (values[Efficiency] ?: 12).takeIf { it in 1..30 } ?: 12,
            refuelMinutes = (values[Minutes] ?: 5).takeIf { it in 1..20 } ?: 5,
            includeTimeCost = values[TimeCost] ?: false,
        )
    }.combine(litersOverride) { settings, override -> settings.copy(litersOverride = override) }
        .distinctUntilChanged()

    override suspend fun save(settings: RefuelSettings) {
        store.edit { values ->
            values[Liters] = settings.liters
            values[Efficiency] = settings.efficiencyKmPerL
            values[Minutes] = settings.refuelMinutes
            values[TimeCost] = settings.includeTimeCost
        }
        litersOverride.value = settings.litersOverride
    }

    private companion object {
        val Liters = intPreferencesKey("liters")
        val Efficiency = intPreferencesKey("efficiency_km_per_l")
        val Minutes = intPreferencesKey("refuel_minutes")
        val TimeCost = booleanPreferencesKey("include_time_cost")
    }
}
