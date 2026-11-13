package eotaek.juyulow

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import eotaek.juyulow.core.design.theme.JUYULOWTheme
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.core.model.NavigationSelection
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.core.model.VehicleFetchStatus
import eotaek.juyulow.core.model.VehicleField
import eotaek.juyulow.core.model.VehicleProperty
import eotaek.juyulow.core.model.VehicleValue
import eotaek.juyulow.core.model.VehicleValueOrigin
import eotaek.juyulow.core.model.resolveRefuelConditions
import eotaek.juyulow.feature.main.completion.CompletionScreen
import eotaek.juyulow.feature.main.home.HomeScreen
import eotaek.juyulow.feature.main.home.HomeUiState
import eotaek.juyulow.feature.main.settings.SettingsScreen
import eotaek.juyulow.feature.main.settings.SettingsUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class VehicleConditionsLayoutTest(private val widthDp: Int, private val dark: Boolean) {
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

    private fun fetched(value: VehicleValue) = VehicleField(
        status = VehicleFetchStatus.SUCCESS, value = value, origin = VehicleValueOrigin.FETCH,
    )

    private val fields = mapOf(
        VehicleProperty.FUEL_CAPACITY to fetched(VehicleValue.Number(50_000.0)),
        VehicleProperty.FUEL_LEVEL to fetched(VehicleValue.Number(12_345.0)),
        VehicleProperty.FUEL_PERCENT to fetched(VehicleValue.Number(24.69)),
        VehicleProperty.CUMULATIVE_ECONOMY to fetched(VehicleValue.Number(8.5)),
        VehicleProperty.CURRENT_ECONOMY to fetched(VehicleValue.Number(9.25)),
        VehicleProperty.FUEL_TYPES to fetched(VehicleValue.FuelTypes(listOf(10))),
    )
    private val conditions = resolveRefuelConditions(RefuelSettings(), fields)

    private fun render(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(.5f, 1f)) {
                JUYULOWTheme(darkTheme = dark) {
                    Box(
                        Modifier
                            .requiredSize(widthDp.dp, 1184.dp)
                            .testTag("viewport")
                    ) { content() }
                }
            }
        }
    }

    private fun verifyAndCapture(screen: String) {
        val overflow = mutableListOf<String>()
        fun check(node: SemanticsNode) {
            val results = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            results.forEach { text ->
                val clipped = text.multiParagraph.height > text.size.height + 1f ||
                        text.multiParagraph.didExceedMaxLines || (0 until text.lineCount).any { line ->
                    text.isLineEllipsized(line) || text.getLineLeft(line) < -1f || text.getLineRight(
                        line
                    ) > text.size.width + 1f
                }
                if (clipped) overflow += text.layoutInput.text.text
            }
            node.children.forEach(::check)
        }
        compose.onAllNodes(isRoot()).fetchSemanticsNodes().forEach(::check)
        assertTrue("Text overflow: $overflow", overflow.isEmpty())
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "prototype-screenshots"
        ).apply { mkdirs() }
        File(directory, "$screen-$widthDp-${if (dark) "dark" else "light"}.png").outputStream()
            .use {
                compose.onNodeWithTag("viewport").captureToImage().asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }
    }

    @Test
    fun homeShowsStoredConditionsWithoutEnergyNotices() {
        render {
            HomeScreen(
                HomeUiState(settings = RefuelSettings(), vehicleFields = fields),
                {},
                {})
        }
        compose.onNodeWithText("차량 에너지원:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("전기차", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("search-stations").assertIsEnabled()
        compose.onNodeWithTag("open-settings").assertIsDisplayed()
        verifyAndCapture("vehicle-home")
    }

    @Test
    fun settingsShowsEditableStoredValuesAndScrollableVehicleDetails() {
        render {
            SettingsScreen(
                SettingsUiState(draft = RefuelSettings(), vehicleFields = fields),
                {},
                {},
                {})
        }
        compose.onNodeWithText("40 L").assertIsDisplayed()
        compose.onNodeWithText("차량 에너지원:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("전기차", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("예상 주유량 늘리기").assertIsEnabled()
        compose.onNodeWithContentDescription("평균연비 늘리기").assertIsEnabled()
        compose.onNodeWithText("차량 조회 정보").performScrollTo().assertIsDisplayed()
        verifyAndCapture("vehicle-settings")
        compose.onNodeWithText("적용하기").assertDoesNotExist()
    }

    @Test
    fun settingsRemainsInteractiveWithoutProgressWhileSaving() {
        render {
            SettingsScreen(
                SettingsUiState(draft = RefuelSettings(), saving = true, vehicleFields = fields),
                {}, {}, {})
        }
        compose.onNodeWithContentDescription("설정 닫기").assertIsEnabled()
        compose.onNodeWithContentDescription("검색 반경 늘리기").assertIsEnabled()
        compose.onNodeWithContentDescription("예상 주유량 늘리기").assertIsEnabled()
        compose.onNodeWithContentDescription("평균연비 늘리기").assertIsEnabled()
        compose.onNodeWithContentDescription("주유 시간 늘리기").assertIsEnabled()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertDoesNotExist()
    }

    @Test
    fun completionShowsSelectionConditions() {
        val selection = NavigationSelection(
            "id",
            "station",
            "한길 셀프주유소",
            JourneyMode.WAYPOINT,
            "서울숲",
            "한길 셀프주유소",
            30200,
            3060,
            300,
            conditions
        )
        render { CompletionScreen(selection, {}, {}) }
        compose.onNodeWithText("차량 에너지원: 전기").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("홈으로").performScrollTo().assertIsDisplayed()
        verifyAndCapture("vehicle-completion")
    }
}
