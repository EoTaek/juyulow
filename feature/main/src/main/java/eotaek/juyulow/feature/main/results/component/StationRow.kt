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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eotaek.juyulow.core.design.component.PleosLabelValue
import eotaek.juyulow.core.design.component.PleosOutlinedCard
import eotaek.juyulow.core.design.theme.JUYULOWTheme
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.feature.main.common.minutes
import eotaek.juyulow.feature.main.common.won
import eotaek.juyulow.feature.main.results.EvaluatedStation

@Composable
internal fun StationRow(
    row: EvaluatedStation,
    mode: JourneyMode,
    selected: Boolean,
    enabled: Boolean,
    narrow: Boolean,
    onClick: () -> Unit
) {
    val colors = JUYULOWTheme.colors
    val type = JUYULOWTheme.typography
    PleosOutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        containerColor = if (selected) colors.surfaceHigh else colors.surfaceBasic,
        borderWidth = 2.dp,
        borderColor = if (selected) colors.basic900 else colors.basic200,
    ) {
        val price: @Composable () -> Unit =
            { PleosLabelValue("리터당 가격", "${won(row.station.priceWonPerL.toDouble())} 원/L") }
        val displayMode =
            if (row.station.observedAtMillis != null) JourneyMode.DESTINATION else mode
        val time: @Composable () -> Unit = {
            val seconds = when (displayMode) {
                JourneyMode.WAYPOINT -> row.comparison.costDurationSeconds
                JourneyMode.DESTINATION -> requireNotNull(row.station.outboundSeconds)
                JourneyMode.ROUND_TRIP -> row.comparison.totalSeconds
            }
            PleosLabelValue(
                if (displayMode == JourneyMode.WAYPOINT) "ETA 변화" else if (displayMode == JourneyMode.ROUND_TRIP) "왕복 소요시간" else "주유소까지",
                "${if (displayMode == JourneyMode.WAYPOINT && seconds >= 0) "+" else ""}${
                    minutes(
                        seconds
                    )
                } 분"
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
