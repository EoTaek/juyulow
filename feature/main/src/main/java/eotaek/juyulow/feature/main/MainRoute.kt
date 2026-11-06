package eotaek.juyulow.feature.main

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import eotaek.juyulow.core.design.theme.JUYULOWTheme

@Composable
fun MainRoute(
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {}
}

@Preview(name = "Pleos Connect-S-17 Landscape", widthDp = 1700, heightDp = 1184)
@Preview(name = "Pleos Connect-S-17 Portrait", widthDp = 840, heightDp = 1184)
@Composable
private fun MainRoutePreview() {
    JUYULOWTheme {
        MainRoute()
    }
}
