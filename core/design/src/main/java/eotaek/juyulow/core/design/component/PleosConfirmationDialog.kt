package eotaek.juyulow.core.design.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eotaek.juyulow.core.design.theme.JUYULOWTheme
import eotaek.juyulow.core.design.theme.pleosShadow

/** Medium System Popup: confirm action on the left, cancel action on the right. */
@Composable
fun PleosConfirmationDialog(
    title: String,
    description: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    cancelLabel: String = "취소",
) {
    Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(40.dp), contentAlignment = Alignment.Center) {
            ConfirmationContent(
                title,
                description,
                confirmLabel,
                onConfirm,
                onDismissRequest,
                modifier,
                cancelLabel
            )
        }
    }
}

@Composable
private fun ConfirmationContent(
    title: String,
    description: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    cancelLabel: String = "취소",
) {
    val colors = JUYULOWTheme.colors
    val type = JUYULOWTheme.typography
    val shape = RoundedCornerShape(48.dp)
    Surface(
        modifier
            .widthIn(max = 760.dp)
            .fillMaxWidth()
            .pleosShadow(shape, JUYULOWTheme.shadows.level3)
            .semantics { paneTitle = title },
        shape = shape,
        color = colors.backgroundPopup,
        contentColor = colors.textDarkPrimary,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Text(
                title, Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
                style = type.titleLargeBold, textAlign = TextAlign.Center
            )
            Text(
                description, Modifier.fillMaxWidth(), style = type.bodyLarge,
                color = colors.textDarkSecondary, textAlign = TextAlign.Center
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                PopupAction(confirmLabel, onConfirm, Modifier.weight(1f), primary = true)
                PopupAction(cancelLabel, onDismissRequest, Modifier.weight(1f), primary = false)
            }
        }
    }
}

@Composable
private fun PopupAction(label: String, onClick: () -> Unit, modifier: Modifier, primary: Boolean) {
    val colors = JUYULOWTheme.colors
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    Button(
        onClick, modifier.heightIn(min = 100.dp), shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) {
                if (pressed) colors.buttonFilledPressed else colors.buttonFilledEnabled
            } else if (pressed) colors.buttonBasicPressed else colors.buttonBasicEnabled,
            contentColor = if (primary) colors.textLightPrimary else colors.textDarkPrimary,
        ),
        interactionSource = interactions,
    ) {
        Text(label, style = JUYULOWTheme.typography.bodyLargeStrong, textAlign = TextAlign.Center)
    }
}

@Preview(name = "Landscape Light", widthDp = 1700, heightDp = 1184)
@Preview(name = "Split Light", widthDp = 840, heightDp = 1184)
@Composable
private fun ConfirmationLightPreview() {
    JUYULOWTheme(darkTheme = false) { ConfirmationPreview() }
}

@Preview(name = "Landscape Dark", widthDp = 1700, heightDp = 1184)
@Preview(name = "Split Dark", widthDp = 840, heightDp = 1184)
@Composable
private fun ConfirmationDarkPreview() {
    JUYULOWTheme(darkTheme = true) { ConfirmationPreview() }
}

@Composable
private fun ConfirmationPreview() {
    Surface(color = JUYULOWTheme.colors.backgroundPrimary) {
        Box(Modifier
            .fillMaxSize()
            .padding(40.dp), contentAlignment = Alignment.Center) {
            ConfirmationContent(
                "목적지를 변경할까요?",
                "서울숲 목적지와 기존 경유지를 제거하고 SK에너지 셀프 한강에너지주유소로 변경해요.",
                "목적지 변경",
                {},
                {})
        }
    }
}
