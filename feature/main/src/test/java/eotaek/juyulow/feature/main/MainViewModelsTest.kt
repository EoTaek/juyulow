package eotaek.juyulow.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import eotaek.juyulow.core.data.InMemoryComparisonSessionRepository
import eotaek.juyulow.core.data.InMemoryVehicleInfoRepository
import eotaek.juyulow.core.data.JourneyRepository
import eotaek.juyulow.core.data.MockJourneyRepository
import eotaek.juyulow.core.data.SettingsRepository
import eotaek.juyulow.core.data.StationRepository
import eotaek.juyulow.core.data.generateStations
import eotaek.juyulow.core.design.component.displayLabel
import eotaek.juyulow.core.design.component.vehicleNumber
import eotaek.juyulow.core.model.ComparisonSession
import eotaek.juyulow.core.model.JourneyContext
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.core.model.NavigationSelection
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.core.model.Station
import eotaek.juyulow.core.model.StationSort
import eotaek.juyulow.core.model.VehicleEnergy
import eotaek.juyulow.core.model.VehicleFetchStatus
import eotaek.juyulow.core.model.VehicleProperty
import eotaek.juyulow.core.model.VehicleValue
import eotaek.juyulow.core.model.VehicleValueOrigin
import eotaek.juyulow.core.model.resolveRefuelConditions
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
        val pauses = mutableListOf<Boolean>()
        override fun pauseSearch(paused: Boolean) { pauses += paused }
        var calls = 0
        var fail = false
        var ignoreCancellation = false
        override suspend fun search(journey: JourneyContext): List<Station> {
            calls++
            if (ignoreCancellation) withContext(NonCancellable) { delay(850.milliseconds) } else delay(
                850.milliseconds
            )
            if (fail) throw IOException("offline")
            return generateStations(journey, Random(calls))
        }
    }

    @Test
    fun targetChangeDismissesReplacementAndResumesSearchOnlyWhenDialogWasOpen() = runTest(dispatcher) {
        val point = eotaek.juyulow.core.model.NaviPoint(37.4, 127.0, "기존 목적지", "old", "0")
        val original = eotaek.juyulow.core.model.NaviSnapshot(
            eotaek.juyulow.core.model.NaviLocation(37.4, 127.0, "r", 0, 1),
            point, emptyList(), "ROUTE_GUIDING", false, "RECOMMENDED", 1
        )
        val context = MutableStateFlow(JourneyContext(
            point.name, navigation = original, destinationOnly = true, tripOrigin = original
        ))
        val journeys = object : JourneyRepository by MockJourneyRepository() {
            override val context = context
        }
        val stations = Stations()
        val sessions = InMemoryComparisonSessionRepository()
        val batch = sessions.create(context.value, resolveRefuelConditions(RefuelSettings()), previewSession().stations)
        val results = keep(ResultsViewModel(batch.id, stations, sessions, journeys))
        runCurrent()
        assertTrue(stations.pauses.isEmpty())
        results.confirm()
        assertTrue(results.state.value.confirmReplacement)
        assertEquals(listOf(true), stations.pauses)
        // Movement alone must keep the confirmation and the search paused.
        context.value = context.value.copy(navigation = original.copy(
            location = original.location.copy(latitude = 37.5)
        ))
        runCurrent()
        assertTrue(results.state.value.confirmReplacement)
        assertEquals(listOf(true), stations.pauses)
        context.value = context.value.copy(navigation = original.copy(
            destination = point.copy(name = "새 목적지", poiId = "new")
        ))
        runCurrent()
        assertFalse(results.state.value.confirmReplacement)
        assertEquals(listOf(true, false), stations.pauses)
        // Further target updates without an open dialog must not release another pause.
        context.value = context.value.copy(navigation = original.copy(destination = null))
        runCurrent()
        assertEquals(listOf(true, false), stations.pauses)
    }

    @Test
    fun movementKeepsReplacementConfirmationAndAllowsExpiredComparisonSelection() =
        runTest(dispatcher) {
            val point = eotaek.juyulow.core.model.NaviPoint(37.4, 127.0, "기존 목적지", "old", "0")
            val original = eotaek.juyulow.core.model.NaviSnapshot(
                eotaek.juyulow.core.model.NaviLocation(37.4, 127.0, "r", 0, 1),
                point, emptyList(), "ROUTE_GUIDING", false, "RECOMMENDED", 1
            )
            val context = MutableStateFlow(
                JourneyContext(
                    point.name, navigation = original,
                    destinationOnly = true, tripOrigin = original
                )
            )
            val delegate = MockJourneyRepository()
            val journeys = object : JourneyRepository by delegate {
                override val context = context
            }
            val sessions = InMemoryComparisonSessionRepository()
            val batch = sessions.create(
                context.value,
                resolveRefuelConditions(RefuelSettings()),
                previewSession().stations
            )
            val results = keep(ResultsViewModel(batch.id, Stations(), sessions, journeys))
            runCurrent()
            results.confirm()
            assertTrue(results.state.value.confirmReplacement)
            val moved = original.copy(
                location = original.location.copy(latitude = 37.5, routeId = "new", routeIndex = 9),
                routing = true, routeOption = "FAST"
            )
            context.value = context.value.copy(navigation = moved)
            runCurrent()
            assertTrue(results.state.value.comparisonExpired)
            assertTrue(results.state.value.confirmReplacement)
            results.confirm(true)
            advanceUntilIdle()
            assertNotNull(results.state.value.completedSelectionId)
        }

    @Test
    fun duplicateSearchIsIgnoredAndConditionsAreCapturedAtStart() = runTest(dispatcher) {
        val settings = Settings()
        val stations = Stations()
        val sessions = InMemoryComparisonSessionRepository()
        val home = keep(
            HomeViewModel(
                stations,
                sessions,
                MockJourneyRepository(),
                settings,
                InMemoryVehicleInfoRepository()
            )
        )
        runCurrent()
        home.search(); home.search()
        runCurrent()
        assertEquals(1, stations.calls)
        assertTrue(home.state.value.searching)
        settings.save(RefuelSettings(liters = 80))
        advanceUntilIdle()
        val batch = requireNotNull(sessions.get(requireNotNull(home.state.value.readySessionId)))
        assertEquals(40.0, batch.context.conditions.liters, 0.0)
        home.consumeSearch()
        assertNull(home.state.value.readySessionId)
    }

    @Test
    fun lateVehicleReadsPreserveSettingsAndCompletionKeepsSnapshot() =
        runTest(dispatcher) {
            val settings = Settings()
            val vehicle = InMemoryVehicleInfoRepository()
            val sessions = InMemoryComparisonSessionRepository()
            val journeys = MockJourneyRepository()
            val stations = Stations()
            val home = keep(HomeViewModel(stations, sessions, journeys, settings, vehicle))
            runCurrent()
            home.search()
            runCurrent()
            vehicle.complete(VehicleProperty.FUEL_CAPACITY, VehicleValue.Number(50_000.0), 1, null)
            vehicle.complete(VehicleProperty.FUEL_LEVEL, VehicleValue.Number(12_345.0), 1, null)
            vehicle.complete(VehicleProperty.CUMULATIVE_ECONOMY, VehicleValue.Number(8.5), 1, null)
            vehicle.complete(
                VehicleProperty.FUEL_TYPES, VehicleValue.FuelTypes(listOf(10)), 1, null
            )
            advanceUntilIdle()
            val first =
                requireNotNull(sessions.get(requireNotNull(home.state.value.readySessionId)))
            assertEquals(40.0, first.context.conditions.liters, 0.0)
            assertEquals(12.0, first.context.conditions.efficiencyKmPerL, 0.0)
            assertTrue(first.context.conditions.energy.codes.isEmpty())
            assertEquals(40.0, requireNotNull(home.state.value.conditions).liters, 0.0)
            val results = keep(ResultsViewModel(first.id, stations, sessions, journeys))
            settings.save(RefuelSettings(liters = 80))
            results.refresh()
            advanceUntilIdle()
            assertEquals(first.context.conditions, results.state.value.session?.context?.conditions)
            results.confirm()
            advanceUntilIdle()
            val completion = keep(
                CompletionViewModel(
                    requireNotNull(results.state.value.completedSelectionId), journeys
                )
            )
            assertEquals(first.context.conditions, completion.selection?.conditions)
            home.consumeSearch()
            home.search()
            advanceUntilIdle()
            val second =
                requireNotNull(sessions.get(requireNotNull(home.state.value.readySessionId)))
            assertEquals(80.0, second.context.conditions.liters, 0.0)
            assertEquals(100.0 / 8.5, second.context.conditions.efficiencyKmPerL, 0.0)
            assertEquals(listOf(10), second.context.conditions.energy.codes)
        }

    @Test
    fun fullTankStillUsesStoredLiters() = runTest(dispatcher) {
        val vehicle = InMemoryVehicleInfoRepository()
        vehicle.complete(VehicleProperty.FUEL_CAPACITY, VehicleValue.Number(15_000.0), 1, null)
        vehicle.complete(VehicleProperty.FUEL_LEVEL, VehicleValue.Number(15_000.0), 1, null)
        val sessions = InMemoryComparisonSessionRepository()
        val home =
            keep(HomeViewModel(Stations(), sessions, MockJourneyRepository(), Settings(), vehicle))
        runCurrent()
        home.search()
        advanceUntilIdle()
        val session = requireNotNull(sessions.get(requireNotNull(home.state.value.readySessionId)))
        assertEquals(40.0, session.context.conditions.liters, 0.0)
    }

    @Test
    fun cancelledLateResponseCannotReplaceNewSearch() = runTest(dispatcher) {
        val stations = Stations().apply { ignoreCancellation = true }
        val home = keep(
            HomeViewModel(
                stations,
                InMemoryComparisonSessionRepository(),
                MockJourneyRepository(),
                Settings(),
                InMemoryVehicleInfoRepository(),
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
                Settings(),
                InMemoryVehicleInfoRepository(),
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
        val batch = sessions.create(
            JourneyContext(), resolveRefuelConditions(RefuelSettings()), previewSession().stations
        )
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
        val batch = sessions.create(
            journeys.context.value,
            resolveRefuelConditions(RefuelSettings()),
            previewSession().stations
        )
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
            val batch = sessions.create(
                JourneyContext(),
                resolveRefuelConditions(RefuelSettings()),
                previewSession().stations
            )
            val stations = Stations().apply { fail = true }
            val results =
                keep(ResultsViewModel(batch.id, stations, sessions, MockJourneyRepository()))
            results.select("station-1")
            results.refresh(); advanceUntilIdle()
            assertEquals(batch, results.state.value.session)
            assertEquals("station-1", results.state.value.selectedId)
            assertNotNull(results.state.value.error)
            val empty = sessions.create(
                JourneyContext(destination = null),
                resolveRefuelConditions(RefuelSettings()),
                emptyList()
            )
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
                session: ComparisonSession, station: Station, mode: JourneyMode
            ): NavigationSelection {
                if (fail) throw IOException("failed")
                return delegate.select(session, station, mode)
            }
        }
        val batch = sessions.create(
            JourneyContext(), resolveRefuelConditions(RefuelSettings()), previewSession().stations
        )
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
        val old = sessions.create(
            JourneyContext(), resolveRefuelConditions(RefuelSettings()), previewSession().stations
        )
        val stations = Stations().apply { ignoreCancellation = true }
        val results = keep(ResultsViewModel(old.id, stations, sessions, MockJourneyRepository()))
        results.refresh(); runCurrent()
        advanceTimeBy(300.milliseconds)
        store.clear()
        val next = sessions.create(
            JourneyContext(destination = null),
            resolveRefuelConditions(RefuelSettings(liters = 80)),
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
        assertEquals("37.7", vehicleNumber(37.655))
        assertEquals("0", vehicleNumber(0.0))
        assertEquals("40", vehicleNumber(40.0))
    }

    @Test
    fun energyDisplayPreservesMultipleAndUnknownCodesAndDefaultOrigin() {
        fun energy(vararg codes: Int) =
            VehicleEnergy(codes.toList(), VehicleFetchStatus.SUCCESS, VehicleValueOrigin.FETCH)
        assertEquals("전기", energy(10).displayLabel())
        assertEquals("휘발유(무연)", energy(1).displayLabel())
        assertEquals("경유", energy(3, 4).displayLabel())
        assertEquals("휘발유(무연) · 전기 · 알 수 없음(999)", energy(1, 10, 999).displayLabel())
        assertEquals("조회 중", VehicleEnergy().displayLabel())
        assertEquals("확인 불가", VehicleEnergy(status = VehicleFetchStatus.FAILED).displayLabel())
        assertEquals(
            "전기 (기본값)", energy(10).copy(origin = VehicleValueOrigin.DEFAULT).displayLabel()
        )
    }
}
