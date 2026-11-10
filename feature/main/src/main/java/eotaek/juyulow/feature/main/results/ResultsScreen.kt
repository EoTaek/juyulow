package eotaek.juyulow.feature.main.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eotaek.juyulow.core.design.component.PleosButton
import eotaek.juyulow.core.design.component.PleosIcon
import eotaek.juyulow.core.design.component.PleosIconButton
import eotaek.juyulow.core.design.component.PleosInlineError
import eotaek.juyulow.core.design.component.PleosTab
import eotaek.juyulow.core.design.component.PleosTabs
import eotaek.juyulow.core.design.component.RefuelConditionsSummary
import eotaek.juyulow.core.design.component.vehicleNumber
import eotaek.juyulow.core.design.theme.JUYULOWTheme
import eotaek.juyulow.core.model.ComparisonContext
import eotaek.juyulow.core.model.ComparisonSession
import eotaek.juyulow.core.model.JourneyContext
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.core.model.Station
import eotaek.juyulow.core.model.StationSort
import eotaek.juyulow.core.model.resolveRefuelConditions
import eotaek.juyulow.feature.main.common.actionLabel
import eotaek.juyulow.feature.main.common.kilometers
import eotaek.juyulow.feature.main.common.label
import eotaek.juyulow.feature.main.common.minutes
import eotaek.juyulow.feature.main.results.component.SelectionPanel
import eotaek.juyulow.feature.main.results.component.StationRow

@Composable
fun ResultsScreen(
    state: ResultsUiState, onBack: () -> Unit, onRefresh: () -> Unit, onSelect: (String) -> Unit,
    onMode: (JourneyMode) -> Unit, onSort: (StationSort) -> Unit, onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    onReconcile: () -> Unit = {},
) {
    val colors = JUYULOWTheme.colors
    val type = JUYULOWTheme.typography
    val batch = state.session
    Surface(modifier.fillMaxSize(), color = colors.backgroundPrimary) {
        BoxWithConstraints {
            val narrow = maxWidth < 1100.dp
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(if (narrow) 24.dp else 40.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    PleosIconButton(PleosIcon.Back, "이전 화면", onBack, enabled = !state.settingRoute)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "내 주변 주유소",
                            style = if (narrow) type.titleSmallBold else type.titleLargeBold
                        )
                        if (batch != null) {
                            val route = batch.context.journey
                            Text(
                                if (route.hasDestination && route.baselineSeconds != null && route.baselineMeters != null) "${route.destination}까지 ${
                                    minutes(
                                        route.baselineSeconds!!
                                    )
                                }분 · ${
                                    kilometers(
                                        route.baselineMeters!!
                                    )
                                } km / ${vehicleNumber(batch.context.conditions.liters)} L 주유 기준"
                                else "${route.destination ?: "현재 위치"} / ${vehicleNumber(batch.context.conditions.liters)} L 주유 기준",
                                style = type.labelMedium, color = colors.textDarkSecondary,
                            )
                            RefuelConditionsSummary(batch.context.conditions)
                        }
                    }
                }
                if (batch != null) PleosTabs((if (batch.context.journey.destinationOnly) JourneyMode.entries else batch.context.journey.availableModes).map {
                    PleosTab(
                        it,
                        if (batch.context.journey.destinationOnly && it == JourneyMode.WAYPOINT) "경유지로 추가" else if (batch.context.journey.destinationOnly && it == JourneyMode.DESTINATION) "목적지로 설정" else it.label,
                        modeIcon(it),
                        enabled = it in batch.context.journey.availableModes,
                    )
                }, state.mode, onMode, enabled = !state.busy)
                state.error?.let {
                    PleosInlineError(it)
                }
                if (state.routeUncertain) PleosButton(
                    "상태 다시 확인",
                    onReconcile,
                    enabled = !state.busy
                )
                if (state.routeExpired) Text(
                    "위치나 경로가 달라져 이전 검색 결과로 설정할 수 없어요.",
                    style = type.labelSmall
                )
                batch?.stations?.firstOrNull()?.observedAtMillis?.let { time ->
                    Text(
                        "검색 시점 ${
                            java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA)
                                .format(java.util.Date(time))
                        } 기준", style = type.labelSmall
                    )
                }
                if (batch?.context?.journey?.destinationOnly == true) Text(
                    if ((batch.context.journey.navigation?.waypoints?.size
                            ?: 0) >= 2
                    ) "경유지는 최대 2개까지 설정할 수 있어요. 거리·시간·비용은 주유소까지 기준이에요." else "거리·시간·비용은 주유소까지 기준이에요. 왕복은 준비 중이에요.",
                    style = type.labelSmall
                )
                if (batch == null) {
                    PleosButton("홈으로", onBack)
                } else {
                    Row(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 24.dp)
                    ) {
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("station-list-pane"),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val refresh: @Composable () -> Unit = {
                                PleosButton(
                                    if (state.searching) "찾는 중" else "다시 찾기",
                                    onRefresh,
                                    icon = PleosIcon.Refresh,
                                    outlined = true,
                                    enabled = !state.busy,
                                    loading = state.searching
                                )
                            }
                            val sort: @Composable () -> Unit = {
                                PleosTabs(
                                    listOf(
                                        PleosTab(StationSort.SAVING, "절약액순"),
                                        PleosTab(StationSort.TIME, "시간순")
                                    ), state.sort, onSort, compact = true, enabled = !state.busy
                                )
                            }
                            if (narrow) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { refresh(); sort() }
                            else Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) { refresh(); sort() }
                            Text(
                                "${batch.stations.size}곳 찾았어요",
                                style = type.labelMedium,
                                color = colors.textDarkSecondary
                            )
                            val listState = rememberLazyListState()
                            LaunchedEffect(batch.id) { listState.scrollToItem(0) }
                            LazyColumn(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .testTag("station-list-scroll")
                                    .selectableGroup(),
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(bottom = 8.dp)
                            ) {
                                items(state.evaluated, key = { it.station.id }) { row ->
                                    StationRow(
                                        row,
                                        state.mode,
                                        row.station.id == state.selectedId,
                                        !state.busy,
                                        narrow
                                    ) { onSelect(row.station.id) }
                                }
                                if (batch.stations.isEmpty()) item {
                                    Text(
                                        "주변 주유소가 없어요. 다시 찾아 주세요.",
                                        style = type.bodySmall
                                    )
                                }
                            }
                            PleosButton(
                                state.mode.actionLabel,
                                onConfirm,
                                Modifier
                                    .fillMaxWidth()
                                    .testTag("confirm-route"),
                                icon = PleosIcon.Navigation,
                                enabled = state.selected != null && !state.busy && !state.routeUncertain && !state.routeExpired,
                                loading = state.settingRoute
                            )
                        }
                        VerticalDivider(color = colors.basic200)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("station-detail-pane")
                        ) {
                            state.selected?.let {
                                SelectionPanel(
                                    it,
                                    batch.context,
                                    state.mode,
                                    narrow
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun modeIcon(mode: JourneyMode): PleosIcon = when (mode) {
    JourneyMode.WAYPOINT -> PleosIcon.Waypoint
    JourneyMode.DESTINATION -> PleosIcon.Flag
    JourneyMode.ROUND_TRIP -> PleosIcon.Refresh
}




internal fun previewSession(): ComparisonSession = ComparisonSession(
    "preview",
    ComparisonContext(JourneyContext(), resolveRefuelConditions(RefuelSettings()), 1740),
    listOf(
        Station(
            "station-0",
            "한길 셀프주유소",
            "S-OIL",
            1635,
            37.6,
            127.0,
            1800,
            300,
            2300,
            420,
            30200,
            2760,
            0
        ),
        Station(
            "station-1",
            "초록 주유소",
            "GS칼텍스",
            1605,
            37.6,
            127.0,
            4800,
            780,
            5300,
            900,
            34600,
            3240,
            500
        ),
        Station(
            "station-2",
            "도담 에너지",
            "현대오일뱅크",
            1740,
            37.6,
            127.0,
            800,
            180,
            1300,
            240,
            29000,
            2640,
            0
        ),
    )
)

@Preview(name = "Results · Landscape", widthDp = 1700, heightDp = 1184)
@Preview(name = "Results · Portrait", widthDp = 840, heightDp = 1184)
@Composable
private fun ResultsPreview() {
    JUYULOWTheme {
        ResultsScreen(
            ResultsUiState(
                previewSession(),
                JourneyMode.WAYPOINT,
                selectedId = "station-0"
            ), {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Results · Dark Landscape", widthDp = 1700, heightDp = 1184)
@Preview(name = "Results · Dark Portrait", widthDp = 840, heightDp = 1184)
@Composable
private fun ResultsDarkPreview() {
    JUYULOWTheme(darkTheme = true) {
        ResultsScreen(
            ResultsUiState(
                previewSession(),
                JourneyMode.WAYPOINT,
                selectedId = "station-0"
            ), {}, {}, {}, {}, {}, {})
    }
}
