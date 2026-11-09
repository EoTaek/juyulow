package eotaek.juyulow.core.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** Both layers belong to the same shadow level and must be drawn together. */
@Immutable
data class PleosShadow(
    val primary: Shadow,
    val secondary: Shadow? = null,
)

fun Modifier.pleosShadow(shape: Shape, shadow: PleosShadow): Modifier {
    val first = dropShadow(shape, shadow.primary)
    return shadow.secondary?.let { first.dropShadow(shape, it) } ?: first
}

/** Pleos Foundations/Shadow values, shared by light and dark themes. */
object PleosShadows {
    val level1 = PleosShadow(
        primary = Shadow(
            radius = 24.dp,
            spread = 0.dp,
            offset = DpOffset(0.dp, 6.dp),
            color = Color(0x14000000),
        ),
    )

    val level2 = PleosShadow(
        primary = Shadow(
            radius = 32.dp,
            spread = 0.dp,
            offset = DpOffset(0.dp, 8.dp),
            color = Color(0x29000000),
        ),
        secondary = Shadow(
            radius = 2.dp,
            spread = 0.dp,
            offset = DpOffset(0.dp, 2.dp),
            color = Color(0x14000000),
        ),
    )

    val level3 = PleosShadow(
        primary = Shadow(
            radius = 40.dp,
            spread = 0.dp,
            offset = DpOffset(0.dp, 20.dp),
            color = Color(0x3D000000),
        ),
        secondary = Shadow(
            radius = 4.dp,
            spread = 0.dp,
            offset = DpOffset(0.dp, 2.dp),
            color = Color(0x33000000),
        ),
    )
}
