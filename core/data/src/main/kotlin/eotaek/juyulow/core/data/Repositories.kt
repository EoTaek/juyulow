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

data class SearchProgress(
    val session: ComparisonSession? = null,
    val complete: Boolean = false,
    val error: String? = null,
    val empty: Boolean = false
)

class SearchHandle(val state: StateFlow<SearchProgress>, val cancel: () -> Unit)

interface StationRepository {
    fun startSearch(
        journey: JourneyContext,
        conditions: RefuelConditions,
        sessions: ComparisonSessionRepository
    ): SearchHandle? = null

    fun pauseSearch(paused: Boolean) {}
    fun cancelActiveSearch() {}

    suspend fun search(journey: JourneyContext): List<Station>
    suspend fun search(journey: JourneyContext, conditions: RefuelConditions): List<Station> =
        search(journey)
}

interface ComparisonSessionRepository {
    fun create(
        journey: JourneyContext,
        conditions: RefuelConditions,
        stations: List<Station>
    ): ComparisonSession

    fun observe(id: String): Flow<ComparisonSession?> = kotlinx.coroutines.flow.flowOf(get(id))
    fun update(session: ComparisonSession) {}
    fun get(id: String): ComparisonSession?
}

interface JourneyRepository {
    val context: StateFlow<JourneyContext>
    val navigationState: StateFlow<eotaek.juyulow.core.model.NaviState>? get() = null
    suspend fun initialize() {}
    suspend fun refresh() {}
    suspend fun release() {}
    suspend fun reconcile(): NavigationSelection? = null
    suspend fun openNavigation() {}
    fun setHasDestination(hasDestination: Boolean)
    suspend fun select(
        session: ComparisonSession,
        station: Station,
        mode: JourneyMode
    ): NavigationSelection

    fun selection(id: String): NavigationSelection?
}
