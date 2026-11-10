package eotaek.juyulow.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eotaek.juyulow.core.data.SettingsRepository
import eotaek.juyulow.core.data.VehicleInfoRepository
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.core.model.VehicleField
import eotaek.juyulow.core.model.VehicleProperty
import eotaek.juyulow.core.model.resolveRefuelConditions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val draft: RefuelSettings? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    val vehicleFields: Map<VehicleProperty, VehicleField> = emptyMap(),
) {
    val conditions get() = draft?.let { resolveRefuelConditions(it, vehicleFields) }
}

class SettingsViewModel(
    private val repository: SettingsRepository,
    vehicle: VehicleInfoRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            vehicle.fields.collect { fields -> mutableState.update { it.copy(vehicleFields = fields) } }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val settings = repository.settings.first()
                mutableState.update { it.copy(draft = settings, error = null, saved = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(error = "주유 조건을 불러오지 못했어요.") }
            }
        }
    }

    fun edit(update: (RefuelSettings) -> RefuelSettings) {
        val current = state.value
        if (!current.saving && !current.saved && current.draft != null) mutableState.update {
            it.copy(
                draft = update(current.draft).let { next ->
                    // The SDK may finish while the popup is open. Keep the manual draft intact.
                    val conditions = requireNotNull(current.conditions)
                    next.copy(
                        liters = if (conditions.automaticLiters) current.draft.liters else next.liters,
                        efficiencyKmPerL = if (conditions.automaticEfficiency) current.draft.efficiencyKmPerL else next.efficiencyKmPerL,
                    )
                },
                error = null
            )
        }
    }

    fun apply() {
        val snapshot = state.value
        val draft = snapshot.draft ?: return
        if (snapshot.saving || snapshot.saved) return
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                repository.save(draft)
                mutableState.update { it.copy(saving = false, saved = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        saving = false,
                        error = "조건을 저장하지 못했어요. 다시 적용해 주세요."
                    )
                }
            }
        }
    }
}
