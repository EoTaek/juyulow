package eotaek.juyulow.core.data

import eotaek.juyulow.core.model.VehicleFailure
import eotaek.juyulow.core.model.VehicleFetchStatus
import eotaek.juyulow.core.model.VehicleField
import eotaek.juyulow.core.model.VehicleProperty
import eotaek.juyulow.core.model.VehicleValue
import eotaek.juyulow.core.model.VehicleValueOrigin
import eotaek.juyulow.core.model.accepts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface VehicleDefaultsRepository {
    val defaults: Flow<Map<VehicleProperty, VehicleValue>>

    /** null clears the override. Does not modify manual refuel settings. */
    suspend fun save(property: VehicleProperty, value: VehicleValue?)
}

interface VehicleInfoRepository {
    val fields: StateFlow<Map<VehicleProperty, VehicleField>>
}

class InMemoryVehicleInfoRepository : VehicleInfoRepository {
    private val mutable = MutableStateFlow(VehicleProperty.entries.associateWith { VehicleField() })
    override val fields = mutable.asStateFlow()

    fun applyDefaults(defaults: Map<VehicleProperty, VehicleValue>) {
        mutable.update { fields ->
            fields.mapValues { (property, field) ->
                if (field.status == VehicleFetchStatus.SUCCESS) field else {
                    val value = defaults[property]?.takeIf(property::accepts)
                    field.copy(
                        value = value,
                        origin = if (value == null) VehicleValueOrigin.UNSET else VehicleValueOrigin.DEFAULT
                    )
                }
            }
        }
    }

    fun complete(
        property: VehicleProperty,
        raw: VehicleValue?,
        atMillis: Long,
        failure: VehicleFailure?
    ) {
        mutable.update { fields ->
            val old = fields.getValue(property)
            if (old.status != VehicleFetchStatus.PENDING) fields else fields + (property to
                    if (failure == null) VehicleField(
                        VehicleFetchStatus.SUCCESS,
                        raw,
                        raw,
                        VehicleValueOrigin.FETCH,
                        atMillis
                    )
                    else old.copy(
                        status = VehicleFetchStatus.FAILED,
                        rawValue = raw,
                        fetchedAtMillis = atMillis,
                        failure = failure
                    ))
        }
    }
}
