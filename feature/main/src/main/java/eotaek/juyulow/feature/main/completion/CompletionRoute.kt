package eotaek.juyulow.feature.main.completion

import androidx.compose.runtime.Composable

@Composable
fun CompletionRoute(viewModel: CompletionViewModel, onCompare: () -> Unit, onHome: () -> Unit) {
    CompletionScreen(viewModel.selection, onCompare, onHome)
}
