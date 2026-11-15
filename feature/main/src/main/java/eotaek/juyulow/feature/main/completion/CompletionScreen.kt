package eotaek.juyulow.feature.main.completion

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import eotaek.juyulow.core.design.theme.JUYULOWTheme
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.core.model.NavigationSelection
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.core.model.resolveRefuelConditions

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

@Preview(name = "Wide", widthDp = 1700, heightDp = 1184)
@Preview(name = "Narrow", widthDp = 840, heightDp = 1184)
@Preview(name = "Dark Wide", widthDp = 1700, heightDp = 1184, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Dark Narrow", widthDp = 840, heightDp = 1184, uiMode = Configuration.UI_MODE_NIGHT_YES)
private annotation class CompletionPreviewVariants

@CompletionPreviewVariants
@Composable
private fun CompletionPreview() {
    CompletionPreviewContent()
}

// 기존 프리뷰 선택이 참조하는 함수 이름을 유지한다.
@Preview(name = "Completion · Dark Landscape", widthDp = 1700, heightDp = 1184)
@Preview(name = "Completion · Dark Portrait", widthDp = 840, heightDp = 1184)
@Composable
private fun CompletionDarkPreview() {
    CompletionPreviewContent(darkTheme = true)
}

@CompletionPreviewVariants
@Composable
private fun CompletionDestinationPreview() {
    CompletionPreviewContent(previewSelection.copy(
        mode = JourneyMode.DESTINATION,
        destination = previewSelection.stationName,
        waypoint = null,
        distanceMeters = 1800,
        durationSeconds = 600,
    ))
}

@CompletionPreviewVariants
@Composable
private fun CompletionOutboundOnlyPreview() {
    CompletionPreviewContent(previewSelection.copy(
        outboundOnly = true, distanceMeters = 1800, durationSeconds = 600,
    ))
}

@CompletionPreviewVariants
@Composable
private fun CompletionMultipleWaypointsPreview() {
    CompletionPreviewContent(previewSelection.copy(
        routeWaypoints = listOf(previewSelection.stationName, "서울역"),
    ))
}

@CompletionPreviewVariants
@Composable
private fun CompletionMissingSelectionPreview() {
    CompletionPreviewContent(selection = null)
}

@CompletionPreviewVariants
@Composable
private fun CompletionWithoutNavigationActionPreview() {
    CompletionPreviewContent(showNavigationAction = false)
}

@CompletionPreviewVariants
@Composable
private fun CompletionNavigationErrorPreview() {
    CompletionPreviewContent(error = "내비게이션을 열지 못했어요. 잠시 후 다시 시도해 주세요.")
}

@CompletionPreviewVariants
@Composable
private fun CompletionLongNamesPreview() {
    val stationName = "서울 만남의광장 고속도로 휴게소 셀프주유소"
    CompletionPreviewContent(previewSelection.copy(
        stationName = stationName,
        waypoint = stationName,
        destination = "현대자동차 양재 본사 서관 방문객 주차장",
        routeWaypoints = listOf(stationName),
    ))
}

@Composable
private fun CompletionPreviewContent(
    selection: NavigationSelection? = previewSelection,
    showNavigationAction: Boolean = true,
    error: String? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    JUYULOWTheme(darkTheme = darkTheme) {
        CompletionScreen(
            selection = selection,
            onCompare = {},
            onHome = {},
            onOpenNavigation = if (showNavigationAction) ({}) else null,
            error = error,
        )
    }
}
