package eotaek.juyulow.feature.main.results.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eotaek.juyulow.core.design.component.PleosIcon
import eotaek.juyulow.core.design.theme.JUYULOWTheme
import eotaek.juyulow.core.model.ComparisonContext
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.feature.main.common.kilometers
import eotaek.juyulow.feature.main.common.minutes
import eotaek.juyulow.feature.main.common.won
import eotaek.juyulow.feature.main.results.EvaluatedStation
import kotlin.math.abs

@Composable
internal fun SelectionPanel(
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
