package eotaek.juyulow.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eotaek.juyulow.core.design.component.PleosInlineError
import eotaek.juyulow.core.design.component.PleosButton
import eotaek.juyulow.core.design.component.PleosIcon
import eotaek.juyulow.core.design.component.PleosIconButton
import eotaek.juyulow.core.design.component.PleosStepper
import eotaek.juyulow.core.design.component.PleosSwitch
import eotaek.juyulow.core.design.theme.JUYULOWTheme
import eotaek.juyulow.core.design.theme.pleosShadow
import eotaek.juyulow.core.model.RefuelSettings

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onDismissGuardChanged: ((() -> Boolean)?) -> Unit,
) {
    DisposableEffect(viewModel, onDismissGuardChanged) {
        // Read the current state at dismissal time, including before the next recomposition.
        onDismissGuardChanged { !viewModel.state.value.saving }
        onDispose { onDismissGuardChanged(null) }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDismiss() }
    SettingsScreen(
        state,
        { next -> viewModel.edit { next } },
        viewModel::apply,
        onDismiss,
        viewModel::load
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState, onChange: (RefuelSettings) -> Unit, onApply: () -> Unit,
    onDismiss: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier,
) {
    val colors = JUYULOWTheme.colors
    val type = JUYULOWTheme.typography
    BoxWithConstraints(
        modifier
            .widthIn(max = 880.dp)
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        val narrow = maxWidth < 700.dp
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.backgroundPopup,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .pleosShadow(RoundedCornerShape(28.dp), JUYULOWTheme.shadows.level3)
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(if (narrow) 24.dp else 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("주유 조건", style = type.titleMediumBold, modifier = Modifier.weight(1f))
                    PleosIconButton(PleosIcon.Close, "설정 닫기", onDismiss, enabled = !state.saving)
                }
                val draft = state.draft
                if (draft == null) {
                    if (state.error == null) CircularProgressIndicator() else PleosButton(
                        "다시 불러오기",
                        onRetry
                    )
                } else {
                    SettingRow("예상 주유량", narrow) {
                        PleosStepper(
                            "예상 주유량",
                            draft.liters,
                            "L",
                            10..100,
                            10,
                            { onChange(draft.copy(liters = it)) },
                            enabled = !state.saving
                        )
                    }
                    SettingRow("차량 연비", narrow) {
                        PleosStepper(
                            "차량 연비",
                            draft.efficiencyKmPerL,
                            "km/L",
                            1..30,
                            1,
                            { onChange(draft.copy(efficiencyKmPerL = it)) },
                            enabled = !state.saving
                        )
                    }
                    SettingRow("주유 시간", narrow) {
                        PleosStepper(
                            "주유 시간",
                            draft.refuelMinutes,
                            "분",
                            1..20,
                            1,
                            { onChange(draft.copy(refuelMinutes = it)) },
                            enabled = !state.saving
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PleosSwitch(
                            draft.includeTimeCost,
                            "시간 비용 반영",
                            { onChange(draft.copy(includeTimeCost = it)) },
                            enabled = !state.saving
                        )
                        Text(
                            "시간당 12,000원",
                            style = type.labelSmall,
                            color = colors.textDarkTertiary
                        )
                    }
                    Text(
                        "모든 주유소에 같은 조건으로 적용해요.",
                        style = type.labelMedium,
                        color = colors.textDarkTertiary
                    )
                }
                state.error?.let {
                    PleosInlineError(it)
                }
                PleosButton(
                    "적용하기",
                    onApply,
                    Modifier.fillMaxWidth(),
                    trailingIcon = PleosIcon.Check,
                    enabled = draft != null,
                    loading = state.saving
                )
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, narrow: Boolean, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (narrow) {
            Text(label, style = JUYULOWTheme.typography.bodyMedium)
            content()
        } else Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(label, style = JUYULOWTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Box(Modifier.width(380.dp)) { content() }
        }
        HorizontalDivider(color = JUYULOWTheme.colors.basic200)
    }
}

@Preview(name = "Settings · Landscape", widthDp = 1700, heightDp = 1184)
@Preview(name = "Settings · Portrait", widthDp = 840, heightDp = 1184)
@Composable
private fun SettingsPreview() {
    JUYULOWTheme {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { SettingsScreen(SettingsUiState(RefuelSettings()), {}, {}, {}, {}) }
    }
}

@Preview(name = "Settings · Dark Landscape", widthDp = 1700, heightDp = 1184)
@Preview(name = "Settings · Dark Portrait", widthDp = 840, heightDp = 1184)
@Composable
private fun SettingsDarkPreview() {
    JUYULOWTheme(darkTheme = true) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { SettingsScreen(SettingsUiState(RefuelSettings()), {}, {}, {}, {}) }
    }
}
