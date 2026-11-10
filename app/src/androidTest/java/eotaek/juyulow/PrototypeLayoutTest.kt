package eotaek.juyulow

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import eotaek.juyulow.core.design.theme.JUYULOWTheme
import eotaek.juyulow.core.model.ComparisonContext
import eotaek.juyulow.core.model.ComparisonSession
import eotaek.juyulow.core.model.JourneyContext
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.core.model.RefuelValueSource
import eotaek.juyulow.core.model.Station
import eotaek.juyulow.core.model.VehicleEnergy
import eotaek.juyulow.core.model.VehicleFetchStatus
import eotaek.juyulow.core.model.VehicleValueOrigin
import eotaek.juyulow.core.model.resolveRefuelConditions
import eotaek.juyulow.feature.main.results.ResultsScreen
import eotaek.juyulow.feature.main.results.ResultsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class PrototypeLayoutTest(private val widthDp: Int, private val dark: Boolean) {
    @get:Rule
    val compose = createAndroidComposeRule<PrototypeTestActivity>()

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}dp dark={1}")
        fun sizes() = listOf(
            arrayOf<Any>(1700, false),
            arrayOf<Any>(840, false),
            arrayOf<Any>(1700, true),
            arrayOf<Any>(840, true)
        )
    }

    @Test
    fun twoPanesScrollWithoutCoveringActionOrOverflowingText() {
        val stations = (0..7).map { index ->
            Station(
                "station-$index",
                "한길 셀프주유소 $index",
                "S-OIL",
                1600 + index * 10,
                37.6,
                127.0,
                1800,
                300,
                2300,
                420,
                30200,
                2760,
                500
            )
        }
        val session = ComparisonSession(
            "layout",
            ComparisonContext(
                JourneyContext(),
                resolveRefuelConditions(RefuelSettings(includeTimeCost = true)).copy(
                    liters = 37.655,
                    efficiencyKmPerL = 100.0 / 8.5,
                    litersSource = RefuelValueSource.TANK_LEVEL,
                    efficiencySource = RefuelValueSource.CUMULATIVE_ECONOMY,
                    energy = VehicleEnergy(
                        listOf(10),
                        VehicleFetchStatus.SUCCESS,
                        VehicleValueOrigin.FETCH
                    ),
                ),
                1670
            ),
            stations
        )
        compose.setContent {
            // Exact logical viewports inside the Pleos host window; half-density avoids fractional line-height rounding.
            CompositionLocalProvider(LocalDensity provides Density(.5f, 1f)) {
                JUYULOWTheme(darkTheme = dark) {
                    Box(
                        Modifier
                            .requiredSize(widthDp.dp, 1184.dp)
                            .testTag("viewport")
                    ) {
                        ResultsScreen(
                            ResultsUiState(
                                session,
                                JourneyMode.WAYPOINT,
                                selectedId = "station-0"
                            ), {}, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        val list = compose.onNodeWithTag("station-list-pane").fetchSemanticsNode().boundsInRoot
        val detail = compose.onNodeWithTag("station-detail-pane").fetchSemanticsNode().boundsInRoot
        assertTrue(detail.left >= list.right)
        assertEquals(list.width, detail.width, 1f)
        compose.onNodeWithTag("station-list-scroll").performScrollToNode(hasTestTag("station-7"))
        compose.onNodeWithTag("station-7").assertIsDisplayed()
        compose.onNodeWithTag("confirm-route").assertIsDisplayed()
        val last = compose.onNodeWithTag("station-7").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag("confirm-route").fetchSemanticsNode().boundsInRoot
        assertTrue(last.bottom <= action.top)
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "prototype-screenshots"
        ).apply { mkdirs() }
        File(directory, "results-$widthDp-${if (dark) "dark" else "light"}.png").outputStream()
            .use { stream ->
                compose.onNodeWithTag("viewport").captureToImage().asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        val overflow = mutableListOf<String>()
        compose.onAllNodes(isRoot()).fetchSemanticsNodes().forEach { root ->
            fun check(node: androidx.compose.ui.semantics.SemanticsNode) {
                val results = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                results.forEach { text ->
                    // Cached MultiParagraph.width can be the original max constraint even for
                    // shrink-wrapped Text. Check actual laid-out glyphs, not that padded width.
                    val clipped = text.multiParagraph.height > text.size.height + 1f ||
                            text.multiParagraph.didExceedMaxLines || (0 until text.lineCount).any { line ->
                        text.isLineEllipsized(line) || text.getLineLeft(line) < -1f || text.getLineRight(
                            line
                        ) > text.size.width + 1f
                    }
                    if (clipped) overflow.add("${text.layoutInput.text.text}: ${text.size}")
                }
                node.children.forEach(::check)
            }
            check(root)
        }
        assertTrue("Text overflow: ${overflow.joinToString("; ")}", overflow.isEmpty())
        compose.onNodeWithText("37.7 L 주유 · 주변 최고가 1,670원/L 대비").performScrollTo()
            .assertIsDisplayed()
    }
}
