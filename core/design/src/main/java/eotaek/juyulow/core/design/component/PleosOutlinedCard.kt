package eotaek.juyulow.core.design.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eotaek.juyulow.core.design.theme.JUYULOWTheme

@Composable
fun PleosOutlinedCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    borderWidth: Dp = 1.dp,
    borderColor: Color = JUYULOWTheme.colors.basic200,
    containerColor: Color = JUYULOWTheme.colors.surfaceBasic,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        border = BorderStroke(borderWidth, borderColor),
        color = containerColor,
        content = content,
    )
}

@Preview
@Composable
private fun OutlinedCardPreview() {
    JUYULOWTheme {
        PleosOutlinedCard {
            PleosLabelValue("현재 목적지", "서울숲", Modifier.padding(24.dp))
        }
    }
}
