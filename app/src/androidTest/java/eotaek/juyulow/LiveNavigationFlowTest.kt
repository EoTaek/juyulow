package eotaek.juyulow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eotaek.juyulow.core.data.JourneyRepository
import eotaek.juyulow.core.data.LiveJourneyRepository
import eotaek.juyulow.core.data.NavigationRepository
import eotaek.juyulow.core.data.StationRepository
import eotaek.juyulow.core.model.NaviCandidate
import eotaek.juyulow.core.model.NaviLocation
import eotaek.juyulow.core.model.NaviPoint
import eotaek.juyulow.core.model.NaviResult
import eotaek.juyulow.core.model.NaviSnapshot
import eotaek.juyulow.core.model.NaviState
import eotaek.juyulow.core.model.NaviStatus
import eotaek.juyulow.core.model.NaviTrip
import eotaek.juyulow.navigation.verifiedStations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module

/** Uses the production coordinator and UI, with a deterministic SDK boundary and no route mutations. */
class LiveNavigationFlowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val navi = FakeNavigation()
    private lateinit var live: LiveJourneyRepository
    private val repositories = object : ExternalResource() {
        private lateinit var oldJourney: JourneyRepository
        private lateinit var oldStations: StationRepository
        private lateinit var fake: org.koin.core.module.Module
        override fun before() {
            oldJourney = GlobalContext.get().get(); oldStations = GlobalContext.get().get()
            live = LiveJourneyRepository({ navi }, verifiedStations)
            fake = module {
                single<JourneyRepository> { live }
                single<StationRepository> { live }
            }
            loadKoinModules(fake)
        }

        override fun after() {
            runBlocking { live.release() }
            unloadKoinModules(fake)
            loadKoinModules(module {
                single<JourneyRepository> { oldJourney }
                single<StationRepository> { oldStations }
            })
        }
    }
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(repositories).around(compose)

    private fun search() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("search-stations").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("search-stations").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("confirm-route").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForCompletion() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("목적지를 설정했어요.").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun replacementConfirmationCancellationAndCompletion() {
        search()
        compose.onNodeWithText("8곳 찾았어요").assertIsDisplayed()
        compose.onNodeWithText("시연용 가격").assertDoesNotExist()
        compose.onNodeWithTag("confirm-route").performClick()
        compose.onNodeWithText("취소").performClick()
        assertEquals(0, navi.routes)
        compose.onNodeWithTag("confirm-route").performClick()
        compose.onNodeWithText("목적지 변경").performClick()
        waitForCompletion()
        assertEquals(1, navi.routes)
        compose.onNodeWithText("내비게이션 열기").performClick()
        compose.waitForIdle(); assertEquals(1, navi.opens)
        compose.onNodeWithText("다른 주유소 비교").performClick()
        compose.onNodeWithTag("confirm-route").assertIsEnabled()
        compose.onNodeWithTag("confirm-route").performClick()
        compose.onNodeWithText("취소").performClick()
        assertEquals(1, navi.trips)
    }


    @Test
    fun movingWhileConfirmationIsOpenStillAllowsDestinationSetting() {
        search()
        compose.onNodeWithTag("confirm-route").performClick()
        compose.runOnIdle {
            navi.current = navi.current.copy(
                location = navi.current.location.copy(
                    latitude = 38.0,
                    routeId = "rerouted",
                    routeIndex = 12
                ),
                routing = true,
                routeOption = "FAST",
            )
            navi.state.value = NaviState(NaviStatus.READY, navi.current)
        }
        compose.onNodeWithText("목적지를 변경할까요?").assertIsDisplayed()
        compose.onNodeWithText("목적지 변경").performClick()
        waitForCompletion()
        assertEquals(1, navi.routes)
    }

    @Test
    fun addsFirstWaypointWithoutReplacementDialog() {
        search()
        compose.onNodeWithText("경유지로 추가").performClick()
        compose.onNodeWithTag("confirm-route").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("경유지를 추가했어요.").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("old", navi.current.destination?.poiId)
        assertEquals(1, navi.current.waypoints.size)
        assertEquals(1, navi.routes)
    }

    @Test
    fun uncertainRouteIsReconciledWithoutResubmission() {
        navi.ambiguous = true
        search()
        compose.onNodeWithTag("confirm-route").performClick()
        compose.onNodeWithText("목적지 변경").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("상태 다시 확인").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("confirm-route").assertIsNotEnabled()
        compose.onNodeWithText("상태 다시 확인").performClick()
        waitForCompletion()
        assertEquals(1, navi.routes)
    }

    private class FakeNavigation : NavigationRepository {
        var current = NaviSnapshot(
            NaviLocation(37.4127212, 127.094681, "route", 0, 1),
            NaviPoint(37.5, 127.0, "기존 목적지", "old", "0"),
            emptyList(),
            "ROUTE_GUIDING",
            false,
            "RECOMMENDED",
            1
        )
        override val state = MutableStateFlow(NaviState())
        var routes = 0
        var trips = 0
        var opens = 0
        var ambiguous = false
        override suspend fun initialize() = refresh()
        override suspend fun refresh(): NaviResult<NaviSnapshot> {
            state.value = NaviState(NaviStatus.READY, current); return NaviResult.Success(current)
        }

        override suspend fun trips(candidates: List<NaviCandidate>): NaviResult<List<NaviTrip>> {
            trips++; return NaviResult.Success(candidates.mapIndexed { i, c ->
                NaviTrip(
                    c,
                    1000 + i * 100,
                    200 + i * 10,
                    1
                )
            })
        }

        override suspend fun setRoute(
            expected: NaviSnapshot,
            candidate: NaviCandidate,
            asWaypoint: Boolean
        ): NaviResult<NaviSnapshot> {
            routes++; current =
                if (asWaypoint) current.copy(waypoints = listOf(candidate.point) + current.waypoints) else current.copy(
                    destination = candidate.point,
                    waypoints = emptyList()
                )
            state.value =
                NaviState(if (ambiguous) NaviStatus.UNCERTAIN else NaviStatus.READY, current)
            return if (ambiguous) NaviResult.Failure("ROUTE_NOT_CONFIRMED") else NaviResult.Success(
                current
            )
        }

        override suspend fun openNavigation(): NaviResult<Unit> {
            opens++; return NaviResult.Success(Unit)
        }

        override suspend fun release() {}
    }
}
