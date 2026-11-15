package eotaek.juyulow.feature.main.results.component

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eotaek.juyulow.core.design.component.PleosIcon
import eotaek.juyulow.core.design.component.vehicleNumber
import eotaek.juyulow.core.design.theme.JUYULOWTheme
import eotaek.juyulow.core.model.ComparisonContext
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.feature.main.common.kilometers
import eotaek.juyulow.feature.main.common.minutes
import eotaek.juyulow.feature.main.common.won
import eotaek.juyulow.feature.main.results.EvaluatedStation
import eotaek.juyulow.feature.main.results.selectionPreviewCases
import kotlin.math.abs

@Composable
internal fun SelectionPanel(
    row: EvaluatedStation,
    context: ComparisonContext,
    mode: JourneyMode,
    narrow: Boolean,
    detourExpired: Boolean = false,
) {
    val colors = JUYULOWTheme.colors
    val type = JUYULOWTheme.typography
    fun signedMinutes(seconds: Int) = (if (seconds < 0) "−" else "+") + minutes(abs(seconds))
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
                "${if (context.journey.destinationOnly) "주유소까지 이동 비용 반영" else "이동 비용까지 반영한"} ${if (result.netSavingWon >= 0) "순절약액" else "추가 지출"}",
                style = type.labelSmall,
                color = colors.textDarkSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    won(abs(result.netSavingWon)),
                    style = type.headlineSmallExtraBold,
                    color = if (result.netSavingWon >= 0) colors.informativePositive else colors.textDarkPrimary,
                    modifier = Modifier.alignByBaseline(),
                    maxLines = 1
                )
                Text(
                    if (result.netSavingWon >= 0) "원 절약" else "원 더 들어요",
                    style = type.bodySmall,
                    modifier = Modifier.alignByBaseline(),
                    maxLines = 1
                )
            }
        }
        HorizontalDivider(color = colors.basic200)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PleosIcon(PleosIcon.Route)
            Text(
                if (context.journey.destinationOnly) "주유소까지 거리" else if (mode == JourneyMode.WAYPOINT) "추가 거리" else "이동 거리",
                style = type.labelSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${if (mode == JourneyMode.WAYPOINT && !context.journey.destinationOnly && result.costDistanceMeters >= 0) "+" else ""}${
                    kilometers(
                        result.costDistanceMeters
                    )
                } km", style = type.bodyMediumStrong
            )
        }
        HorizontalDivider(color = colors.basic200)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("현재 위치", style = type.labelSmall, modifier = Modifier.align(Alignment.CenterVertically))
            PleosIcon(PleosIcon.Next, Modifier.size(24.dp).align(Alignment.CenterVertically))
            Text(row.station.name, style = type.labelSmall, modifier = Modifier.align(Alignment.CenterVertically))
            if (mode != JourneyMode.DESTINATION) {
                PleosIcon(PleosIcon.Next, Modifier.size(24.dp).align(Alignment.CenterVertically))
                Text(
                    if (mode == JourneyMode.WAYPOINT) listOfNotNull(
                        context.journey.waypoint,
                        context.journey.destination
                    ).joinToString(" → ") else "현재 위치로 복귀",
                    style = type.labelSmall,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
        Text(
            "${if (context.journey.destinationOnly) "주유소까지" else "전체"} ${minutes(result.totalSeconds)}분 · ${
                kilometers(
                    result.distanceMeters
                )
            } km",
            style = type.labelSmall,
            color = colors.textDarkTertiary
        )
        row.station.detour?.let { estimate ->
            HorizontalDivider(color = colors.basic200)
            Text("예상 경유 추가 비용", style = type.labelMedium)
            when {
                detourExpired -> Text("이전 검색 기준 · 재검색 필요", style = type.bodySmall)
                estimate.pending -> Text("경유 비용 확인 중", style = type.bodySmall)
                estimate.error != null -> Text(estimate.error.orEmpty(), style = type.bodySmall)
                else -> {
                    val meters = requireNotNull(estimate.meters)
                    val seconds = requireNotNull(estimate.seconds)
                    val toll = requireNotNull(estimate.tollWon)
                    val fuel =
                        meters / 1000.0 / context.conditions.efficiencyKmPerL * context.referencePriceWonPerL
                    CostRow("추가 거리", kilometers(meters) + " km")
                    CostRow("추가 주행 시간", signedMinutes(seconds) + " 분")
                    CostRow(
                        "주유 포함 추가 시간",
                        signedMinutes(seconds + context.conditions.refuelSeconds) + " 분"
                    )
                    CostRow("경유 순절약액", won(result.fuelSavingWon - fuel - toll) + " 원")
                }
            }
        }
        HorizontalDivider(color = colors.basic200)
        CostRow("유가 차이 절감액", "+${won(result.fuelSavingWon)}원")
        CostRow(
            if (mode == JourneyMode.WAYPOINT && !context.journey.destinationOnly) "추가 주행 연료비" else "이동 연료비",
            "−${won(result.drivingCostWon)}원"
        )
        Text(
            "${vehicleNumber(context.conditions.liters)} L 주유 · 주변 최고가 ${won(context.referencePriceWonPerL.toDouble())}원/L 대비",
            style = type.labelSmall,
            color = colors.textDarkTertiary
        )
    }
}

// 각 상태를 인자 없는 진입점으로 제공해 IDE의 프리뷰 인자 주입에 의존하지 않는다.
@Preview(name = "Wide", widthDp = 798, heightDp = 960)
@Preview(name = "Narrow", widthDp = 390, heightDp = 960)
@Preview(name = "Dark Wide", widthDp = 798, heightDp = 960, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Dark Narrow", widthDp = 390, heightDp = 960, uiMode = Configuration.UI_MODE_NIGHT_YES)
private annotation class SelectionPreviewVariants

@SelectionPreviewVariants
@Composable
private fun SelectionNoDestinationPreview() {
    SelectionPreviewContent("목적지 없음")
}

@SelectionPreviewVariants
@Composable
private fun SelectionReplaceDestinationPreview() {
    SelectionPreviewContent("목적지 있음·목적지 대체")
}

@SelectionPreviewVariants
@Composable
private fun SelectionPanelPreview() {
    SelectionPreviewContent("목적지 있음·경유 추정 미제공")
}

// 이전 프리뷰 선택을 유지한 IDE에서도 같은 진입점을 찾을 수 있도록 이름을 유지한다.
@Preview(name = "Selection · Dark", widthDp = 798, heightDp = 960, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Selection · Dark Narrow", widthDp = 390, heightDp = 960, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SelectionPanelDarkPreview() {
    SelectionPreviewContent("목적지 있음·경유 추정 미제공", darkTheme = true)
}

@SelectionPreviewVariants
@Composable
private fun SelectionDetourSuccessPreview() {
    SelectionPreviewContent("경유 추정 성공")
}

@SelectionPreviewVariants
@Composable
private fun SelectionDetourPendingPreview() {
    SelectionPreviewContent("경유 추정 조회 중")
}

@SelectionPreviewVariants
@Composable
private fun SelectionDetourFailedPreview() {
    SelectionPreviewContent("경유 추정 실패")
}

@SelectionPreviewVariants
@Composable
private fun SelectionExpiredPreview() {
    SelectionPreviewContent("이전 검색 기준")
}

@SelectionPreviewVariants
@Composable
private fun SelectionExtraCostPreview() {
    SelectionPreviewContent("추가 지출")
}

@Composable
private fun SelectionPreviewContent(name: String, darkTheme: Boolean = isSystemInDarkTheme()) {
    val state = selectionPreviewCases().first { it.name == name }.state
    JUYULOWTheme(darkTheme = darkTheme) {
        Surface(color = JUYULOWTheme.colors.backgroundPrimary) {
            BoxWithConstraints {
                SelectionPanel(
                    row = requireNotNull(state.selected),
                    context = requireNotNull(state.session).context,
                    mode = state.mode,
                    narrow = maxWidth < 500.dp,
                    detourExpired = state.comparisonExpired,
                )
            }
        }
    }
}
