package eotaek.juyulow.feature.main.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import eotaek.juyulow.core.design.component.PleosInlineError
import eotaek.juyulow.core.design.component.PleosOutlinedCard
import eotaek.juyulow.core.design.component.vehicleNumber
import eotaek.juyulow.core.design.theme.JUYULOWTheme
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.feature.main.common.minutes

@Composable
fun HomeScreen(
    state: HomeUiState,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
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
                        PleosOutlinedCard {
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
                                    if (state.journey.hasDestination) Text(
                                        "현재 목적지",
                                        style = type.labelSmall,
                                        color = colors.textDarkTertiary
                                    )
                                    Text(
                                        state.journey.destination ?: "목적지 없음",
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
                                if (state.journey.hasDestination && state.journey.baselineSeconds != null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "도착까지",
                                            style = type.labelSmall,
                                            color = colors.textDarkTertiary,
                                            modifier = Modifier.alignByBaseline(),
                                        )
                                        Text(
                                            "${minutes(state.journey.baselineSeconds!!)}분",
                                            style = type.titleLargeBold,
                                            modifier = Modifier.alignByBaseline(),
                                        )
                                    }
                                }
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
                            enabled = state.settings != null && state.navigationReady,
                            emphasizeContentOnFocus = true,
                        )
                        state.conditions?.let { conditions ->
                            Text(
                                "반경 ${conditions.searchRadiusKm} km 내 검색",
                                style = type.labelSmall
                            )
                        }
                        state.navigationMessage?.let { Text(it, style = type.labelMedium) }
                        state.error?.let {
                            PleosInlineError(it, style = type.labelMedium)
                        }
                    }
                }
                HorizontalDivider(color = colors.basic200)
                Spacer(Modifier.height(24.dp))
                val value = state.conditions
                PleosButton(
                    onClick = onSettings, modifier = Modifier.testTag("open-settings"),
                    trailingIcon = PleosIcon.Settings, outlined = true,
                    enabled = value != null && !state.searching,
                ) {
                    if (value == null) Text("주유 조건을 불러오는 중") else Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(value.comparisonFuel.label, style = type.bodySmallStrong)
                        androidx.compose.material3.VerticalDivider(Modifier.height(24.dp), thickness = 1.dp)
                        Text("주유량 "+vehicleNumber(value.liters)+"L", style = type.bodySmallStrong)
                        androidx.compose.material3.VerticalDivider(Modifier.height(24.dp), thickness = 1.dp)
                        Text("평균연비 "+vehicleNumber(value.efficiencyKmPerL)+"km/L", style = type.bodySmallStrong)
                    }
                }
            }
        }
    }
}

@Preview(name = "Home · Landscape", widthDp = 1700, heightDp = 1184)
@Preview(name = "Home · Portrait", widthDp = 840, heightDp = 1184)
@Composable
private fun HomePreview() {
    JUYULOWTheme { HomeScreen(HomeUiState(settings = RefuelSettings()), {}, {}) }
}

@Preview(name = "Home · Dark Landscape", widthDp = 1700, heightDp = 1184)
@Preview(name = "Home · Dark Portrait", widthDp = 840, heightDp = 1184)
@Composable
private fun HomeDarkPreview() {
    JUYULOWTheme(darkTheme = true) {
        HomeScreen(
            HomeUiState(settings = RefuelSettings()),
            {},
            {})
    }
}
