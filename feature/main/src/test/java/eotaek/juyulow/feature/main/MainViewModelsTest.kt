package eotaek.juyulow.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import eotaek.juyulow.core.data.InMemoryComparisonSessionRepository
import eotaek.juyulow.core.data.JourneyRepository
import eotaek.juyulow.core.data.MockJourneyRepository
import eotaek.juyulow.core.data.SettingsRepository
import eotaek.juyulow.core.data.StationRepository
import eotaek.juyulow.core.data.generateStations
import eotaek.juyulow.core.model.ComparisonSession
import eotaek.juyulow.core.model.JourneyContext
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.core.model.NavigationSelection
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.core.model.Station
import eotaek.juyulow.core.model.StationSort
import eotaek.juyulow.feature.main.common.kilometers
import eotaek.juyulow.feature.main.common.minutes
import eotaek.juyulow.feature.main.common.won
import eotaek.juyulow.feature.main.completion.CompletionViewModel
import eotaek.juyulow.feature.main.home.HomeViewModel
import eotaek.juyulow.feature.main.results.ResultsViewModel
import eotaek.juyulow.feature.main.results.previewSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var nextKey = 0
    private fun <T : ViewModel> keep(viewModel: T): T =
        viewModel.also { store.put("${nextKey++}", it) }

    @Before
    fun prepare() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun close() {
        store.clear(); Dispatchers.resetMain()
    }

    private class Settings : SettingsRepository {
        override val settings = MutableStateFlow(RefuelSettings())
        override suspend fun save(settings: RefuelSettings) {
            this.settings.value = settings
        }
    }

    private class Stations : StationRepository {
        var calls = 0
        var fail = false
        var ignoreCancellation = false
        override suspend fun search(journey: JourneyContext): List<Station> {
            calls++
            if (ignoreCancellation) withContext(NonCancellable) { delay(850) } else delay(850)
            if (fail) throw IOException("offline")
            return generateStations(journey, Random(calls))
        }
    }

    @Test
    fun duplicateSearchIsIgnoredAndConditionsAreCapturedAtStart() = runTest(dispatcher) {
        val settings = Settings()
        val stations = Stations()
        val sessions = InMemoryComparisonSessionRepository()
        val home = keep(HomeViewModel(stations, sessions, MockJourneyRepository(), settings))
        runCurrent()
        home.search(); home.search()
        runCurrent()
        assertEquals(1, stations.calls)
        assertTrue(home.state.value.searching)
        settings.save(RefuelSettings(liters = 80))
        advanceUntilIdle()
        val batch = requireNotNull(sessions.get(requireNotNull(home.state.value.readySessionId)))
        assertEquals(40, batch.context.settings.liters)
        home.consumeSearch()
        assertNull(home.state.value.readySessionId)
    }

    @Test
    fun cancelledLateResponseCannotReplaceNewSearch() = runTest(dispatcher) {
        val stations = Stations().apply { ignoreCancellation = true }
        val home = keep(
            HomeViewModel(
                stations,
                InMemoryComparisonSessionRepository(),
                MockJourneyRepository(),
                Settings()
            )
        )
        runCurrent()
        home.search(); runCurrent()
        advanceTimeBy(400.milliseconds)
        home.cancelSearch()
        home.search(); runCurrent()
        advanceTimeBy(450.milliseconds); runCurrent()
        assertNull(home.state.value.readySessionId)
        assertTrue(home.state.value.searching)
        advanceUntilIdle()
        assertNotNull(home.state.value.readySessionId)
        assertEquals(2, stations.calls)
    }

    @Test
    fun initialSearchFailureCanBeRetried() = runTest(dispatcher) {
        val stations = Stations().apply { fail = true }
        val home = keep(
            HomeViewModel(
                stations,
                InMemoryComparisonSessionRepository(),
                MockJourneyRepository(),
                Settings()
            )
        )
        runCurrent(); home.search(); advanceUntilIdle()
        assertNotNull(home.state.value.error)
        assertFalse(home.state.value.searching)
        stations.fail = false
        home.search(); advanceUntilIdle()
        assertNull(home.state.value.error)
        assertNotNull(home.state.value.readySessionId)
    }

    @Test
    fun sortingAndModeChangesRetainSelectionAndData() = runTest(dispatcher) {
        val sessions = InMemoryComparisonSessionRepository()
        val batch = sessions.create(JourneyContext(), RefuelSettings(), previewSession().stations)
        val results =
            keep(ResultsViewModel(batch.id, Stations(), sessions, MockJourneyRepository()))
        val initialId = results.state.value.selectedId
        results.changeSort(StationSort.TIME)
        assertEquals(initialId, results.state.value.selectedId)
        results.select("station-2")
        results.changeMode(JourneyMode.ROUND_TRIP)
        results.changeSort(StationSort.SAVING)
        assertEquals("station-2", results.state.value.selectedId)
        assertEquals(batch, results.state.value.session)
    }

    @Test
    fun selectionCommitsHomeAndCompletionButComparisonStaysOnOldRoute() = runTest(dispatcher) {
        val sessions = InMemoryComparisonSessionRepository()
        val journeys = MockJourneyRepository()
        val stations = Stations()
        val batch =
            sessions.create(journeys.context.value, RefuelSettings(), previewSession().stations)
        val results = keep(ResultsViewModel(batch.id, stations, sessions, journeys))
        results.changeMode(JourneyMode.DESTINATION)
        results.confirm(); results.confirm(); advanceUntilIdle()
        val id = requireNotNull(results.state.value.completedSelectionId)
        val completion = keep(CompletionViewModel(id, journeys))
        assertEquals(results.state.value.selected?.station?.name, completion.selection?.destination)
        assertEquals(completion.selection?.destination, journeys.context.value.destination)
        assertEquals("서울숲", results.state.value.session?.context?.journey?.destination)
        results.consumeSelection()
        results.refresh(); advanceUntilIdle()
        assertEquals("서울숲", results.state.value.session?.context?.journey?.destination)
        assertEquals(28400, results.state.value.session?.context?.journey?.baselineMeters)
        assertEquals(JourneyMode.WAYPOINT, results.state.value.mode)
    }

    @Test
    fun failedRefreshKeepsOldSelectionAndBatchAndEmptyResultsDisableConfirmation() =
        runTest(dispatcher) {
            val sessions = InMemoryComparisonSessionRepository()
            val batch =
                sessions.create(JourneyContext(), RefuelSettings(), previewSession().stations)
            val stations = Stations().apply { fail = true }
            val results =
                keep(ResultsViewModel(batch.id, stations, sessions, MockJourneyRepository()))
            results.select("station-1")
            results.refresh(); advanceUntilIdle()
            assertEquals(batch, results.state.value.session)
            assertEquals("station-1", results.state.value.selectedId)
            assertNotNull(results.state.value.error)
            val empty =
                sessions.create(JourneyContext(destination = null), RefuelSettings(), emptyList())
            val emptyResults =
                keep(ResultsViewModel(empty.id, stations, sessions, MockJourneyRepository()))
            emptyResults.changeMode(JourneyMode.WAYPOINT)
            emptyResults.confirm(); runCurrent()
            assertEquals(JourneyMode.DESTINATION, emptyResults.state.value.mode)
            assertNull(emptyResults.state.value.completedSelectionId)
        }

    @Test
    fun failedRouteDoesNotUpdateHomeAndCanBeRetried() = runTest(dispatcher) {
        val sessions = InMemoryComparisonSessionRepository()
        val delegate = MockJourneyRepository()
        var fail = true
        val journeys = object : JourneyRepository by delegate {
            override suspend fun select(
                session: ComparisonSession,
                station: Station,
                mode: JourneyMode
            ): NavigationSelection {
                if (fail) throw IOException("failed")
                return delegate.select(session, station, mode)
            }
        }
        val batch = sessions.create(JourneyContext(), RefuelSettings(), previewSession().stations)
        val results = keep(ResultsViewModel(batch.id, Stations(), sessions, journeys))
        results.changeMode(JourneyMode.DESTINATION)
        results.confirm(); advanceUntilIdle()
        assertEquals(JourneyContext(), journeys.context.value)
        assertNotNull(results.state.value.error)
        assertNull(results.state.value.completedSelectionId)
        fail = false
        results.confirm(); advanceUntilIdle()
        assertNotNull(results.state.value.completedSelectionId)
        assertNull(results.state.value.error)
    }

    @Test
    fun poppedResultsCannotReplaceANewHomeBatchWithALateRefresh() = runTest(dispatcher) {
        val sessions = InMemoryComparisonSessionRepository()
        val old = sessions.create(JourneyContext(), RefuelSettings(), previewSession().stations)
        val stations = Stations().apply { ignoreCancellation = true }
        val results = keep(ResultsViewModel(old.id, stations, sessions, MockJourneyRepository()))
        results.refresh(); runCurrent()
        advanceTimeBy(300.milliseconds)
        store.clear()
        val next = sessions.create(
            JourneyContext(destination = null),
            RefuelSettings(liters = 80),
            previewSession().stations
        )
        advanceUntilIdle()
        assertEquals(next, sessions.get(next.id))
    }

    @Test
    fun displayUsesWebRoundingWithoutChangingSourceValues() {
        assertEquals("1,235", won(1234.5))
        assertEquals("0", won(-.5))
        assertEquals("2", minutes(61))
        assertEquals("0", minutes(0))
        assertEquals("28.4", kilometers(28400))
    }
}
