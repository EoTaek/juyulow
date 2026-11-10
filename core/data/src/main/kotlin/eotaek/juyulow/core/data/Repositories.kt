package eotaek.juyulow.core.data

import eotaek.juyulow.core.model.ComparisonSession
import eotaek.juyulow.core.model.JourneyContext
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.core.model.NavigationSelection
import eotaek.juyulow.core.model.RefuelConditions
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.core.model.Station
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val settings: Flow<RefuelSettings>
    suspend fun save(settings: RefuelSettings)
}

interface StationRepository {
    suspend fun search(journey: JourneyContext): List<Station>
}

interface ComparisonSessionRepository {
    fun create(
        journey: JourneyContext,
        conditions: RefuelConditions,
        stations: List<Station>
    ): ComparisonSession

    fun get(id: String): ComparisonSession?
}

interface JourneyRepository {
    val context: StateFlow<JourneyContext>
    fun setHasDestination(hasDestination: Boolean)
    suspend fun select(
        session: ComparisonSession,
        station: Station,
        mode: JourneyMode
    ): NavigationSelection

    fun selection(id: String): NavigationSelection?
}
