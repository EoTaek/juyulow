package eotaek.juyulow.feature.main.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eotaek.juyulow.core.design.component.PleosButton
import eotaek.juyulow.core.design.component.PleosIcon
import eotaek.juyulow.core.design.component.PleosSwitch
import eotaek.juyulow.core.design.theme.JUYULOWTheme
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.feature.main.common.minutes

@Composable
fun HomeScreen(
    state: HomeUiState,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onDestinationChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = JUYULOWTheme.colors
    val type = JUYULOWTheme.typography
    Surface(modifier.fillMaxSize(), color = colors.backgroundPrimary) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val narrow = maxWidth < 1000.dp
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(if (narrow) 28.dp else 40.dp)
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column(
                        Modifier
                            .widthIn(max = 680.dp)
                            .fillMaxWidth()
                            .padding(start = if (narrow) 0.dp else 24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Surface(
                            color = colors.surfaceBasic,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.basic200)
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                PleosIcon(
                                    if (state.journey.hasDestination) PleosIcon.Flag else PleosIcon.Pin,
                                    Modifier.size(48.dp)
                                )
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        if (state.journey.hasDestination) "현재 목적지" else "현재 위치",
                                        style = type.labelSmall,
                                        color = colors.textDarkTertiary
                                    )
                                    Text(
                                        state.journey.destination ?: "광운대학교 인근",
                                        style = type.titleSmallBold
                                    )
                                    state.journey.waypoint?.let {
                                        Text(
                                            "$it 경유",
                                            style = type.labelSmall,
                                            color = colors.textDarkTertiary
                                        )
                                    }
                                }
                                if (state.journey.hasDestination) Text(
                                    "${minutes(state.journey.baselineSeconds)}분",
                                    style = type.titleLargeBold
                                )
                            }
                        }
                        PleosButton(
                            if (state.searching) "주변 주유소를 찾고 있어요" else "주변 주유소 찾기",
                            onSearch,
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 96.dp)
                                .testTag("search-stations"),
                            icon = PleosIcon.Search,
                            trailingIcon = PleosIcon.Next,
                            loading = state.searching,
                            enabled = state.settings != null,
                        )
                        state.error?.let {
                            Text(
                                it,
                                style = type.labelMedium,
                                color = colors.informativeNegative
                            )
                        }
                    }
                }
                HorizontalDivider(color = colors.basic200)
                Spacer(Modifier.height(24.dp))
                val destination: @Composable () -> Unit = {
                    PleosSwitch(
                        state.journey.hasDestination,
                        "유효 목적지 ${if (state.journey.hasDestination) "있음" else "없음"}",
                        onDestinationChange,
                        enabled = !state.searching
                    )
                }
                val settings: @Composable () -> Unit = {
                    val value = state.settings
                    PleosButton(
                        if (value == null) "주유 조건을 불러오는 중" else "${value.liters} L 주유 · 연비 ${value.efficiencyKmPerL} km/L · 주유 ${value.refuelMinutes}분",
                        onSettings,
                        Modifier.testTag("open-settings"),
                        trailingIcon = PleosIcon.Settings,
                        outlined = true,
                        enabled = value != null && !state.searching,
                    )
                }
                if (narrow) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { destination(); settings() }
                else Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) { destination(); settings() }
            }
        }
    }
}

@Preview(name = "Home · Landscape", widthDp = 1700, heightDp = 1184)
@Preview(name = "Home · Portrait", widthDp = 840, heightDp = 1184)
@Composable
private fun HomePreview() {
    JUYULOWTheme { HomeScreen(HomeUiState(settings = RefuelSettings()), {}, {}, {}) }
}

@Preview(name = "Home · Dark Landscape", widthDp = 1700, heightDp = 1184)
@Preview(name = "Home · Dark Portrait", widthDp = 840, heightDp = 1184)
@Composable
private fun HomeDarkPreview() {
    JUYULOWTheme(darkTheme = true) {
        HomeScreen(
            HomeUiState(settings = RefuelSettings()),
            {},
            {},
            {})
    }
}
