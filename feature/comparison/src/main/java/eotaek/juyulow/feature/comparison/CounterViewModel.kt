package eotaek.juyulow.feature.comparison

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CounterViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CounterUiState())
    val uiState: StateFlow<CounterUiState> = _uiState.asStateFlow()

    fun onAction(action: CounterAction) {
        _uiState.update { state ->
            when (action) {
                CounterAction.Increment -> state.copy(
                    count = if (state.count < Int.MAX_VALUE) state.count + 1 else state.count
                )
                CounterAction.Decrement -> state.copy(count = (state.count - 1).coerceAtLeast(0))
                CounterAction.Reset -> CounterUiState()
            }
        }
    }
}
