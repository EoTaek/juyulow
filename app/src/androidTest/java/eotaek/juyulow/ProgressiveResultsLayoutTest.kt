package eotaek.juyulow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import eotaek.juyulow.core.design.theme.JUYULOWTheme
import eotaek.juyulow.core.model.ComparisonContext
import eotaek.juyulow.core.model.ComparisonSession
import eotaek.juyulow.core.model.JourneyContext
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.core.model.Station
import eotaek.juyulow.core.model.resolveRefuelConditions
import eotaek.juyulow.feature.main.results.ResultsScreen
import eotaek.juyulow.feature.main.results.ResultsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ProgressiveResultsLayoutTest(private val width: Int, private val dark: Boolean) {
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
    fun selectionAndFocusFollowStationAcrossProgressiveReordering() {
        val stations = (0..31).map { i ->
            Station(
                "s$i",
                "긴 상호명 주유소 $i",
                "S-OIL",
                1800 - i * 10,
                37.4,
                127.1,
                if (i < 2) 1000 else null,
                if (i < 2) 100 else null,
                null,
                null,
                null,
                null,
                null,
                observedAtMillis = 1,
                sourceIndex = i,
                routePending = i >= 2
            )
        }
        val session = ComparisonSession(
            "progressive", ComparisonContext(
                JourneyContext(null, null, null, destinationOnly = true),
                resolveRefuelConditions(RefuelSettings()), 1800
            ), stations, firstReadyId = "s0", searchComplete = false
        )
        val state = mutableStateOf(ResultsUiState(session = session, selectedId = "s0"))
        lateinit var inputModeManager: InputModeManager
        compose.setContent {
            inputModeManager = LocalInputModeManager.current
            CompositionLocalProvider(LocalDensity provides Density(.5f, 1f)) {
                JUYULOWTheme(darkTheme = dark) {
                    Box(Modifier.requiredSize(width.dp, 1184.dp)) {
                        ResultsScreen(state.value, {}, {}, {}, {}, {}, onUserInteraction = {
                            state.value = state.value.copy(userInteracted = true)
                        })
                    }
                }
            }
        }
        compose.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        compose.onNodeWithTag("s0").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("s0").assertIsFocused().assertIsSelected()
        compose.onNodeWithTag("s0").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("s1").assertIsFocused()
        compose.onNodeWithTag("s0").assertIsSelected()
        compose.onNodeWithTag("s0").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.runOnIdle {
            state.value = state.value.copy(session = session.copy(stations = stations.map {
                if (it.id == "s2") it.copy(
                    outboundMeters = 1000,
                    outboundSeconds = 90,
                    routePending = false
                ) else it
            }))
        }
        compose.waitForIdle()
        compose.onNodeWithTag("s0").assertIsFocused().assertIsSelected()
        compose.onNodeWithTag("confirm-route").assertIsEnabled()
        compose.onNodeWithText("잠정 절약액").assertIsDisplayed()
        compose.onNodeWithText("기준 유가 갱신 중").assertIsDisplayed()
    }

    @Test
    fun progressiveReorderingPinsScrollToZeroUntilUserScrolls() {
        val stations = (0..31).map { i ->
            Station(
                "s$i", "주유소 $i", "S-OIL", 1800 - i * 10, 37.4, 127.1,
                if (i < 2) null else 1000, if (i < 2) null else 100, null, null, null, null, null,
                observedAtMillis = 1, sourceIndex = i, routePending = i < 2,
            )
        }
        val session = ComparisonSession(
            "scroll", ComparisonContext(
                JourneyContext(null, null, null, destinationOnly = true),
                resolveRefuelConditions(RefuelSettings()), 1800,
            ), stations, searchComplete = false,
        )
        val state = mutableStateOf(ResultsUiState(session = session, selectedId = "s31"))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(.5f, 1f)) {
                JUYULOWTheme(darkTheme = dark) {
                    Box(Modifier.requiredSize(width.dp, 1184.dp)) {
                        ResultsScreen(state.value, {}, {}, {}, {}, {}, onUserInteraction = {
                            state.value = state.value.copy(userInteracted = true)
                        })
                    }
                }
            }
        }
        val list = compose.onNodeWithTag("station-list-scroll")
        compose.waitForIdle()
        val initialScrollMax =
            list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
        // 기존 첫 항목이 아래로 밀리는 두 번의 ETA 갱신을 재현한다.
        listOf("s0", "s1").forEachIndexed { index, bestId ->
            compose.runOnIdle {
                val batch = state.value.session!!
                state.value = state.value.copy(
                    session = batch.copy(stations = batch.stations.map {
                        if (it.id == bestId) it.copy(
                            priceWonPerL = 900 - index * 100,
                            outboundMeters = 1000, outboundSeconds = 100, routePending = false,
                        ) else it
                    }),
                    selectedId = bestId,
                )
            }
            compose.waitForIdle()
            assertEquals(0f, list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value())
            assertEquals(
                initialScrollMax,
                list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
            )
            compose.onNodeWithTag(bestId).assertIsDisplayed().assertIsSelected()
        }
        list.performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(state.value.userInteracted)
            state.value = state.value.copy(session = state.value.session!!.copy(searchComplete = true))
        }
        compose.waitForIdle()
        assertTrue(list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
        compose.runOnIdle { assertEquals("s1", state.value.selectedId) }
    }
}
