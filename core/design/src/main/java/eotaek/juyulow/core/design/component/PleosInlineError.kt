package eotaek.juyulow.core.design.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import eotaek.juyulow.core.design.theme.JUYULOWTheme

@Composable
fun PleosInlineError(
    message: String,
    modifier: Modifier = Modifier,
    style: TextStyle = JUYULOWTheme.typography.labelSmall,
) {
    PleosDividedText(
        message,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = style,
        color = JUYULOWTheme.colors.informativeNegative,
    )
}

@Preview
@Composable
private fun InlineErrorPreview() {
    JUYULOWTheme { PleosInlineError("주유소를 찾지 못했어요. 다시 시도해 주세요.") }
}
