package eotaek.juyulow.feature.comparison

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eotaek.juyulow.core.design.theme.JUYULOWTheme

@Composable
fun CounterScreen(
    uiState: CounterUiState,
    onAction: (CounterAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = stringResource(R.string.counter_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = uiState.count.toString(),
                style = MaterialTheme.typography.displayLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = { onAction(CounterAction.Decrement) },
                    enabled = uiState.count > 0,
                ) {
                    Text(stringResource(R.string.counter_decrement))
                }
                Button(
                    onClick = { onAction(CounterAction.Increment) },
                    enabled = uiState.count < Int.MAX_VALUE,
                ) {
                    Text(stringResource(R.string.counter_increment))
                }
            }
            TextButton(onClick = { onAction(CounterAction.Reset) }) {
                Text(stringResource(R.string.counter_reset))
            }
        }
    }
}

@Preview(
    name = "Pleos Connect-S-17 Landscape",
    widthDp = 1700,
    heightDp = 1184,
    showBackground = true,
)
@Preview(
    name = "Pleos Connect-S-17 Portrait",
    widthDp = 840,
    heightDp = 1184,
    showBackground = true,
)
@Composable
private fun CounterScreenPreview() {
    JUYULOWTheme {
        CounterScreen(uiState = CounterUiState(), onAction = {})
    }
}
