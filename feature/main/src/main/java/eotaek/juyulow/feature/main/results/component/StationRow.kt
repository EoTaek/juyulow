package eotaek.juyulow.feature.main.results.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eotaek.juyulow.core.design.component.PleosLabelValue
import eotaek.juyulow.core.design.component.PleosOutlinedCard
import eotaek.juyulow.core.design.theme.JUYULOWTheme
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.core.model.Station
import eotaek.juyulow.core.model.StationComparison
import eotaek.juyulow.feature.main.common.minutes
import eotaek.juyulow.feature.main.common.won

@Composable
internal fun StationRow(
    station: Station,
    comparison: StationComparison?,
    mode: JourneyMode,
    selected: Boolean,
    enabled: Boolean,
    narrow: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = JUYULOWTheme.colors
    val type = JUYULOWTheme.typography
    PleosOutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else .4f),
        cornerRadius = 18.dp,
        containerColor = if (selected) colors.surfaceHigh else colors.surfaceBasic,
        borderWidth = 2.dp,
        borderColor = if (selected) colors.basic900 else colors.basic200,
    ) {
        val price: @Composable () -> Unit =
            { PleosLabelValue("리터당 가격", "${won(station.priceWonPerL.toDouble())} 원/L") }
        val displayMode =
            if (station.observedAtMillis != null) JourneyMode.DESTINATION else mode
        val time: @Composable () -> Unit = {
            val seconds = when (displayMode) {
                JourneyMode.WAYPOINT -> comparison?.costDurationSeconds
                JourneyMode.DESTINATION -> station.outboundSeconds
                JourneyMode.ROUND_TRIP -> comparison?.totalSeconds
            }
            val value = if (comparison == null || seconds == null) {
                if (station.routePending || station.detour?.pending == true) "ETA 확인 중" else "경로 확인 불가"
            } else "${if (displayMode == JourneyMode.WAYPOINT && seconds >= 0) "+" else ""}${
                minutes(
                    seconds
                )
            } 분"
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (displayMode == JourneyMode.WAYPOINT) "ETA 변화" else if (displayMode == JourneyMode.ROUND_TRIP) "왕복 소요시간" else "주유소까지",
                    style = type.labelSmall,
                    color = colors.textDarkTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    value,
                    style = type.bodyMediumStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(
            modifier
                .testTag(station.id)
                .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (narrow) {
                Text(
                    station.name,
                    style = type.bodyMediumStrong,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) { price(); time() }
            } else Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    station.name,
                    style = type.bodyMediumStrong,
                    modifier = Modifier.weight(1.35f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(Modifier.weight(1f)) { price() }
                Box(Modifier.weight(1f)) { time() }
            }
        }
    }
}
