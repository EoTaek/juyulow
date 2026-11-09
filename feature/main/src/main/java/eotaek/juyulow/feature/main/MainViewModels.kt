package eotaek.juyulow.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eotaek.juyulow.core.data.ComparisonSessionRepository
import eotaek.juyulow.core.data.JourneyRepository
import eotaek.juyulow.core.data.SettingsRepository
import eotaek.juyulow.core.data.StationRepository
import eotaek.juyulow.core.model.ComparisonSession
import eotaek.juyulow.core.model.JourneyContext
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.core.model.NavigationSelection
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.core.model.Station
import eotaek.juyulow.core.model.StationComparison
import eotaek.juyulow.core.model.StationSort
import eotaek.juyulow.core.model.compareStation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val journey: JourneyContext = JourneyContext(),
    val settings: RefuelSettings? = null,
    val searching: Boolean = false,
    val error: String? = null,
    val readySessionId: String? = null,
)

class HomeViewModel(
    private val stations: StationRepository,
    private val sessions: ComparisonSessionRepository,
    private val journeys: JourneyRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState())
    val state = mutableState.asStateFlow()
    private var searchJob: Job? = null
    private var searchGeneration = 0

    init {
        viewModelScope.launch {
            journeys.context.collect { route ->
                mutableState.update {
                    it.copy(
                        journey = route
                    )
                }
            }
        }
        viewModelScope.launch {
            try {
                settings.settings.collect { value -> mutableState.update { it.copy(settings = value) } }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        settings = RefuelSettings(),
                        error = "저장된 조건을 읽지 못해 기본 조건을 사용해요."
                    )
                }
            }
        }
    }

    fun setHasDestination(value: Boolean) {
        if (!state.value.searching) journeys.setHasDestination(value)
    }

    fun search() {
        val snapshot = state.value
        val settings = snapshot.settings ?: return
        if (snapshot.searching || snapshot.readySessionId != null) return
        val generation = ++searchGeneration
        mutableState.update { it.copy(searching = true, error = null) }
        searchJob = viewModelScope.launch {
            try {
                val found = stations.search(snapshot.journey)
                if (generation != searchGeneration) return@launch
                val session = sessions.create(snapshot.journey, settings, found)
                mutableState.update { it.copy(searching = false, readySessionId = session.id) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation == searchGeneration) mutableState.update {
                    it.copy(
                        searching = false,
                        error = "주유소를 찾지 못했어요. 다시 시도해 주세요."
                    )
                }
            }
        }
    }

    fun consumeSearch() {
        mutableState.update { it.copy(readySessionId = null) }
    }

    fun cancelSearch() {
        searchGeneration++
        searchJob?.cancel()
        mutableState.update { it.copy(searching = false, readySessionId = null) }
    }

    override fun onCleared() {
        searchGeneration++
    }
}

data class EvaluatedStation(val station: Station, val comparison: StationComparison)

data class ResultsUiState(
    val session: ComparisonSession? = null,
    val mode: JourneyMode = JourneyMode.DESTINATION,
    val sort: StationSort = StationSort.SAVING,
    val selectedId: String? = null,
    val searching: Boolean = false,
    val settingRoute: Boolean = false,
    val error: String? = null,
    val completedSelectionId: String? = null,
) {
    val busy: Boolean get() = searching || settingRoute
    val evaluated: List<EvaluatedStation>
        get() {
            val batch = session ?: return emptyList()
            val rows =
                batch.stations.map { EvaluatedStation(it, compareStation(batch.context, it, mode)) }
            return when (sort) {
                StationSort.SAVING -> rows.sortedByDescending { it.comparison.netSavingWon }
                StationSort.TIME -> rows.sortedBy { it.comparison.costDurationSeconds }
            }
        }
    val selected: EvaluatedStation? get() = evaluated.find { it.station.id == selectedId }
}

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

class CompletionViewModel(selectionId: String, journeys: JourneyRepository) : ViewModel() {
    val selection: NavigationSelection? = journeys.selection(selectionId)
}
