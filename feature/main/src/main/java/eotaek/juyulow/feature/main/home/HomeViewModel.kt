package eotaek.juyulow.feature.main.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eotaek.juyulow.core.data.ComparisonSessionRepository
import eotaek.juyulow.core.data.JourneyRepository
import eotaek.juyulow.core.data.SettingsRepository
import eotaek.juyulow.core.data.StationRepository
import eotaek.juyulow.core.data.VehicleInfoRepository
import eotaek.juyulow.core.model.RefuelSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val stations: StationRepository,
    private val sessions: ComparisonSessionRepository,
    private val journeys: JourneyRepository,
    settings: SettingsRepository,
    vehicle: VehicleInfoRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        HomeUiState(
            journey = journeys.context.value,
            navigationReady = journeys.navigationState == null
        )
    )
    val state = mutableState.asStateFlow()
    private var searchJob: Job? = null
    private var searchGeneration = 0

    init {
        journeys.navigationState?.let { navigation ->
            mutableState.update { it.copy(navigationReady = false) }
            viewModelScope.launch {
                navigation.collect { n ->
                    mutableState.update {
                        it.copy(
                            navigationReady = n.status == eotaek.juyulow.core.model.NaviStatus.READY,
                            navigationMessage = if (n.tripTotal > 0) "주유소 경로 조회 ${n.tripCompleted}/${n.tripTotal}" else when (n.status) {
                                eotaek.juyulow.core.model.NaviStatus.IDLE,
                                eotaek.juyulow.core.model.NaviStatus.CONNECTING -> "내비게이션에 연결하고 있어요."

                                eotaek.juyulow.core.model.NaviStatus.READING -> "내비게이션 상태를 확인하고 있어요."
                                eotaek.juyulow.core.model.NaviStatus.READY -> null
                                else -> "내비게이션 상태를 확인할 수 없어요."
                            }
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            vehicle.fields.collect { fields -> mutableState.update { it.copy(vehicleFields = fields) } }
        }
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
            } catch (error: Exception) {
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
        val conditions = snapshot.conditions ?: return
        if (snapshot.searching || snapshot.readySessionId != null || !snapshot.navigationReady) return
        val generation = ++searchGeneration
        mutableState.update { it.copy(searching = true, error = null) }
        searchJob = viewModelScope.launch {
            try {
                val found = stations.search(snapshot.journey)
                if (generation != searchGeneration) return@launch
                val journey =
                    if (snapshot.journey.destinationOnly) journeys.context.value else snapshot.journey
                val session = sessions.create(journey, conditions, found)
                mutableState.update { it.copy(searching = false, readySessionId = session.id) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == searchGeneration) mutableState.update {
                    it.copy(
                        searching = false,
                        error = eotaek.juyulow.feature.main.common.navigationFailureMessage(
                            error,
                            "주유소를 찾지 못했어요. 다시 시도해 주세요."
                        )
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
