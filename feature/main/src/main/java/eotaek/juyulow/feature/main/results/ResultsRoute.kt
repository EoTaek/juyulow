package eotaek.juyulow.feature.main.results

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eotaek.juyulow.core.design.component.PleosConfirmationDialog

@Composable
fun ResultsRoute(
    viewModel: ResultsViewModel, onBack: () -> Unit, onComplete: (String) -> Unit,
    onDismissGuard: (((() -> Boolean)?) -> Unit) = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(state.settingRoute) { }
    DisposableEffect(viewModel) {
        onDismissGuard { !viewModel.state.value.settingRoute }
        onDispose { onDismissGuard(null) }
    }
    if (state.confirmReplacement) PleosConfirmationDialog(
        onDismissRequest = viewModel::dismissReplacement,
        title = "목적지를 변경할까요?",
        description = "${state.session?.context?.journey?.destination} 목적지와 기존 경유지를 제거하고 ${state.selected?.station?.name}로 변경해요.",
        confirmLabel = "목적지 변경",
        onConfirm = { viewModel.confirm(true) },
    )
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
        viewModel::changeSort,
        viewModel::requestRoute,
        onReconcile = viewModel::reconcile,
    )
}
