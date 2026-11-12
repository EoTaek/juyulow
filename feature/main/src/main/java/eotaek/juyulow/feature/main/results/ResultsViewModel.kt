package eotaek.juyulow.feature.main.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eotaek.juyulow.core.data.ComparisonSessionRepository
import eotaek.juyulow.core.data.JourneyRepository
import eotaek.juyulow.core.data.StationRepository
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.core.model.StationSort
import eotaek.juyulow.core.model.sameRouteTargetsAs
import eotaek.juyulow.core.model.sameTripOriginAs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ResultsViewModel(
    sessionId: String,
    private val stations: StationRepository,
    private val sessions: ComparisonSessionRepository,
    private val journeys: JourneyRepository,
) : ViewModel() {
    private val initial = sessions.get(sessionId)
    private val mutableState = MutableStateFlow(
        withFirstSelection(
            ResultsUiState(
                session = initial,
                mode = initial?.context?.journey?.defaultMode ?: JourneyMode.DESTINATION,
                error = if (initial == null) "검색 결과가 없어요. 홈에서 다시 찾아 주세요." else null,
            )
        )
    )
    val state = mutableState.asStateFlow()
    private var searchJob: Job? = null
    private var generation = 0

    init {
        viewModelScope.launch {
            sessions.observe(sessionId).collect { next ->
                if (next != null && !state.value.settingRoute && state.value.completedSelectionId == null) mutableState.update {
                    it.copy(session = next, selectedId = it.selectedId ?: next.firstReadyId)
                }
            }
        }

        if (initial?.context?.journey?.destinationOnly == true) viewModelScope.launch {
            journeys.context.collect { context ->
                val session = state.value.session ?: return@collect
                val a = session.context.journey.navigation
                val b = context.navigation
                if (a != null && b != null) {
                    val origin = session.context.journey.tripOrigin ?: a
                    val reusable = origin.sameTripOriginAs(b)
                    val sameTargets = a.sameRouteTargetsAs(b)
                    val resumeSearch = !sameTargets && state.value.confirmReplacement
                    mutableState.update {
                        it.copy(
                            comparisonExpired = !reusable || !sameTargets,
                            mode = if (it.mode in context.availableModes) it.mode else JourneyMode.DESTINATION,
                            session = session.copy(
                                context = session.context.copy(
                                    journey = session.context.journey.copy(waypointSupported = context.waypointSupported)
                                )
                            ),
                            confirmReplacement = sameTargets && it.confirmReplacement,
                        )
                    }
                    if (resumeSearch) stations.pauseSearch(false)
                }
            }
        }
    }

    fun select(id: String) {
        if (!state.value.busy && state.value.session?.stations?.any { it.id == id && it.outboundMeters != null && it.outboundSeconds != null } == true) mutableState.update {
            it.copy(
                selectedId = id,
                error = null
            )
        }
    }

    fun changeMode(mode: JourneyMode) {
        val snapshot = state.value
        if (!snapshot.busy && mode in (snapshot.session?.context?.journey?.availableModes
                ?: emptyList())
        ) mutableState.update { it.copy(mode = mode, error = null) }
    }

    fun changeSort(sort: StationSort) {
        if (!state.value.busy) mutableState.update { it.copy(sort = sort) }
    }

    fun refresh() {
        val snapshot = state.value
        val oldSession = snapshot.session ?: return
        if (snapshot.busy) return
        val request = ++generation
        mutableState.update { it.copy(searching = true, error = null) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                // Keep the search snapshot, even after a mock route is configured.
                val handle = stations.startSearch(
                    oldSession.context.journey,
                    oldSession.context.conditions,
                    sessions
                )
                if (handle != null) {
                    val ready =
                        handle.state.first { it.session?.firstReadyId != null || it.complete }
                    if (request != generation) return@launch
                    val next = ready.session?.takeIf { it.firstReadyId != null }
                    if (next == null) mutableState.update {
                        it.copy(
                            searching = false,
                            error = ready.error
                        )
                    }
                    else {
                        mutableState.update {
                            it.copy(
                                searching = false,
                                session = next,
                                selectedId = next.firstReadyId
                            )
                        }
                        sessions.observe(next.id).collect { update ->
                            if (update != null && !state.value.settingRoute && state.value.completedSelectionId == null)
                                mutableState.update { it.copy(session = update) }
                        }
                    }
                    return@launch
                }
                val found =
                    stations.search(oldSession.context.journey, oldSession.context.conditions)
                if (request != generation) return@launch
                val next =
                    sessions.create(
                        if (oldSession.context.journey.destinationOnly) journeys.context.value else oldSession.context.journey,
                        oldSession.context.conditions,
                        found
                    )
                mutableState.update {
                    withFirstSelection(
                        it.copy(
                            session = next,
                            mode = next.context.journey.defaultMode,
                            searching = false,
                            selectedId = null
                        )
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (request == generation) mutableState.update {
                    it.copy(
                        searching = false,
                        error = eotaek.juyulow.feature.main.common.navigationFailureMessage(
                            error,
                            "다시 찾지 못했어요. 기존 검색 결과를 표시해요."
                        )
                    )
                }
            }
        }
    }

    fun dismissReplacement() {
        stations.pauseSearch(false)
        mutableState.update { it.copy(confirmReplacement = false) }
    }

    fun requestRoute(mode: JourneyMode) {
        val snapshot = state.value
        if (snapshot.busy || snapshot.routeUncertain || snapshot.completedSelectionId != null ||
            mode !in (snapshot.session?.context?.journey?.availableModes ?: emptyList())
        ) return
        changeMode(mode)
        confirm()
    }

    fun confirm(confirmed: Boolean = false) {
        val snapshot = state.value
        val session = snapshot.session ?: return
        val selected = snapshot.selected ?: return
        if (snapshot.busy || snapshot.completedSelectionId != null || snapshot.routeUncertain) return
        if (session.context.journey.destinationOnly && session.context.journey.hasDestination && snapshot.mode == JourneyMode.DESTINATION && !confirmed) {
            stations.pauseSearch(true)
            mutableState.update { it.copy(confirmReplacement = true) }
            return
        }
        mutableState.update {
            it.copy(
                settingRoute = true,
                confirmReplacement = false,
                error = null
            )
        }
        viewModelScope.launch {
            try {
                val selection = journeys.select(session, selected.station, snapshot.mode)
                mutableState.update {
                    it.copy(
                        settingRoute = false,
                        completedSelectionId = selection.id
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                stations.pauseSearch(false)
                mutableState.update {
                    it.copy(
                        settingRoute = false,
                        routeUncertain = journeys.navigationState?.value?.status == eotaek.juyulow.core.model.NaviStatus.UNCERTAIN,
                        error = if (journeys.navigationState?.value?.status == eotaek.juyulow.core.model.NaviStatus.UNCERTAIN)
                            "경로 설정 결과를 확인하지 못했어요. 상태를 다시 확인해 주세요."
                        else eotaek.juyulow.feature.main.common.navigationFailureMessage(
                            error,
                            "경로를 설정하지 못했어요. 현재 위치나 경로가 달라졌는지 확인해 주세요."
                        )
                    )
                }
            }
        }
    }

    fun consumeSelection() {
        mutableState.update { it.copy(completedSelectionId = null) }
    }

    fun reconcile() {
        if (state.value.busy) return
        mutableState.update { it.copy(settingRoute = true) }
        viewModelScope.launch {
            try {
                val recovered = journeys.reconcile()
                mutableState.update {
                    it.copy(
                        settingRoute = false, completedSelectionId = recovered?.id,
                        error = if (recovered == null) "아직 경로 설정 결과를 확인하지 못했어요." else null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        settingRoute = false,
                        error = "내비게이션 상태를 확인하지 못했어요."
                    )
                }
            }
        }
    }

    fun cancelSearch() {
        stations.cancelActiveSearch()
        generation++
        searchJob?.cancel()
        mutableState.update { it.copy(searching = false) }
    }

    override fun onCleared() {
        generation++
    }

    private fun withFirstSelection(state: ResultsUiState): ResultsUiState =
        state.copy(
            selectedId = state.session?.firstReadyId ?: state.evaluated.firstOrNull()?.station?.id
        )
}
