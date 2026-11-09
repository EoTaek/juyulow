package eotaek.juyulow.feature.main.results.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eotaek.juyulow.core.design.theme.JUYULOWTheme

@Composable
internal fun CostRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = JUYULOWTheme.typography.labelMedium,
            color = JUYULOWTheme.colors.textDarkSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = JUYULOWTheme.typography.labelMedium)
    }
}
