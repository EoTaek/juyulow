package eotaek.juyulow.core.design.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Original project-authored outline artwork. These are not official Pleos assets. */
enum class PleosIcon(private val drawing: String, private val directional: Boolean = false) {
    Search("M21 21L16 16 M18 10A8 8 0 1 1 2 10A8 8 0 1 1 18 10"),
    Back("M20 12H4 M11 5L4 12L11 19", true),
    Next("M4 12H20 M13 5L20 12L13 19", true),
    Pin("M12 22C9 18 4 14 4 9A8 8 0 0 1 20 9C20 14 15 18 12 22 M15 9A3 3 0 1 1 9 9A3 3 0 1 1 15 9"),
    Flag("M5 22V3 M5 3H19L16 7L19 11H5"),
    Settings("M4 6H20 M4 12H20 M4 18H20 M8 3V9 M16 9V15 M10 15V21"),
    Close("M5 5L19 19 M5 19L19 5"),
    Plus("M12 4V20 M4 12H20"),
    Minus("M4 12H20"),
    Waypoint("M4 3V10Q4 14 8 14H20 M14 8L20 14L14 20", true),
    Refresh("M20 7A9 9 0 1 0 21 15 M20 2V8H14"),
    Navigation("M3 10L21 3L14 21L11 13Z"),
    Check("M4 12L10 18L21 6"),
    Fuel("M4 21V4H14V21 M2 21H16 M4 10H14 M14 12H17V17Q17 20 20 20Q22 20 22 17V8L18 4 M19 5V9H22"),
    Route("M6 5H16Q21 5 21 10Q21 15 16 15H8Q3 15 3 20 M6 5A2 2 0 1 1 2 5A2 2 0 1 1 6 5 M5 20A2 2 0 1 1 1 20A2 2 0 1 1 5 20");

    val vector: ImageVector by lazy {
        ImageVector.Builder(name, 48.dp, 48.dp, 24f, 24f, autoMirror = directional).addPath(
            pathData = PathParser().parsePathString(drawing).toNodes(),
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ).build()
    }
}

@Composable
fun PleosIcon(icon: PleosIcon, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Icon(icon.vector, contentDescription, modifier.size(32.dp))
}
