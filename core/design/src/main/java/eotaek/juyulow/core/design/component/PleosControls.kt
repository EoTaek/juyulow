package eotaek.juyulow.core.design.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eotaek.juyulow.core.design.theme.JUYULOWTheme
import eotaek.juyulow.core.design.theme.pleosShadow

@Composable
fun PleosButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: PleosIcon? = null,
    trailingIcon: PleosIcon? = null,
    outlined: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = JUYULOWTheme.colors
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 80.dp),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (outlined) colors.surfaceBasic else if (pressed) colors.basic700 else colors.basic900,
            contentColor = if (outlined) colors.textDarkPrimary else colors.basic00,
            disabledContainerColor = colors.surfaceAccent,
            disabledContentColor = colors.textDarkQuaternary,
        ),
        border = if (outlined) BorderStroke(1.dp, colors.basic200) else null,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        interactionSource = interactions,
    ) {
        if (loading) CircularProgressIndicator(
            Modifier.size(28.dp),
            color = colors.textDarkSecondary,
            strokeWidth = 3.dp
        )
        else if (icon != null) PleosIcon(icon)
        if (loading || icon != null) Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = JUYULOWTheme.typography.bodySmallStrong,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (trailingIcon != null && !loading) {
            Spacer(Modifier.width(12.dp))
            PleosIcon(trailingIcon)
        }
    }
}

@Composable
fun PleosIconButton(
    icon: PleosIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    FilledIconButton(
        onClick, modifier.size(72.dp), enabled,
        shape = RoundedCornerShape(16.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = JUYULOWTheme.colors.surfaceHigh,
            contentColor = JUYULOWTheme.colors.iconDarkPrimary,
        ),
    ) { PleosIcon(icon, contentDescription = label) }
}

data class PleosTab<T>(val value: T, val label: String, val icon: PleosIcon? = null)

@Composable
fun <T> PleosTabs(
    tabs: List<PleosTab<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val colors = JUYULOWTheme.colors
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceHigh)
            .padding(4.dp)
            .selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEach { tab ->
            val active = tab.value == selected
            Surface(
                color = if (active) colors.basic900 else colors.surfaceHigh,
                contentColor = if (active) colors.basic00 else colors.textDarkSecondary,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .then(
                        if (active) Modifier.pleosShadow(
                            RoundedCornerShape(14.dp),
                            JUYULOWTheme.shadows.level1
                        ) else Modifier
                    ),
            ) {
                Row(
                    Modifier
                        .testTag("tab-${tab.value}")
                        .selectable(
                            active,
                            enabled = enabled,
                            role = Role.Tab,
                            onClick = { onSelect(tab.value) })
                        .heightIn(min = 64.dp)
                        .padding(horizontal = if (compact) 12.dp else 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (tab.icon != null) PleosIcon(tab.icon, Modifier.size(28.dp))
                    Text(
                        tab.label,
                        style = if (compact) JUYULOWTheme.typography.labelSmall else JUYULOWTheme.typography.bodySmallStrong
                    )
                }
            }
        }
    }
}

@Composable
fun PleosSwitch(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = JUYULOWTheme.colors
    Row(
        modifier
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(12.dp))
            .toggleable(
                checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            Modifier
                .size(88.dp, 52.dp)
                .clip(CircleShape)
                .background(if (!enabled) colors.switchDisabled else if (checked) colors.switchOn else colors.switchOff)
                .padding(6.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(
                        if (checked) colors.switchKnobOn else colors.switchKnobOff,
                        CircleShape
                    )
            )
        }
        Text(label, style = JUYULOWTheme.typography.bodySmall)
    }
}

@Composable
fun PleosStepper(
    label: String,
    value: Int,
    unit: String,
    range: IntRange,
    step: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PleosIconButton(
            PleosIcon.Minus,
            "$label 줄이기",
            { onChange((value - step).coerceAtLeast(range.first)) },
            enabled = enabled && value > range.first
        )
        Text(
            "$value $unit",
            style = JUYULOWTheme.typography.titleSmallBold,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "$label $value $unit" })
        PleosIconButton(
            PleosIcon.Plus,
            "$label 늘리기",
            { onChange((value + step).coerceAtMost(range.last)) },
            enabled = enabled && value < range.last
        )
    }
}
