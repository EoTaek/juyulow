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
    private val mutableState = MutableStateFlow(HomeUiState())
    val state = mutableState.asStateFlow()
    private var searchJob: Job? = null
    private var searchGeneration = 0

    init {
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
        val conditions = snapshot.conditions ?: return
        if (snapshot.searching || snapshot.readySessionId != null) return
        val generation = ++searchGeneration
        mutableState.update { it.copy(searching = true, error = null) }
        searchJob = viewModelScope.launch {
            try {
                val found = stations.search(snapshot.journey)
                if (generation != searchGeneration) return@launch
                val session = sessions.create(snapshot.journey, conditions, found)
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
