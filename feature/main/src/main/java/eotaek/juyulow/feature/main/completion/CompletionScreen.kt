package eotaek.juyulow.feature.main.completion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eotaek.juyulow.core.design.component.PleosButton
import eotaek.juyulow.core.design.component.PleosDividedText
import eotaek.juyulow.core.design.component.PleosIcon
import eotaek.juyulow.core.design.component.PleosLabelValue
import eotaek.juyulow.core.design.component.PleosOutlinedCard
import eotaek.juyulow.core.design.theme.JUYULOWTheme
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.core.model.NavigationSelection
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.core.model.resolveRefuelConditions
import eotaek.juyulow.feature.main.common.kilometers
import eotaek.juyulow.feature.main.common.minutes

@Composable
fun CompletionScreen(
    selection: NavigationSelection?,
    onCompare: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenNavigation: (() -> Unit)? = null,
    error: String? = null,
) {
    val colors = JUYULOWTheme.colors
    val type = JUYULOWTheme.typography
    Surface(modifier.fillMaxSize(), color = colors.backgroundPrimary) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val narrow = maxWidth < 1100.dp
            Column(
                Modifier
                    .widthIn(max = 1400.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(if (narrow) 28.dp else 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.basic900,
                    contentColor = colors.basic00
                ) {
                    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                        PleosIcon(
                            PleosIcon.Check,
                            Modifier.size(56.dp)
                        )
                    }
                }
                if (selection == null) {
                    Text("설정한 경로가 없어요.", style = type.titleLargeBold)
                    PleosButton("홈으로", onHome)
                } else {
                    Text(
                        when (selection.mode) {
                            JourneyMode.WAYPOINT -> "경유지를 추가했어요."
                            JourneyMode.DESTINATION -> "목적지를 설정했어요."
                            JourneyMode.ROUND_TRIP -> "왕복 경로를 준비했어요."
                        }, style = if (narrow) type.titleLargeBold else type.headlineSmall
                    )
                    Text(
                        when (selection.mode) {
                            JourneyMode.WAYPOINT -> "${selection.stationName}에 들러 ${selection.destination}으로 이동해요."
                            JourneyMode.DESTINATION -> "${selection.stationName}로 이동해요."
                            JourneyMode.ROUND_TRIP -> "${selection.stationName}에 들른 뒤 현재 위치로 돌아와요."
                        }, style = type.bodyMedium, color = colors.textDarkTertiary
                    )
                    PleosOutlinedCard(cornerRadius = 24.dp) {
                        FlowRow(
                            Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            PleosLabelValue(
                                if (selection.waypoint != null) "경유지" else "목적지",
                                if (selection.routeWaypoints.isNotEmpty()) selection.routeWaypoints.joinToString(
                                    " → "
                                ) else selection.stationName,
                                valueStyle = type.titleSmallBold
                            )
                            if (selection.waypoint != null) {
                                PleosIcon(PleosIcon.Next, Modifier.padding(top = 24.dp))
                                PleosLabelValue(
                                    "최종 목적지",
                                    selection.destination,
                                    valueStyle = type.titleSmallBold
                                )
                            }
                            PleosLabelValue(
                                "${if (selection.outboundOnly) "검색 시점 기준 · 주유소까지 " else ""}${
                                    kilometers(
                                        selection.distanceMeters
                                    )
                                } km · 주유 포함",
                                "${minutes(selection.durationSeconds)}분",
                                valueStyle = type.titleSmallBold
                            )
                        }
                    }
                    error?.let { PleosDividedText(it, style = type.labelSmall) }
                    onOpenNavigation?.let { PleosButton("내비게이션 열기", it) }
                    if (narrow) Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PleosButton(
                            "다른 주유소 비교",
                            onCompare,
                            Modifier.fillMaxWidth(),
                            outlined = true
                        )
                        PleosButton(
                            "홈으로",
                            onHome,
                            Modifier.fillMaxWidth(),
                            trailingIcon = PleosIcon.Next
                        )
                    } else Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        PleosButton("다른 주유소 비교", onCompare, outlined = true)
                        PleosButton("홈으로", onHome, trailingIcon = PleosIcon.Next)
                    }
                }
            }
        }
    }
}

private val previewSelection = NavigationSelection(
    "preview",
    "station-0",
    "한길 셀프주유소",
    JourneyMode.WAYPOINT,
    "서울숲",
    "한길 셀프주유소",
    30200,
    3060,
    300,
    resolveRefuelConditions(RefuelSettings()),
)

@Preview(name = "Completion · Landscape", widthDp = 1700, heightDp = 1184)
@Preview(name = "Completion · Portrait", widthDp = 840, heightDp = 1184)
@Composable
private fun CompletionPreview() {
    JUYULOWTheme { CompletionScreen(previewSelection, {}, {}) }
}

@Preview(name = "Completion · Dark Landscape", widthDp = 1700, heightDp = 1184)
@Preview(name = "Completion · Dark Portrait", widthDp = 840, heightDp = 1184)
@Composable
private fun CompletionDarkPreview() {
    JUYULOWTheme(darkTheme = true) { CompletionScreen(previewSelection, {}, {}) }
}
