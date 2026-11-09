package eotaek.juyulow.feature.main.home

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
