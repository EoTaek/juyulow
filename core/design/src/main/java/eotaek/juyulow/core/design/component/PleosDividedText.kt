package eotaek.juyulow.core.design.component

import androidx.compose.foundation.layout.Arrangement
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

/** 한 Row 안에서 텍스트 사이에 세로 구분선을 배치한다. 각 텍스트는 할당된 폭 안에서 줄바꿈한다. */
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
        Row(
            modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            parts.forEachIndexed { index, part ->
                if (index > 0) VerticalDivider(
                    modifier = Modifier.fillMaxHeight(),
                    color = JUYULOWTheme.colors.basic200,
                )
                Text(part, modifier = Modifier.weight(1f, fill = false), style = style, color = color)
            }
        }
    }
}
