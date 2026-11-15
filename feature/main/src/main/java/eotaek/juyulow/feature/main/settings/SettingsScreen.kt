package eotaek.juyulow.feature.main.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eotaek.juyulow.core.design.component.PleosButton
import eotaek.juyulow.core.design.component.PleosIcon
import eotaek.juyulow.core.design.component.PleosIconButton
import eotaek.juyulow.core.design.component.PleosInlineError
import eotaek.juyulow.core.design.component.PleosOutlinedCard
import eotaek.juyulow.core.design.component.PleosStepper
import eotaek.juyulow.core.design.component.vehicleFieldDisplay
import eotaek.juyulow.core.design.component.vehicleNumber
import eotaek.juyulow.core.design.theme.JUYULOWTheme
import eotaek.juyulow.core.design.theme.pleosShadow
import eotaek.juyulow.core.model.ComparisonFuel
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.core.model.VehicleProperty

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onDismissGuardChanged: ((() -> Boolean)?) -> Unit,
) {
    DisposableEffect(viewModel, onDismissGuardChanged) {
        // Read the current state at dismissal time, including before the next recomposition.
        onDismissGuardChanged(viewModel::canDismiss)
        onDispose { onDismissGuardChanged(null) }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.dismissRequested, state.saving) {
        if (state.dismissRequested && !state.saving) onDismiss()
    }
    SettingsScreen(
        state,
        viewModel::edit,
        onDismiss,
        { if (state.draft == null) viewModel.load() else viewModel.retrySave() }
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState, onChange: ((RefuelSettings) -> RefuelSettings) -> Unit,
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
                    PleosIconButton(PleosIcon.Close, "설정 닫기", onDismiss)
                }
                val draft = state.draft
                if (draft == null) {
                    if (state.error == null) CircularProgressIndicator() else PleosButton(
                        "다시 불러오기",
                        onRetry
                    )
                } else {
                    SettingRow("검색 유종", narrow) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ComparisonFuel.entries.forEach { fuel ->
                                val selected = draft.comparisonFuel == fuel
                                PleosButton(
                                    onClick = { onChange { it.copy(comparisonFuel = fuel) } },
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics { this.selected = selected },
                                    outlined = !selected,
                                ) {
                                    Text(
                                        fuel.label,
                                        style = type.labelMedium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                    SettingRow("예상 주유량", narrow) {
                        PleosStepper(
                            "예상 주유량", draft.liters, "L", 10..100, 10,
                            { value -> onChange { it.copy(liters = value) } })
                    }
                    SettingRow("평균연비", narrow) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PleosStepper(
                                "평균연비", draft.efficiencyKmPerL, "km/L", 1..30, 1,
                                { value -> onChange { it.copy(efficiencyKmPerL = value) } })
                        }
                    }
                    SettingRow("주유 시간", narrow) {
                        PleosStepper(
                            "주유 시간",
                            draft.refuelMinutes,
                            "분",
                            1..20,
                            1,
                            { value -> onChange { it.copy(refuelMinutes = value) } }
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("차량 조회 정보", style = type.bodyMediumStrong)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            VehicleInfoCard(
                                "탱크 잔량 / 용량", Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                val level = vehicleFieldDisplay(
                                    VehicleProperty.FUEL_LEVEL,
                                    state.vehicleFields[VehicleProperty.FUEL_LEVEL]
                                )
                                val capacity = vehicleFieldDisplay(
                                    VehicleProperty.FUEL_CAPACITY,
                                    state.vehicleFields[VehicleProperty.FUEL_CAPACITY]
                                )
                                Text(
                                    buildAnnotatedString {
                                        withStyle(type.bodyLargeStrong.toSpanStyle()) {
                                            append(level)
                                        }
                                        withStyle(
                                            type.bodyMedium.toSpanStyle()
                                                .copy(color = colors.textDarkSecondary)
                                        ) {
                                            append(" / ")
                                            append(capacity)
                                        }
                                    },
                                    style = type.bodyLargeStrong,
                                )
                            }
                            val conditions = requireNotNull(state.conditions)
                            VehicleInfoCard(
                                "누적 연비", Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Text(
                                    "${vehicleNumber(conditions.efficiencyKmPerL)} km/L",
                                    style = type.bodyLargeStrong,
                                )
                                Text(
                                    if (conditions.automaticEfficiency) "절약액 계산에 적용" else "기본 연비 사용 중",
                                    style = type.labelSmall, color = colors.textDarkSecondary,
                                )
                            }
                        }
                    }
                }
                state.error?.let {
                    PleosInlineError(it)
                }
                if (draft != null && state.error != null && !state.saving) {
                    PleosButton("다시 저장", onRetry, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun VehicleInfoCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    PleosOutlinedCard(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = JUYULOWTheme.typography.labelMedium,
                color = JUYULOWTheme.colors.textDarkSecondary
            )
            content()
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
            Box(Modifier.width(520.dp)) { content() }
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
        ) { SettingsScreen(SettingsUiState(RefuelSettings()), {}, {}, {}) }
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
        ) { SettingsScreen(SettingsUiState(RefuelSettings()), {}, {}, {}) }
    }
}
