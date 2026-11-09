package eotaek.juyulow.core.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eotaek.juyulow.core.design.theme.JUYULOWTheme

@Composable
fun PleosLabelValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = JUYULOWTheme.typography.bodyMediumStrong,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = JUYULOWTheme.typography.labelSmall,
            color = JUYULOWTheme.colors.textDarkTertiary,
        )
        Text(value, style = valueStyle)
    }
}

@Preview
@Composable
private fun LabelValuePreview() {
    JUYULOWTheme { PleosLabelValue("리터당 가격", "1,635 원/L") }
}
