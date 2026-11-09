package eotaek.juyulow.feature.main.results

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ResultsRoute(viewModel: ResultsViewModel, onBack: () -> Unit, onComplete: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.completedSelectionId) {
        state.completedSelectionId?.let { id -> onComplete(id); viewModel.consumeSelection() }
    }
    ResultsScreen(
        state,
        {
            viewModel.cancelSearch()
            onBack()
        },
        viewModel::refresh,
        viewModel::select,
        viewModel::changeMode,
        viewModel::changeSort,
        viewModel::confirm
    )
}
