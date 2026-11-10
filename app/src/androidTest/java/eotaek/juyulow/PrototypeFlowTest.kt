package eotaek.juyulow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import eotaek.juyulow.core.data.JourneyRepository
import eotaek.juyulow.core.data.SettingsRepository
import eotaek.juyulow.core.model.RefuelSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class PrototypeFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var settings: SettingsRepository
    private lateinit var original: RefuelSettings

    @Before
    fun rememberUserSettings() {
        settings = GlobalContext.get().get()
        original = runBlocking { settings.settings.first() }
        GlobalContext.get().get<JourneyRepository>().setHasDestination(true)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("search-stations").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    @After
    fun restoreUserSettings() {
        runBlocking { settings.save(original) }
    }

    private fun search() {
        compose.onNodeWithTag("search-stations").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("confirm-route").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun modesCompletionBackAndWindowRecreationRetainComparison() {
        search()
        compose.onNodeWithTag("station-list-scroll").performScrollToNode(hasTestTag("station-0"))
        compose.onNodeWithTag("station-0").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("station-0").assertIsSelected()
        compose.onNodeWithTag("tab-DESTINATION").performClick()
        compose.onNodeWithTag("confirm-route").performClick()
        compose.onNodeWithText("목적지를 설정했어요.").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("station-list-scroll").performScrollToNode(hasTestTag("station-0"))
        compose.onNodeWithTag("station-0").assertIsSelected()
        compose.onNodeWithTag("tab-ROUND_TRIP").performClick()
        compose.onNodeWithTag("confirm-route").performClick()
        compose.onNodeWithText("왕복 경로를 준비했어요.").assertIsDisplayed()
        compose.onNodeWithText("다른 주유소 비교").performClick()
        compose.onNodeWithTag("tab-WAYPOINT").performClick()
        compose.onNodeWithTag("confirm-route").performClick()
        compose.onNodeWithText("경유지를 추가했어요.").assertIsDisplayed()
        compose.onNodeWithText("홈으로").performClick()
        compose.onNodeWithText("유효 목적지 있음").performClick()
        search()
        compose.onNodeWithTag("tab-WAYPOINT").assertDoesNotExist()
        compose.onNodeWithTag("tab-DESTINATION").assertIsSelected()
        compose.onNodeWithTag("tab-ROUND_TRIP").assertExists()
    }

    @Test
    fun automaticVolumeCanBeEditedCancelledAndApplied() {
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithContentDescription("예상 주유량 늘리기").performClick()
        compose.onNodeWithContentDescription("설정 닫기").performClick()
        assertEquals(original, runBlocking { settings.settings.first() })
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithContentDescription("예상 주유량 늘리기").performClick()
        compose.onNodeWithText("적용하기").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("적용하기").fetchSemanticsNodes().isEmpty()
        }
        org.junit.Assert.assertNotNull(runBlocking { settings.settings.first() }.litersOverride)
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithText("수동").assertIsDisplayed()
        pressBack()
    }

    @Test
    fun settingsCancelApplyAndReopen() {
        val changedMinutes = if (original.refuelMinutes == 20) 19 else original.refuelMinutes + 1
        val stepLabel = if (original.refuelMinutes == 20) "주유 시간 줄이기" else "주유 시간 늘리기"
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithContentDescription(stepLabel).performClick()
        compose.onNodeWithContentDescription("설정 닫기").performClick()
        assertEquals(original, runBlocking { settings.settings.first() })
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithContentDescription("주유 시간 ${original.refuelMinutes} 분").assertExists()
        compose.onNodeWithContentDescription(stepLabel).performClick()
        compose.onNodeWithText("적용하기").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("적용하기").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(changedMinutes, runBlocking { settings.settings.first() }.refuelMinutes)
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithContentDescription("주유 시간 $changedMinutes 분").assertExists()
        pressBack()
        compose.onNodeWithTag("search-stations").assertIsDisplayed()
    }
}
