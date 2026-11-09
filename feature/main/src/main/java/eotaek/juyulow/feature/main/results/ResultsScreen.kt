package eotaek.juyulow.feature.main.results

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eotaek.juyulow.core.design.component.PleosButton
import eotaek.juyulow.core.design.component.PleosIcon
import eotaek.juyulow.core.design.component.PleosIconButton
import eotaek.juyulow.core.design.component.PleosTab
import eotaek.juyulow.core.design.component.PleosTabs
import eotaek.juyulow.core.design.theme.JUYULOWTheme
import eotaek.juyulow.core.model.ComparisonContext
import eotaek.juyulow.core.model.ComparisonSession
import eotaek.juyulow.core.model.JourneyContext
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.core.model.Station
import eotaek.juyulow.core.model.StationSort
import eotaek.juyulow.feature.main.common.actionLabel
import eotaek.juyulow.feature.main.common.kilometers
import eotaek.juyulow.feature.main.common.label
import eotaek.juyulow.feature.main.common.minutes
import eotaek.juyulow.feature.main.common.won
import kotlin.math.abs

@Composable
fun ResultsScreen(
    state: ResultsUiState, onBack: () -> Unit, onRefresh: () -> Unit, onSelect: (String) -> Unit,
    onMode: (JourneyMode) -> Unit, onSort: (StationSort) -> Unit, onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
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
                                if (route.hasDestination) "${route.destination}까지 ${minutes(route.baselineSeconds)}분 · ${
                                    kilometers(
                                        route.baselineMeters
                                    )
                                } km / ${batch.context.settings.liters} L 주유 기준"
                                else "현재 위치 · 광운대학교 인근 / ${batch.context.settings.liters} L 주유 기준",
                                style = type.labelMedium, color = colors.textDarkSecondary,
                            )
                        }
                    }
                }
                if (batch != null) PleosTabs(batch.context.journey.availableModes.map {
                    PleosTab(
                        it,
                        it.label,
                        modeIcon(it)
                    )
                }, state.mode, onMode, enabled = !state.busy)
                state.error?.let {
                    Text(
                        it,
                        style = type.labelSmall,
                        color = colors.informativeNegative
                    )
                }
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
                                enabled = state.selected != null && !state.busy,
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

@Composable
private fun StationRow(
    row: EvaluatedStation,
    mode: JourneyMode,
    selected: Boolean,
    enabled: Boolean,
    narrow: Boolean,
    onClick: () -> Unit
) {
    val colors = JUYULOWTheme.colors
    val type = JUYULOWTheme.typography
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) colors.surfaceHigh else colors.surfaceBasic,
        border = BorderStroke(2.dp, if (selected) colors.basic900 else colors.basic200),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val price: @Composable () -> Unit =
            { StationValue("리터당 가격", "${won(row.station.priceWonPerL.toDouble())} 원/L") }
        val time: @Composable () -> Unit = {
            val seconds = when (mode) {
                JourneyMode.WAYPOINT -> row.comparison.costDurationSeconds
                JourneyMode.DESTINATION -> row.station.outboundSeconds
                JourneyMode.ROUND_TRIP -> row.comparison.totalSeconds
            }
            StationValue(
                if (mode == JourneyMode.WAYPOINT) "ETA 변화" else if (mode == JourneyMode.ROUND_TRIP) "왕복 소요시간" else "주유소까지",
                "${if (mode == JourneyMode.WAYPOINT && seconds >= 0) "+" else ""}${minutes(seconds)} 분"
            )
        }
        Column(
            Modifier
                .testTag(row.station.id)
                .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (narrow) {
                Text(row.station.name, style = type.bodyMediumStrong)
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { price(); time() }
            } else Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    row.station.name,
                    style = type.bodyMediumStrong,
                    modifier = Modifier.weight(1.35f)
                )
                Box(Modifier.weight(1f)) { price() }
                Box(Modifier.weight(1f)) { time() }
            }
        }
    }
}

@Composable
private fun StationValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = JUYULOWTheme.typography.labelSmall,
            color = JUYULOWTheme.colors.textDarkTertiary
        )
        Text(value, style = JUYULOWTheme.typography.bodyMediumStrong)
    }
}

@Composable
private fun SelectionPanel(
    row: EvaluatedStation,
    context: ComparisonContext,
    mode: JourneyMode,
    narrow: Boolean
) {
    val colors = JUYULOWTheme.colors
    val type = JUYULOWTheme.typography
    val result = row.comparison
    val scroll = rememberScrollState()
    LaunchedEffect(row.station.id) { scroll.scrollTo(0) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = if (narrow) 12.dp else 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(row.station.brand, style = type.labelSmall, color = colors.textDarkTertiary)
            Text(row.station.name, style = type.titleSmallBold)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "이동 비용까지 반영한 ${if (result.netSavingWon >= 0) "순절약액" else "추가 지출"}",
                style = type.labelSmall,
                color = colors.textDarkSecondary
            )
            Text(
                won(abs(result.netSavingWon)),
                style = type.headlineSmallExtraBold,
                color = if (result.netSavingWon >= 0) colors.informativePositive else colors.textDarkPrimary
            )
            Text(if (result.netSavingWon >= 0) "원 절약" else "원 더 들어요", style = type.bodySmall)
        }
        HorizontalDivider(color = colors.basic200)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PleosIcon(PleosIcon.Route)
            Text(
                if (mode == JourneyMode.WAYPOINT) "추가 거리" else "이동 거리",
                style = type.labelSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${if (mode == JourneyMode.WAYPOINT && result.costDistanceMeters >= 0) "+" else ""}${
                    kilometers(
                        result.costDistanceMeters
                    )
                } km", style = type.bodyMediumStrong
            )
        }
        HorizontalDivider(color = colors.basic200)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("현재 위치", style = type.labelSmall)
            PleosIcon(PleosIcon.Next, Modifier.size(24.dp))
            Text(row.station.name, style = type.labelMedium)
            if (mode != JourneyMode.DESTINATION) {
                PleosIcon(PleosIcon.Next, Modifier.size(24.dp))
                Text(
                    if (mode == JourneyMode.WAYPOINT) context.journey.destination.orEmpty() else "현재 위치로 복귀",
                    style = type.labelSmall
                )
            }
        }
        Text(
            "전체 ${minutes(result.totalSeconds)}분 · ${kilometers(result.distanceMeters)} km · 주유 ${context.settings.refuelMinutes}분 포함",
            style = type.labelSmall,
            color = colors.textDarkTertiary
        )
        Text(
            when (mode) {
                JourneyMode.WAYPOINT -> "${context.journey.destination} 목적지는 그대로 유지돼요."
                JourneyMode.DESTINATION -> if (context.journey.hasDestination) "${context.journey.destination} 대신 주유소로 목적지를 변경해요." else "선택한 주유소로 목적지를 설정해요."
                JourneyMode.ROUND_TRIP -> "주유소를 경유해 현재 위치로 돌아와요."
            }, style = type.labelSmall, color = colors.textDarkSecondary
        )
        HorizontalDivider(color = colors.basic200)
        CostRow("유가 차이 절감액", "+${won(result.fuelSavingWon)}원")
        CostRow(
            if (mode == JourneyMode.WAYPOINT) "추가 주행 연료비" else "이동 연료비",
            "−${won(result.drivingCostWon)}원"
        )
        CostRow("통행료", "−${won(result.tollWon)}원")
        CostRow(
            "시간 비용",
            if (context.settings.includeTimeCost) "−${won(result.timeCostWon)}원" else "제외"
        )
        Text(
            "${context.settings.liters} L 주유 · 주변 최고가 ${won(context.referencePriceWonPerL.toDouble())}원/L 대비",
            style = type.labelSmall,
            color = colors.textDarkTertiary
        )
    }
}

@Composable
private fun CostRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = JUYULOWTheme.typography.labelMedium,
            color = JUYULOWTheme.colors.textDarkSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = JUYULOWTheme.typography.labelMedium)
    }
}

internal fun previewSession(): ComparisonSession = ComparisonSession(
    "preview", ComparisonContext(JourneyContext(), RefuelSettings(), 1740), listOf(
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
