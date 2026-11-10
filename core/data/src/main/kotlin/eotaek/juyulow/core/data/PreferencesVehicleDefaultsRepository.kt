package eotaek.juyulow.core.data

import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import eotaek.juyulow.core.model.VehicleProperty
import eotaek.juyulow.core.model.VehicleValue
import eotaek.juyulow.core.model.accepts
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.IOException

class PreferencesVehicleDefaultsRepository(
    storage: SettingsStorage,
    settings: SettingsRepository,
) : VehicleDefaultsRepository {
    private val store = storage.store
    override val defaults = combine(
        store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it },
        settings.settings,
    ) { values, manual ->
        buildMap {
            VehicleProperty.entries.forEach { property ->
                val saved: VehicleValue? = if (property == VehicleProperty.FUEL_TYPES) {
                    values[typesKey]?.mapNotNull(String::toIntOrNull)
                        ?.let { VehicleValue.FuelTypes(it.sorted()) }
                } else values[numberKey(property)]?.let(VehicleValue::Number)
                val fallback =
                    if (property == VehicleProperty.CUMULATIVE_ECONOMY || property == VehicleProperty.CURRENT_ECONOMY)
                        VehicleValue.Number(100.0 / manual.efficiencyKmPerL) else null
                (saved?.takeIf(property::accepts) ?: fallback)?.let { put(property, it) }
            }
        }
    }.distinctUntilChanged()

    override suspend fun save(property: VehicleProperty, value: VehicleValue?) {
        require(value == null || property.accepts(value)) { "Invalid vehicle default for $property" }
        store.edit { values ->
            if (property == VehicleProperty.FUEL_TYPES) {
                if (value == null) values.remove(typesKey)
                else values[typesKey] =
                    (value as VehicleValue.FuelTypes).codes.map(Int::toString).toSet()
            } else {
                if (value == null) values.remove(numberKey(property))
                else values[numberKey(property)] = (value as VehicleValue.Number).value
            }
        }
    }

    private fun numberKey(property: VehicleProperty) =
        doublePreferencesKey("vehicle_default_${property.name.lowercase()}")

    private val typesKey = stringSetPreferencesKey("vehicle_default_fuel_types")
}
