package eotaek.juyulow.feature.main.completion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eotaek.juyulow.core.data.JourneyRepository
import eotaek.juyulow.core.model.NavigationSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CompletionViewModel(selectionId: String, private val journeys: JourneyRepository) :
    ViewModel() {
    val selection: NavigationSelection? = journeys.selection(selectionId)
    val canOpenNavigation = journeys.navigationState != null
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private var opening = false
    fun openNavigation() {
        if (opening) return
        opening = true
        viewModelScope.launch {
            try {
                journeys.openNavigation(); mutableError.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableError.value = "목적지는 설정됐지만 내비게이션 화면을 열지 못했어요."
            } finally {
                opening = false
            }
        }
    }
}
