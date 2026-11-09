package eotaek.juyulow.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeRoute(viewModel: HomeViewModel, onResults: (String) -> Unit, onSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.readySessionId) {
        state.readySessionId?.let { id -> onResults(id); viewModel.consumeSearch() }
    }
    BackHandler(state.searching) { viewModel.cancelSearch() }
    HomeScreen(state, viewModel::search, onSettings, viewModel::setHasDestination)
}

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

@Composable
fun CompletionRoute(viewModel: CompletionViewModel, onCompare: () -> Unit, onHome: () -> Unit) {
    CompletionScreen(viewModel.selection, onCompare, onHome)
}
