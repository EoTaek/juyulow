package eotaek.juyulow.core.data

import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.core.model.NaviCandidate
import eotaek.juyulow.core.model.NaviLocation
import eotaek.juyulow.core.model.NaviPoint
import eotaek.juyulow.core.model.NaviResult
import eotaek.juyulow.core.model.NaviSnapshot
import eotaek.juyulow.core.model.NaviState
import eotaek.juyulow.core.model.NaviStatus
import eotaek.juyulow.core.model.NaviTrip
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.core.model.Station
import eotaek.juyulow.core.model.compareStation
import eotaek.juyulow.core.model.resolveRefuelConditions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveJourneyRepositoryTest {
    private val fixtures = (0..7).map { i ->
        VerifiedStation(
            NaviCandidate(
                NaviPoint(37.4 + i * .001, 127.1, "주유소 $i", "poi-$i", "0"),
                37.4 + i * .001, 127.1
            ), "brand", 1740 - i * 20
        )
    }

    private fun snapshot() = NaviSnapshot(
        NaviLocation(37.41, 127.09, "", 0, 1),
        null, emptyList(), "SAFE_GUIDING", false, "RECOMMENDED", 1
    )

    private inner class Navi : NavigationRepository {
        var current = snapshot()
        override val state = MutableStateFlow(NaviState())
        var tripCalls = 0
        val requestedCandidates = mutableListOf<NaviCandidate>()
        var routeCalls = 0
        var releases = 0
        var tripDelay = 0L
        var ambiguous = false
        override suspend fun initialize(): NaviResult<NaviSnapshot> {
            state.value = NaviState(NaviStatus.READY, current)
            return NaviResult.Success(current)
        }

        override suspend fun refresh(): NaviResult<NaviSnapshot> {
            state.value = NaviState(NaviStatus.READY, current)
            return NaviResult.Success(current)
        }

        override suspend fun trips(candidates: List<NaviCandidate>): NaviResult<List<NaviTrip>> {
            requestedCandidates += candidates
            tripCalls++; delay(tripDelay)
            return NaviResult.Success(candidates.mapIndexed { i, c ->
                NaviTrip(
                    c,
                    1000 + i * 100,
                    100 + i * 10,
                    1
                )
            })
        }

        override suspend fun setRoute(
            expected: NaviSnapshot,
            candidate: NaviCandidate,
            asWaypoint: Boolean
        ): NaviResult<NaviSnapshot> {
            routeCalls++
            current = if (asWaypoint) current.copy(
                waypoints = listOf(
                    candidate.point.copy(
                        latitude = 0.0,
                        longitude = 0.0
                    )
                ) + current.waypoints, mode = "ROUTE_GUIDING"
            ) else current.copy(
                destination = candidate.point,
                waypoints = emptyList(),
                mode = "ROUTE_GUIDING"
            )
            state.value =
                NaviState(if (ambiguous) NaviStatus.UNCERTAIN else NaviStatus.READY, current)
            return if (ambiguous) NaviResult.Failure("ROUTE_NOT_CONFIRMED") else NaviResult.Success(
                current
            )
        }

        override suspend fun openNavigation() = NaviResult.Success(Unit)
        override suspend fun release() {
            releases++
        }
    }

    private suspend fun expectFailure(reason: String, action: suspend () -> Unit) {
        try {
            action(); fail("Expected $reason")
        } catch (e: IllegalStateException) {
            assertEquals(reason, e.message)
        }
    }

    private fun batch(repo: LiveJourneyRepository, stations: List<Station>) =
        InMemoryComparisonSessionRepository()
            .create(repo.context.value, resolveRefuelConditions(RefuelSettings()), stations)


    @Test
    fun waypointSlotsAndOutboundComparisonAndRecovery() = runTest {
        val n = Navi()
        val destination = fixtures.last().candidate.point
        n.current = n.current.copy(destination = destination, mode = "ROUTE_GUIDING")
        val repo = LiveJourneyRepository({ n }, fixtures, StandardTestDispatcher(testScheduler))
        try {
            repo.initialize()
            val stations = repo.search(repo.context.value)
            assertEquals(
                listOf(JourneyMode.WAYPOINT, JourneyMode.DESTINATION),
                repo.context.value.availableModes
            )
            val session = batch(repo, stations)
            val comparison = compareStation(session.context, stations[0], JourneyMode.WAYPOINT)
            assertEquals(stations[0].outboundMeters, comparison.costDistanceMeters)
            n.ambiguous = true
            expectFailure("ROUTE_NOT_CONFIRMED") {
                repo.select(
                    session,
                    stations[0],
                    JourneyMode.WAYPOINT
                )
            }
            assertNotNull(repo.reconcile())
            assertEquals(destination, n.current.destination)
            n.ambiguous = false
            assertEquals(listOf(stations[0].id), n.current.waypoints.map { it.poiId })
            assertEquals(listOf(JourneyMode.DESTINATION), repo.context.value.availableModes)
            expectFailure("UNSUPPORTED_SELECTION") {
                repo.select(
                    batch(repo, stations),
                    stations[2],
                    JourneyMode.WAYPOINT
                )
            }
            assertEquals(1, n.routeCalls)
        } finally {
            repo.release()
        }
    }

    @Test
    fun mapsRealTripsAndReusesOnlySameContext() = runTest {
        val n = Navi()
        val repo = LiveJourneyRepository({ n }, fixtures, StandardTestDispatcher(testScheduler))
        try {
            repo.initialize()
            val first = repo.search(repo.context.value)
            assertEquals(8, first.size); assertEquals(1000, first[0].outboundMeters)
            assertNull(first[0].returnMeters); assertNull(first[0].viaMeters); assertNull(first[0].tollWon)
            assertEquals(listOf(JourneyMode.DESTINATION), repo.context.value.availableModes)
            assertEquals(first, repo.search(repo.context.value)); assertEquals(1, n.tripCalls)
            val result =
                compareStation(batch(repo, first).context, first[0], JourneyMode.DESTINATION)
            assertNull(result.tollWon)
            assertEquals(
                result.fuelSavingWon - result.drivingCostWon,
                result.netSavingWon,
                .00001
            )
        } finally {
            repo.release()
        }
    }

    @Test
    fun changedLocationExpiresCachedSearchWithoutSdkRetry() = runTest {
        val n = Navi()
        val repo = LiveJourneyRepository({ n }, fixtures, StandardTestDispatcher(testScheduler))
        try {
            repo.initialize()
            val stations = repo.search(repo.context.value)
            val saved = batch(repo, stations)
            n.current = n.current.copy(location = n.current.location.copy(latitude = 37.5))
            repo.refresh()
            expectFailure("SEARCH_EXPIRED") { repo.search(repo.context.value) }
            n.current = n.current.copy(
                routeOption = "FAST", routing = true,
                location = n.current.location.copy(routeId = "new", routeIndex = 99)
            )
            n.ambiguous = true
            expectFailure("ROUTE_NOT_CONFIRMED") {
                repo.select(saved, stations[0], JourneyMode.DESTINATION)
            }
            assertNotNull(repo.reconcile())
            assertEquals(1, n.tripCalls); assertEquals(1, n.routeCalls)
        } finally {
            repo.release()
        }
    }

    @Test
    fun selectionIsConfirmedOnceAndUnsupportedModesNeverSubmit() = runTest {
        val n = Navi()
        val repo = LiveJourneyRepository({ n }, fixtures, StandardTestDispatcher(testScheduler))
        try {
            repo.initialize()
            val stations = repo.search(repo.context.value)
            val saved = batch(repo, stations)
            expectFailure("UNSUPPORTED_SELECTION") {
                repo.select(
                    saved,
                    stations[0],
                    JourneyMode.WAYPOINT
                )
            }
            val selected = repo.select(saved, stations[0], JourneyMode.DESTINATION)
            assertEquals(selected, repo.selection(selected.id)); assertEquals(
                stations[0].id,
                repo.context.value.navigation?.destination?.poiId
            )
            expectFailure("CONTEXT_CHANGED") {
                repo.select(
                    saved,
                    stations[0],
                    JourneyMode.DESTINATION
                )
            }
            val next = batch(repo, repo.search(repo.context.value))
            repo.select(next, stations[1], JourneyMode.DESTINATION)
            assertEquals(2, n.routeCalls)
        } finally {
            repo.release()
        }
    }

    @Test
    fun ambiguousRouteCanOnlyBeReconciled() = runTest {
        val n = Navi(); n.ambiguous = true
        val repo = LiveJourneyRepository({ n }, fixtures, StandardTestDispatcher(testScheduler))
        try {
            repo.initialize()
            val stations = repo.search(repo.context.value)
            val saved = batch(repo, stations)
            expectFailure("ROUTE_NOT_CONFIRMED") {
                repo.select(
                    saved,
                    stations[0],
                    JourneyMode.DESTINATION
                )
            }
            expectFailure("ROUTE_UNCERTAIN") {
                repo.select(
                    saved,
                    stations[0],
                    JourneyMode.DESTINATION
                )
            }
            assertNotNull(repo.reconcile()); assertEquals(1, n.routeCalls)
        } finally {
            repo.release()
        }
    }

    @Test
    fun cancellingScreenWaiterDoesNotCancelSubmittedTripBatch() = runTest {
        val n = Navi(); n.tripDelay = 500
        val repo = LiveJourneyRepository({ n }, fixtures, StandardTestDispatcher(testScheduler))
        try {
            repo.initialize()
            val waiter = launch { repo.search(repo.context.value) }
            runCurrent(); waiter.cancelAndJoin(); advanceUntilIdle()
            assertEquals(8, repo.search(repo.context.value).size); assertEquals(1, n.tripCalls)
        } finally {
            repo.release()
        }
    }

    @Test
    fun incompleteCatalogCannotCallTrip() = runTest {
        val n = Navi()
        val repo =
            LiveJourneyRepository({ n }, fixtures.take(7), StandardTestDispatcher(testScheduler))
        try {
            repo.initialize(); expectFailure("CATALOG_NOT_VERIFIED") { repo.search(repo.context.value) }
            assertEquals(0, n.tripCalls)
        } finally {
            repo.release()
        }
    }

    @Test
    fun settingAndRemovingSystemDestinationKeepSearchAvailable() = runTest {
        val n = Navi()
        val repo = LiveJourneyRepository({ n }, fixtures, StandardTestDispatcher(testScheduler))
        try {
            repo.initialize()
            val original = n.current
            val stations = repo.search(repo.context.value)
            n.current = original.copy(
                destination = fixtures[6].candidate.point, mode = "ROUTE_GUIDING",
                location = original.location.copy(routeId = "system-route", routeIndex = 5)
            )
            repo.refresh()
            assertEquals(stations, repo.search(repo.context.value))
            assertEquals(fixtures[6].candidate.point.name, repo.context.value.destination)
            n.current = original
            repo.refresh()
            assertEquals(stations, repo.search(repo.context.value))
            assertEquals(1, n.tripCalls)
            n.current = original.copy(location = original.location.copy(latitude = 37.4102))
            repo.refresh()
            assertEquals(stations, repo.search(repo.context.value))
            assertEquals(1, n.tripCalls)
        } finally {
            repo.release()
        }
    }

    @Test
    fun opinetSearchRequestsTripsOnlyForNearestFifteenValidStations() = runTest {
        for (count in listOf(0, 3, 15, 34)) {
            val n = Navi()
            val api = object : OpinetApi {
                override suspend fun around(
                    key: String, x: Double, y: Double, radiusMeters: Int,
                    fuel: String, format: String, sort: Int
                ): ResponseBody {
                    assertEquals(2, sort)
                    // Later (farther) stations are cheaper, but must not displace nearer ones.
                    val rows = (1..count).joinToString(",") { i ->
                        """{"UNI_ID":"id-$i","OS_NM":"station-$i","PRICE":${2000 - i},"GIS_X_COOR":312623.2596234215,"GIS_Y_COOR":548572.9766224765}"""
                    }
                    // Invalid price and out-of-Korea coordinates do not consume valid slots.
                    val invalid = """{"UNI_ID":"invalid","OS_NM":"invalid","PRICE":0,"GIS_X_COOR":312623,"GIS_Y_COOR":548573}"""
                    val invalidCoordinates = invalid.replace("\"invalid\"", "\"invalid-coordinates\"")
                        .replace("\"PRICE\":0", "\"PRICE\":1800")
                        .replace("548573", "1")
                    val allRows = listOf(invalid, invalidCoordinates, rows).filter { it.isNotEmpty() }
                    return """{"RESULT":{"OIL":[${allRows.joinToString(",")}]}}""".toResponseBody()
                }
            }
            val source = OpinetCandidates(OpinetClient(api, "test-key"))
            val repo = LiveJourneyRepository(
                { n }, dispatcher = StandardTestDispatcher(testScheduler),
                candidateSource = source::search
            )
            try {
                repo.initialize()
                val found = repo.search(repo.context.value, resolveRefuelConditions(RefuelSettings()))
                val expectedCount = minOf(count, 15)
                assertEquals((1..expectedCount).map { "id-$it" }, found.map { it.id })
                assertEquals(expectedCount, n.tripCalls)
                assertEquals(
                    (1..expectedCount).map { "station-$it" },
                    n.requestedCandidates.map { it.point.name }
                )
            } finally {
                repo.release()
            }
        }
    }

    @Test
    fun dynamicSearchUsesSnapshotConditionsAndReusesOnlyValidTrips() = runTest {
        val n = Navi()
        val requested = mutableListOf<eotaek.juyulow.core.model.RefuelConditions>()
        var price = 1600
        val coordinate = fixtures.first().candidate.let {
            it.copy(point = it.point.copy(poiId = "", subId = null), coordinateOnly = true)
        }
        val repo = LiveJourneyRepository(
            { n }, dispatcher = StandardTestDispatcher(testScheduler),
            candidateSource = { _, conditions ->
                requested += conditions
                listOf(VerifiedStation(coordinate, "brand", price, "opinet-unique"))
            })
        try {
            repo.initialize()
            val conditions = resolveRefuelConditions(
                RefuelSettings(
                    searchRadiusKm = 5,
                    comparisonFuel = eotaek.juyulow.core.model.ComparisonFuel.DIESEL
                )
            )
            val first = repo.search(repo.context.value, conditions)
            assertEquals("opinet-unique", first.single().id)
            assertEquals("오피넷", first.single().source)
            assertEquals(conditions, requested.single())
            price = 1800
            val second = repo.search(repo.context.value, conditions)
            assertEquals(1800, second.single().priceWonPerL)
            assertEquals(1, n.tripCalls)
            val previousJourney = repo.context.value
            n.current = n.current.copy(location = n.current.location.copy(latitude = 37.415))
            // The results screen may submit its old session without refreshing first.
            repo.search(previousJourney, conditions)
            assertEquals(2, n.tripCalls)
            assertEquals(n.current, repo.context.value.tripOrigin)
        } finally {
            repo.release()
        }
    }

    @Test
    fun dynamicSearchCompletesAfterMovementDuringSourceAndTripsWithoutCachingMixedOrigins() = runTest {
        val n = Navi()
        val requestedOrigins = mutableListOf<NaviSnapshot>()
        var moveDuringSearch = true
        val moving = object : NavigationRepository by n {
            override suspend fun trips(candidates: List<NaviCandidate>): NaviResult<List<NaviTrip>> {
                val result = n.trips(candidates)
                if (moveDuringSearch) {
                    n.current = n.current.copy(location = n.current.location.copy(latitude = 37.43))
                }
                return result
            }
        }
        val repo = LiveJourneyRepository(
            { moving }, dispatcher = StandardTestDispatcher(testScheduler),
            candidateSource = { origin, _ ->
                requestedOrigins += origin
                if (moveDuringSearch) {
                    n.current = n.current.copy(location = n.current.location.copy(latitude = 37.42))
                }
                fixtures.take(2)
            })
        try {
            repo.initialize()
            val journey = repo.context.value
            val conditions = resolveRefuelConditions(RefuelSettings())
            val found = repo.search(journey, conditions)
            assertEquals(2, found.size)
            assertEquals(2, n.tripCalls)
            assertEquals(journey.navigation, requestedOrigins.single())
            assertEquals(37.43, repo.context.value.navigation!!.location.latitude, .000001)

            // Returning to the batch origin must not reuse trips obtained while moving.
            moveDuringSearch = false
            n.current = n.current.copy(location = n.current.location.copy(latitude = 37.42))
            assertEquals(2, repo.search(journey, conditions).size)
            assertEquals(n.current, requestedOrigins.last())
            assertEquals(4, n.tripCalls)
        } finally {
            repo.release()
        }
    }

    @Test
    fun opinetSourceOffersWaypointOnlyWithDestinationAndNoExistingWaypoints() = runTest {
        val n = Navi()
        val destination = fixtures.last().candidate.point
        n.current = n.current.copy(destination = destination, mode = "ROUTE_GUIDING")
        val repo = LiveJourneyRepository(
            { n }, dispatcher = StandardTestDispatcher(testScheduler),
            candidateSource = { _, _ -> fixtures })
        try {
            repo.initialize()
            assertEquals(
                listOf(JourneyMode.WAYPOINT, JourneyMode.DESTINATION),
                repo.context.value.availableModes
            )
            n.current = n.current.copy(waypoints = listOf(fixtures.first().candidate.point))
            repo.refresh()
            assertEquals(listOf(JourneyMode.DESTINATION), repo.context.value.availableModes)
            n.current =
                n.current.copy(destination = null, waypoints = emptyList(), mode = "SAFE_GUIDING")
            repo.refresh()
            assertEquals(listOf(JourneyMode.DESTINATION), repo.context.value.availableModes)
        } finally {
            repo.release()
        }
    }

    @Test
    fun dynamicSearchRetainsPricesWhenRoutesFail() = runTest {
        val base = Navi()
        val failing = object : NavigationRepository by base {
            override suspend fun trips(candidates: List<NaviCandidate>): NaviResult<List<NaviTrip>> =
                NaviResult.Failure("UNAVAILABLE")
        }
        val repo = LiveJourneyRepository(
            { failing }, dispatcher = StandardTestDispatcher(testScheduler),
            candidateSource = { _, _ -> fixtures.take(2) })
        try {
            repo.initialize()
            val conditions = resolveRefuelConditions(RefuelSettings())
            val found = repo.search(repo.context.value, conditions)
            assertEquals(2, found.size)
            assertNull(found.first().outboundMeters)
            assertNotNull(found.first().routeError)
            val session =
                InMemoryComparisonSessionRepository().create(repo.context.value, conditions, found)
            assertEquals(0, session.context.referencePriceWonPerL)
        } finally {
            repo.release()
        }
    }

    @Test
    fun refreshExceptionRestoresOldSelectionOnlyBeforeFirstReadyStation() = runTest {
        for (failAfterFirstReady in listOf(false, true)) {
            val n = Navi()
            var tripRequests = 0
            var searches = 0
            val navigation = object : NavigationRepository by n {
                override suspend fun trips(candidates: List<NaviCandidate>): NaviResult<List<NaviTrip>> {
                    tripRequests++
                    if (tripRequests == if (failAfterFirstReady) 3 else 2) error("CONNECTION_LOST")
                    return n.trips(candidates)
                }
            }
            val repo = LiveJourneyRepository(
                { navigation }, dispatcher = StandardTestDispatcher(testScheduler),
                candidateSource = { _, _ ->
                    if (++searches == 1) fixtures.take(1) else fixtures.drop(1).take(2)
                }
            )
            try {
                repo.initialize()
                val sessions = InMemoryComparisonSessionRepository()
                val conditions = resolveRefuelConditions(RefuelSettings())
                val first = repo.startSearch(repo.context.value, conditions, sessions)!!
                advanceUntilIdle()
                val previous = first.state.value.session!!
                val refresh = repo.startSearch(repo.context.value, conditions, sessions)!!
                advanceUntilIdle()
                assertEquals(true, refresh.state.value.complete)
                assertEquals("CONNECTION_LOST", refresh.state.value.error)
                val failed = refresh.state.value.session!!
                assertEquals(0, failed.stations.count { it.routePending })
                val visible = if (failAfterFirstReady) failed else previous
                assertEquals(if (failAfterFirstReady) fixtures[1].stationId else null, failed.firstReadyId)
                val station = visible.stations.first { it.id == visible.firstReadyId }
                repo.select(visible, station, JourneyMode.DESTINATION)
                assertEquals(station.name, n.current.destination?.name)
                assertEquals(1, n.routeCalls)
            } finally {
                repo.release()
            }
        }
    }

    @Test
    fun progressiveSearchPublishesFirstSuccessBeforeRemainingTrips() = runTest {
        val n = Navi().apply { tripDelay = 100 }
        val sessions = InMemoryComparisonSessionRepository()
        val repo = LiveJourneyRepository(
            { n },
            dispatcher = StandardTestDispatcher(testScheduler),
            candidateSource = { _, _ -> fixtures.take(3) })
        repo.initialize()
        val handle = repo.startSearch(
            repo.context.value,
            resolveRefuelConditions(RefuelSettings()),
            sessions
        )!!
        runCurrent()
        assertEquals(3, handle.state.value.session!!.stations.count { it.routePending })
        advanceTimeBy(100); runCurrent()
        val first = handle.state.value.session!!
        assertEquals(fixtures.first().stationId, first.firstReadyId)
        assertEquals(2, first.stations.count { it.routePending })
        assertEquals(false, handle.state.value.complete)
        advanceUntilIdle()
        assertEquals(first.id, handle.state.value.session!!.id)
        assertEquals(true, handle.state.value.complete)
        assertEquals(3, n.tripCalls)
        repo.release()
    }

    @Test
    fun cancelledGenerationDrainsInflightTripWithoutStartingAnother() = runTest {
        val n = Navi().apply { tripDelay = 100 }
        val repo = LiveJourneyRepository(
            { n },
            dispatcher = StandardTestDispatcher(testScheduler),
            candidateSource = { _, _ -> fixtures.take(3) })
        repo.initialize()
        val h = repo.startSearch(
            repo.context.value,
            resolveRefuelConditions(RefuelSettings()),
            InMemoryComparisonSessionRepository()
        )!!
        runCurrent(); h.cancel(); advanceUntilIdle()
        assertEquals(1, n.tripCalls)
        assertEquals(0, h.state.value.session!!.stations.count { it.routePending })
        assertNull(h.state.value.session!!.firstReadyId)
        repo.release()
    }

    @Test
    fun progressiveSearchSelectsFirstSuccessAfterCandidateFailure() = runTest {
        val n = Navi()
        var calls = 0
        val mixed = object : NavigationRepository by n {
            override suspend fun trips(candidates: List<NaviCandidate>): NaviResult<List<NaviTrip>> {
                calls++
                return if (calls == 1) NaviResult.Failure("NO_ROUTE") else n.trips(candidates)
            }
        }
        val repo = LiveJourneyRepository(
            { mixed },
            dispatcher = StandardTestDispatcher(testScheduler),
            candidateSource = { _, _ -> fixtures.take(2) })
        repo.initialize()
        val h = repo.startSearch(
            repo.context.value,
            resolveRefuelConditions(RefuelSettings()),
            InMemoryComparisonSessionRepository()
        )!!
        advanceUntilIdle()
        assertEquals(fixtures[1].stationId, h.state.value.session!!.firstReadyId)
        assertNotNull(h.state.value.session!!.stations.first().routeError)
        repo.release()
    }

    @Test
    fun detourUsesOneBaselineAndAtMostTwoConcurrentRequests() = runTest {
        val n =
            Navi().apply { current = current.copy(destination = fixtures.last().candidate.point) }
        var calls = 0
        var concurrent = 0
        var maximum = 0
        val detour = object : DetourEtaRepository {
            override suspend fun estimate(
                origin: NaviSnapshot,
                conditions: eotaek.juyulow.core.model.RefuelConditions,
                waypoint: Station?
            ): RouteEstimate {
                calls++; concurrent++; maximum =
                    maxOf(maximum, concurrent); delay(200); concurrent--
                return if (waypoint == null) RouteEstimate(
                    1000,
                    100,
                    100,
                    "test"
                ) else RouteEstimate(900, 90, 0, "test")
            }
        }
        val repo = LiveJourneyRepository(
            { n },
            dispatcher = StandardTestDispatcher(testScheduler),
            candidateSource = { _, _ -> fixtures.take(3) },
            detours = detour
        )
        repo.initialize()
        val h = repo.startSearch(
            repo.context.value,
            resolveRefuelConditions(RefuelSettings()),
            InMemoryComparisonSessionRepository()
        )!!
        advanceUntilIdle()
        assertEquals(4, calls); assertEquals(2, maximum)
        assertEquals(-100, h.state.value.session!!.stations.first().detour!!.meters)
        assertEquals(-10, h.state.value.session!!.stations.first().detour!!.seconds)
        repo.release()
    }

    @Test
    fun uncertainTripStopsBatchAndNeverPublishesFalseSuccess() = runTest {
        val n = Navi()
        var calls = 0
        val uncertain = object : NavigationRepository by n {
            override suspend fun trips(candidates: List<NaviCandidate>): NaviResult<List<NaviTrip>> {
                calls++; n.state.value = n.state.value.copy(status = NaviStatus.UNCERTAIN)
                return NaviResult.Failure("TIMEOUT")
            }
        }
        val repo = LiveJourneyRepository(
            { uncertain },
            dispatcher = StandardTestDispatcher(testScheduler),
            candidateSource = { _, _ -> fixtures.take(3) })
        repo.initialize()
        val h = repo.startSearch(
            repo.context.value,
            resolveRefuelConditions(RefuelSettings()),
            InMemoryComparisonSessionRepository()
        )!!
        advanceUntilIdle()
        assertEquals(1, calls); assertNull(h.state.value.session!!.firstReadyId)
        assertEquals(0, h.state.value.session!!.stations.count { it.routePending })
        assertNotNull(h.state.value.error)
        repo.release()
    }

    @Test
    fun noDestinationMakesZeroDetourCallsAndRouteSubmissionStopsNewTrips() = runTest {
        val n = Navi().apply { tripDelay = 100 }
        var calls = 0
        val detour = object : DetourEtaRepository {
            override suspend fun estimate(
                origin: NaviSnapshot,
                conditions: eotaek.juyulow.core.model.RefuelConditions,
                waypoint: Station?
            ): RouteEstimate {
                calls++; error("not eligible")
            }
        }
        val repo = LiveJourneyRepository(
            { n },
            dispatcher = StandardTestDispatcher(testScheduler),
            candidateSource = { _, _ -> fixtures.take(3) },
            detours = detour
        )
        repo.initialize()
        val h = repo.startSearch(
            repo.context.value,
            resolveRefuelConditions(RefuelSettings()),
            InMemoryComparisonSessionRepository()
        )!!
        runCurrent(); advanceTimeBy(100); runCurrent()
        val batch = h.state.value.session!!
        val submitted =
            launch { repo.select(batch, batch.stations.first(), JourneyMode.DESTINATION) }
        advanceUntilIdle(); submitted.join()
        assertEquals(0, calls); assertEquals(1, n.routeCalls)
        assertEquals(2, n.tripCalls)
        assertEquals(0, h.state.value.session!!.stations.count { it.routePending })
        repo.release()
    }

    @Test
    fun baselineFailureLeavesPleosReadyAndNeverCallsVia() = runTest {
        val n =
            Navi().apply { current = current.copy(destination = fixtures.last().candidate.point) }
        var calls = 0
        val detour = object : DetourEtaRepository {
            override suspend fun estimate(
                origin: NaviSnapshot,
                conditions: eotaek.juyulow.core.model.RefuelConditions,
                waypoint: Station?
            ): RouteEstimate {
                calls++; throw KakaoRequestFailure(true)
            }
        }
        val repo = LiveJourneyRepository(
            { n },
            dispatcher = StandardTestDispatcher(testScheduler),
            candidateSource = { _, _ -> fixtures.take(3) },
            detours = detour
        )
        repo.initialize()
        val h = repo.startSearch(
            repo.context.value,
            resolveRefuelConditions(RefuelSettings()),
            InMemoryComparisonSessionRepository()
        )!!
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(3, h.state.value.session!!.stations.count { it.outboundMeters != null })
        assertEquals(3, h.state.value.session!!.stations.count { it.detour?.error != null })
        repo.release()
    }
}
