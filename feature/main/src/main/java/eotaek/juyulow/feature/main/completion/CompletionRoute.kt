package eotaek.juyulow.feature.main.completion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CompletionRoute(viewModel: CompletionViewModel, onHome: () -> Unit) {
    val error by viewModel.error.collectAsStateWithLifecycle()
    CompletionScreen(
        viewModel.selection,
        onHome,
        onOpenNavigation = if (viewModel.canOpenNavigation) viewModel::openNavigation else null,
        error = error
    )
}
