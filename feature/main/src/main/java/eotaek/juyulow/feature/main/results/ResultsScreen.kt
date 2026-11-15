package eotaek.juyulow.feature.main.results

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eotaek.juyulow.core.design.component.PleosButton
import eotaek.juyulow.core.design.component.PleosDividedText
import eotaek.juyulow.core.design.component.PleosIcon
import eotaek.juyulow.core.design.component.PleosIconButton
import eotaek.juyulow.core.design.component.PleosInlineError
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
import eotaek.juyulow.core.model.resolveRefuelConditions
import eotaek.juyulow.feature.main.common.actionLabel
import eotaek.juyulow.feature.main.results.component.SelectionPanel
import eotaek.juyulow.feature.main.results.component.StationRow
import kotlinx.coroutines.flow.first

@Composable
fun ResultsScreen(
    state: ResultsUiState, onBack: () -> Unit, onRefresh: () -> Unit, onSelect: (String) -> Unit,
    onSort: (StationSort) -> Unit, onConfirm: (JourneyMode) -> Unit,
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
                    Text(
                        "주변에 ${batch?.stations?.size ?: 0}개의 주유소가 있어요",
                        modifier = Modifier.weight(1f),
                        style = if (narrow) type.titleSmallBold else type.titleLargeBold
                    )
                }
                state.error?.let {
                    PleosInlineError(it)
                }
                if (state.routeUncertain) PleosButton(
                    "상태 다시 확인",
                    onReconcile,
                    enabled = !state.busy
                )
                if (state.comparisonExpired) Text(
                    "거리·시간·절약액은 이전 검색 기준이에요. 목적지 설정은 가능해요.",
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
                            if (batch.context.journey.stationSource == "오피넷") {
                                val fetchedAt = batch.stations.firstOrNull()?.observedAtMillis
                                    ?: batch.createdAtMillis
                            }
                            var focusedId by remember { mutableStateOf<String?>(null) }
                            val requesters =
                                remember(batch.id) { mutableMapOf<String, FocusRequester>() }
                            if (!batch.searchComplete) PleosDividedText(
                                "잠정 절약액 · 기준 유가 갱신 중",
                                style = type.labelSmall
                            )
                            val detourCount = batch.stations.count { it.detour != null }
                            if (detourCount > 0) Text(
                                "카카오 경유 " + batch.stations.count { it.detour?.pending == false } + "/" + detourCount,
                                style = type.labelSmall
                            )
                            val listState = rememberLazyListState()
                            val orderedIds = state.evaluated.map { it.station.id }
                            LaunchedEffect(orderedIds, focusedId) {
                                val id = focusedId ?: return@LaunchedEffect
                                snapshotFlow { listState.isScrollInProgress }.first { !it }
                                val index = orderedIds.indexOf(id)
                                if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.key == id }) {
                                    listState.scrollToItem(index)
                                    androidx.compose.runtime.withFrameNanos { }
                                    requesters[id]?.requestFocus()
                                }
                            }
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
                                    val requester =
                                        remember(row.station.id) { requesters.getOrPut(row.station.id) { FocusRequester() } }
                                    Box(
                                        Modifier.animateItem(
                                            fadeInSpec = null, fadeOutSpec = null,
                                            placementSpec = tween(
                                                250,
                                                easing = LinearOutSlowInEasing
                                            )
                                        )
                                    ) {
                                        StationRow(
                                            row,
                                            state.mode,
                                            row.station.id == state.selectedId,
                                            !state.busy,
                                            narrow,
                                            Modifier
                                                .focusRequester(requester)
                                                .onFocusChanged {
                                                    if (it.isFocused) focusedId = row.station.id
                                                }
                                        ) { onSelect(row.station.id) }
                                    }
                                }
                                items(
                                    batch.stations.filter { it.outboundMeters == null || it.outboundSeconds == null }
                                        .sortedWith(compareByDescending<Station> { it.routePending }.thenBy { it.sourceIndex }),
                                    key = { it.id }) { station ->
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .animateItem(
                                                fadeInSpec = null,
                                                fadeOutSpec = null,
                                                placementSpec = tween(
                                                    250,
                                                    easing = LinearOutSlowInEasing
                                                )
                                            )
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(station.name, style = type.bodySmall)
                                        Text(station.brand, style = type.labelSmall)
                                        Text("${station.priceWonPerL} 원/L", style = type.bodySmall)
                                        PleosDividedText(
                                            if (station.routePending) "ETA 확인 중" else station.routeError
                                                ?: "경로 확인 불가",
                                            style = type.labelSmall
                                        )
                                        PleosDividedText("거리·시간·절약액 미확보 · 내비 설정 불가", style = type.labelSmall)
                                    }
                                }
                                if (batch.stations.isEmpty()) item {
                                    Text(
                                        "주변 주유소가 없어요. 다시 찾아 주세요.",
                                        style = type.bodySmall
                                    )
                                }
                            }
                            val actions = listOf(JourneyMode.WAYPOINT, JourneyMode.DESTINATION)
                                .filter { it in batch.context.journey.availableModes }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                actions.forEach { mode ->
                                    PleosButton(
                                        mode.actionLabel,
                                        { onConfirm(mode) },
                                        Modifier
                                            .weight(1f)
                                            .onFocusChanged { if (it.isFocused) focusedId = null }
                                            .testTag(
                                                if (mode == JourneyMode.DESTINATION) "confirm-route" else "add-waypoint"
                                            ),
                                        icon = if (narrow && actions.size > 1) null else modeIcon(
                                            mode
                                        ),
                                        enabled = state.selected != null && !state.busy && !state.routeUncertain,
                                        loading = state.settingRoute && state.mode == mode,
                                    )
                                }
                            }
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
                                    narrow,
                                    state.comparisonExpired
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

// 각 상태를 인자 없는 진입점으로 제공해 IDE의 프리뷰 인자 주입에 의존하지 않는다.
@Preview(name = "Wide", widthDp = 1700, heightDp = 1184)
@Preview(name = "Narrow", widthDp = 840, heightDp = 1184)
@Preview(name = "Dark Wide", widthDp = 1700, heightDp = 1184, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Dark Narrow", widthDp = 840, heightDp = 1184, uiMode = Configuration.UI_MODE_NIGHT_YES)
private annotation class ResultsPreviewVariants

@ResultsPreviewVariants
@Composable
private fun ResultsNoDestinationPreview() {
    ResultsPreviewContent("목적지 없음")
}

@ResultsPreviewVariants
@Composable
private fun ResultsReplaceDestinationPreview() {
    ResultsPreviewContent("목적지 있음·목적지 대체")
}

@ResultsPreviewVariants
@Composable
private fun ResultsPreview() {
    ResultsPreviewContent("목적지 있음·경유 추정 미제공")
}

@Preview(name = "Results · Dark", widthDp = 1700, heightDp = 1184, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Results · Dark Narrow", widthDp = 840, heightDp = 1184, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ResultsDarkPreview() {
    ResultsPreviewContent("목적지 있음·경유 추정 미제공", darkTheme = true)
}

@ResultsPreviewVariants
@Composable
private fun ResultsDetourSuccessPreview() {
    ResultsPreviewContent("경유 추정 성공")
}

@ResultsPreviewVariants
@Composable
private fun ResultsDetourPendingPreview() {
    ResultsPreviewContent("경유 추정 조회 중")
}

@ResultsPreviewVariants
@Composable
private fun ResultsDetourFailedPreview() {
    ResultsPreviewContent("경유 추정 실패")
}

@ResultsPreviewVariants
@Composable
private fun ResultsExpiredPreview() {
    ResultsPreviewContent("이전 검색 기준")
}

@ResultsPreviewVariants
@Composable
private fun ResultsExtraCostPreview() {
    ResultsPreviewContent("추가 지출")
}

@ResultsPreviewVariants
@Composable
private fun ResultsMixedEtaPreview() {
    ResultsPreviewContent("ETA 성공·조회 중·실패 혼합")
}

@ResultsPreviewVariants
@Composable
private fun ResultsAllEtaFailedPreview() {
    ResultsPreviewContent("ETA 전체 실패")
}

@ResultsPreviewVariants
@Composable
private fun ResultsEmptyPreview() {
    ResultsPreviewContent("검색 결과 없음")
}

@ResultsPreviewVariants
@Composable
private fun ResultsRefreshingPreview() {
    ResultsPreviewContent("재검색 중")
}

@ResultsPreviewVariants
@Composable
private fun ResultsRefreshFailedPreview() {
    ResultsPreviewContent("재검색 실패·기존 결과 유지")
}

@ResultsPreviewVariants
@Composable
private fun ResultsSettingRoutePreview() {
    ResultsPreviewContent("경로 설정 중")
}

@ResultsPreviewVariants
@Composable
private fun ResultsRouteUncertainPreview() {
    ResultsPreviewContent("경로 설정 결과 불명")
}

@ResultsPreviewVariants
@Composable
private fun ResultsMissingSessionPreview() {
    ResultsPreviewContent("세션 없음")
}

@Composable
private fun ResultsPreviewContent(name: String, darkTheme: Boolean = isSystemInDarkTheme()) {
    JUYULOWTheme(darkTheme = darkTheme) {
        ResultsScreen(resultsPreviewCases().first { it.name == name }.state, {}, {}, {}, {}, {})
    }
}
