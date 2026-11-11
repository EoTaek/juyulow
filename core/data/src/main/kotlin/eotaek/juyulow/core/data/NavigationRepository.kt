package eotaek.juyulow.core.data

import eotaek.juyulow.core.model.*
import kotlinx.coroutines.flow.StateFlow

/** Explicitly owned session. initialize/refresh also serve initial/foreground reads. */
interface NavigationRepository {
    val state: StateFlow<NaviState>
    suspend fun initialize(): NaviResult<NaviSnapshot>
    suspend fun refresh(): NaviResult<NaviSnapshot>
    suspend fun trips(candidates: List<NaviCandidate>): NaviResult<List<NaviTrip>>
    suspend fun setRoute(expected: NaviSnapshot, candidate: NaviCandidate, asWaypoint: Boolean): NaviResult<NaviSnapshot>
    /** Success means submission only, never confirmed foreground visibility. */
    suspend fun openNavigation(): NaviResult<Unit>
    suspend fun release()
}
