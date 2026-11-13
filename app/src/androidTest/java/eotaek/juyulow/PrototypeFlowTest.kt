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
import eotaek.juyulow.core.data.MockJourneyRepository
import eotaek.juyulow.core.data.MockStationRepository
import eotaek.juyulow.core.data.SettingsRepository
import eotaek.juyulow.core.data.StationRepository
import eotaek.juyulow.core.model.RefuelSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module

@RunWith(AndroidJUnit4::class)
class PrototypeFlowTest {
    val compose = createAndroidComposeRule<MainActivity>()
    private val repositories = object : ExternalResource() {
        private lateinit var oldJourney: JourneyRepository
        private lateinit var oldStations: StationRepository
        private lateinit var fake: org.koin.core.module.Module
        override fun before() {
            oldJourney = GlobalContext.get().get()
            oldStations = GlobalContext.get().get()
            fake = module {
                single<JourneyRepository> { MockJourneyRepository() }
                single<StationRepository> { MockStationRepository() }
            }
            loadKoinModules(fake)
        }

        override fun after() {
            unloadKoinModules(fake)
            loadKoinModules(module {
                single<JourneyRepository> { oldJourney }
                single<StationRepository> { oldStations }
            })
        }
    }
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(repositories).around(compose)
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
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("confirm-route").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("station-list-scroll").performScrollToNode(hasTestTag("station-0"))
        compose.onNodeWithTag("station-0").assertIsSelected()
        compose.onNodeWithTag("confirm-route").performClick()
        compose.onNodeWithText("목적지를 설정했어요.").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("station-list-scroll").performScrollToNode(hasTestTag("station-0"))
        compose.onNodeWithTag("station-0").assertIsSelected()
        compose.onNodeWithTag("tab-ROUND_TRIP").assertDoesNotExist()
        compose.onNodeWithTag("add-waypoint").performClick()
        compose.onNodeWithText("경유지를 추가했어요.").assertIsDisplayed()
        compose.onNodeWithText("홈으로").performClick()
        compose.runOnIdle {
            GlobalContext.get().get<JourneyRepository>().setHasDestination(false)
        }
        compose.onNodeWithText("목적지 없음").assertIsDisplayed()
        search()
        compose.onNodeWithTag("add-waypoint").assertDoesNotExist()
        compose.onNodeWithTag("confirm-route").assertIsDisplayed()
        compose.onNodeWithTag("tab-ROUND_TRIP").assertDoesNotExist()
    }

    @Test
    fun storedVolumeCanBeEditedCancelledAndApplied() {
        val nextLiters = if (original.liters == 100) 90 else original.liters + 10
        val volumeAction = if (original.liters == 100) "예상 주유량 줄이기" else "예상 주유량 늘리기"
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithContentDescription(volumeAction).performClick()
        compose.onNodeWithContentDescription("설정 닫기").performClick()
        assertEquals(original, runBlocking { settings.settings.first() })
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithContentDescription(volumeAction).performClick()
        compose.onNodeWithText("적용하기").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("적용하기").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(nextLiters, runBlocking { settings.settings.first() }.liters)
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithContentDescription("예상 주유량 $nextLiters L").assertExists()
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
