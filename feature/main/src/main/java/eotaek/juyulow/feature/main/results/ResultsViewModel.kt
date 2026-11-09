package eotaek.juyulow.feature.main.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eotaek.juyulow.core.data.ComparisonSessionRepository
import eotaek.juyulow.core.data.JourneyRepository
import eotaek.juyulow.core.data.StationRepository
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.core.model.StationSort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun select(id: String) {
        if (!state.value.busy && state.value.session?.stations?.any { it.id == id } == true) mutableState.update {
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
        searchJob = viewModelScope.launch {
            try {
                // Keep the search snapshot, even after a mock route is configured.
                val found = stations.search(oldSession.context.journey)
                if (request != generation) return@launch
                val next =
                    sessions.create(oldSession.context.journey, oldSession.context.settings, found)
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
            } catch (_: Exception) {
                if (request == generation) mutableState.update {
                    it.copy(
                        searching = false,
                        error = "다시 찾지 못했어요. 기존 검색 결과를 표시해요."
                    )
                }
            }
        }
    }

    fun confirm() {
        val snapshot = state.value
        val session = snapshot.session ?: return
        val selected = snapshot.selected ?: return
        if (snapshot.busy || snapshot.completedSelectionId != null) return
        mutableState.update { it.copy(settingRoute = true, error = null) }
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
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        settingRoute = false,
                        error = "경로를 설정하지 못했어요. 다시 시도해 주세요."
                    )
                }
            }
        }
    }

    fun consumeSelection() {
        mutableState.update { it.copy(completedSelectionId = null) }
    }

    fun cancelSearch() {
        generation++
        searchJob?.cancel()
        mutableState.update { it.copy(searching = false) }
    }

    override fun onCleared() {
        generation++
    }

    private fun withFirstSelection(state: ResultsUiState): ResultsUiState =
        state.copy(selectedId = state.evaluated.firstOrNull()?.station?.id)
}
