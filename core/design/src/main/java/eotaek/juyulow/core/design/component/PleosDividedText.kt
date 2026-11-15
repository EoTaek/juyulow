package eotaek.juyulow.core.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import eotaek.juyulow.core.design.theme.JUYULOWTheme

/** 독립 정보를 세로 구분선으로 나란히 표시한다. 좁은 창에서는 항목 단위로 줄바꿈한다. */
@Composable
fun PleosDividedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = JUYULOWTheme.typography.labelSmall,
    color: Color = Color.Unspecified,
) {
    val parts = text.split(" · ").map(String::trim).filter(String::isNotEmpty)
    if (parts.size <= 1) {
        Text(text, modifier = modifier, style = style, color = color)
    } else {
        FlowRow(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            parts.forEachIndexed { index, part ->
                Row(
                    Modifier.height(IntrinsicSize.Min).align(Alignment.CenterVertically),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (index > 0) VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        color = JUYULOWTheme.colors.basic200,
                    )
                    Text(part, style = style, color = color)
                }
            }
        }
    }
}
