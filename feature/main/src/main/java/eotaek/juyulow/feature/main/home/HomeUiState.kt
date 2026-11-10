package eotaek.juyulow.feature.main.home

import eotaek.juyulow.core.model.JourneyContext
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.core.model.VehicleField
import eotaek.juyulow.core.model.VehicleProperty
import eotaek.juyulow.core.model.resolveRefuelConditions

data class HomeUiState(
    val journey: JourneyContext = JourneyContext(),
    val settings: RefuelSettings? = null,
    val searching: Boolean = false,
    val error: String? = null,
    val readySessionId: String? = null,
    val vehicleFields: Map<VehicleProperty, VehicleField> = emptyMap(),
) {
    val conditions get() = settings?.let { resolveRefuelConditions(it, vehicleFields) }
}
