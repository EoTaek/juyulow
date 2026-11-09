package eotaek.juyulow.feature.main.home

import eotaek.juyulow.core.model.JourneyContext
import eotaek.juyulow.core.model.RefuelSettings

data class HomeUiState(
    val journey: JourneyContext = JourneyContext(),
    val settings: RefuelSettings? = null,
    val searching: Boolean = false,
    val error: String? = null,
    val readySessionId: String? = null,
)
