package eotaek.juyulow.core.data

import eotaek.juyulow.core.model.ComparisonSession
import eotaek.juyulow.core.model.JourneyContext
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.core.model.NaviCandidate
import eotaek.juyulow.core.model.NaviResult
import eotaek.juyulow.core.model.NaviSnapshot
import eotaek.juyulow.core.model.NaviState
import eotaek.juyulow.core.model.NaviStatus
import eotaek.juyulow.core.model.NaviTrip
import eotaek.juyulow.core.model.NavigationSelection
import eotaek.juyulow.core.model.RefuelConditions
import eotaek.juyulow.core.model.Station
import eotaek.juyulow.core.model.confirmsRoute
import eotaek.juyulow.core.model.sameRouteTargetsAs
import eotaek.juyulow.core.model.sameTripOriginAs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield
import java.util.UUID

/** Only candidates whose Trip and route response coordinates were measured belong here. */
data class VerifiedStation(
    val candidate: NaviCandidate,
    val brand: String,
    val priceWonPerL: Int,
    val stationId: String = candidate.key
)

/** One root-owned session; screen cancellation never cancels an SDK request already submitted. */
class LiveJourneyRepository(
    private val navigationFactory: () -> NavigationRepository,
    private val catalog: List<VerifiedStation> = emptyList(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val canInteract: suspend () -> Boolean = { true },
    private val candidateSource: (suspend (NaviSnapshot, RefuelConditions) -> List<VerifiedStation>)? = null,
    private val detours: DetourEtaRepository? = null,
) : JourneyRepository, StationRepository {
    private var searchToken = 0
    private var paused = false
    private var sourceRequest: Deferred<List<VerifiedStation>>? = null
    private var httpJob: Job? = null
    private var stopSearch: (() -> Unit)? = null
    override fun pauseSearch(paused: Boolean) {
        this.paused = paused
    }

    override fun cancelActiveSearch() {
        stopSearch?.invoke(); stopSearch = null; searchToken++; sourceRequest?.cancel(); paused =
            false; httpJob?.cancel()
    }
    private var scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var navigation = navigationFactory()
    private var closed = false
    private val mutex = Mutex()
    private val mutableContext =
        MutableStateFlow(JourneyContext(null, null, null, destinationOnly = true))
    override val context = mutableContext.asStateFlow()
    private val mutableNavigationState = MutableStateFlow(NaviState())
    override val navigationState = mutableNavigationState.asStateFlow()
    private var initialized = false
    private var cached: Pair<NaviSnapshot, List<Station>>? = null
    private var pending: NavigationSelection? = null
    private var pendingExpected: NaviSnapshot? = null
    private var lastSelection: NavigationSelection? = null
    private var activeCandidates = catalog.associate { it.stationId to it.candidate }
    private val tripCache = mutableMapOf<String, Pair<NaviSnapshot, NaviTrip>>()

    private fun <T> NaviResult<T>.value(): T = when (this) {
        is NaviResult.Success -> value
        is NaviResult.Failure -> error(reason)
    }

    private fun publish(snapshot: NaviSnapshot) {
        mutableContext.value = JourneyContext(
            snapshot.destination?.name,
            null,
            null,
            snapshot.waypoints.joinToString(" → ") { it.name }.ifEmpty { null },
            snapshot,
            true,
            cached?.first,
            stationSource = if (candidateSource != null) "오피넷" else null,
        )
    }

    override suspend fun initialize() {
        if (closed) {
            navigation = navigationFactory()
            scope = CoroutineScope(SupervisorJob() + dispatcher)
            initialized = false
            closed = false
        }
        scope.async {
            mutex.withLock {
                if (!initialized) {
                    initialized = true
                    scope.launch {
                        navigation.state.collect {
                            mutableNavigationState.value = it
                            if (it.status == NaviStatus.READY) it.snapshot?.let(::publish)
                        }
                    }
                    publish(navigation.initialize().value())
                }
            }
        }.await()
    }

    override suspend fun refresh() = scope.async {
        mutex.withLock { publish(navigation.refresh().value()) }
    }.await()

    override fun setHasDestination(hasDestination: Boolean) = Unit

    override suspend fun search(journey: JourneyContext): List<Station> = scope.async {
        mutex.withLock {
            check(initialized) { "NAVIGATION_NOT_READY" }
            check(canInteract()) { "PARKING_REQUIRED" }
            val current = navigation.refresh().value()
            publish(current)
            val expected = requireNotNull(journey.navigation) { "NAVIGATION_NOT_READY" }
            check(expected.sameTripOriginAs(current)) { "CONTEXT_CHANGED" }
            cached?.let { (origin, stations) ->
                check(origin.sameTripOriginAs(current)) { "SEARCH_EXPIRED" }
                return@withLock stations
            }
            check(catalog.size == 8 && catalog.map { it.candidate.point.poiId }
                .distinct().size == 8) { "CATALOG_NOT_VERIFIED" }
            val trips = navigation.trips(catalog.map { it.candidate }).value()
            check(trips.size == catalog.size) { "INCOMPLETE_TRIPS" }
            val observedAtMillis = System.currentTimeMillis()
            val stations = catalog.zip(trips).map { (entry, trip) ->
                check(entry.candidate == trip.candidate) { "TRIP_MISMATCH" }
                val p = entry.candidate.point
                Station(
                    p.poiId, p.name, entry.brand, entry.priceWonPerL, p.latitude, p.longitude,
                    trip.meters, trip.seconds, null, null, null, null, null, observedAtMillis
                )
            }
            cached = current to stations
            publish(current)
            stations
        }
    }.await()

    override suspend fun search(
        journey: JourneyContext,
        conditions: RefuelConditions
    ): List<Station> {
        if (candidateSource == null) return search(journey)
        val handle =
            requireNotNull(startSearch(journey, conditions, InMemoryComparisonSessionRepository()))
        val result = handle.state.first { it.complete }
        return result.session?.stations ?: if (result.empty) emptyList() else error(
            result.error ?: "SEARCH_FAILED"
        )
    }

    override fun startSearch(
        journey: JourneyContext,
        conditions: RefuelConditions,
        sessions: ComparisonSessionRepository
    ): SearchHandle? {
        val source = candidateSource ?: return null
        cancelActiveSearch()
        val previousCache = cached
        val previousCandidates = activeCandidates
        val token = searchToken
        val progress = MutableStateFlow(SearchProgress())
        fun active() = token == searchToken && !closed
        stopSearch = { progress.value = progress.value.copy(complete = true, error = "검색 취소") }
        scope.launch {
            var batch: ComparisonSession? = null
            fun emit(next: ComparisonSession) {
                if (!active()) return
                val price =
                    next.stations.filter { it.outboundMeters != null && it.outboundSeconds != null }
                        .maxOfOrNull { it.priceWonPerL } ?: 0
                batch = next.copy(context = next.context.copy(referencePriceWonPerL = price))
                sessions.update(batch)
                progress.value = progress.value.copy(session = batch)
            }

            fun replace(station: Station) {
                batch?.let { emit(it.copy(stations = it.stations.map { old -> if (old.id == station.id) station else old })) }
            }
            try {
                if (!active()) return@launch
                check(initialized) { "NAVIGATION_NOT_READY" }
                check(canInteract()) { "PARKING_REQUIRED" }
                val origin = mutex.withLock { navigation.refresh().value().also(::publish) }
                if (!active()) return@launch
                val entries =
                    scope.async { source(origin, conditions).take(15) }.also { sourceRequest = it }
                        .await()
                if (!active()) return@launch
                if (entries.isEmpty()) {
                    progress.value = SearchProgress(
                        complete = true,
                        error = "주변 주유소가 없어요. 검색 반경을 바꿔 다시 찾아 주세요.",
                        empty = true
                    )
                    return@launch
                }
                val at = System.currentTimeMillis()
                val eligible =
                    detours != null && origin.destination != null && origin.waypoints.isEmpty()
                val initial = entries.mapIndexed { index, e ->
                    Station(
                        e.stationId,
                        e.candidate.point.name,
                        e.brand,
                        e.priceWonPerL,
                        e.candidate.point.latitude,
                        e.candidate.point.longitude,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        at,
                        source = "오피넷",
                        sourceIndex = index,
                        routePending = true,
                        detour = if (eligible) eotaek.juyulow.core.model.DetourEstimate() else null
                    )
                }
                val frozen = journey.copy(
                    destination = origin.destination?.name,
                    navigation = origin,
                    tripOrigin = origin,
                    destinationOnly = true,
                    stationSource = "오피넷",
                    waypoint = origin.waypoints.joinToString { it.name }.ifEmpty { null })
                emit(sessions.create(frozen, conditions, initial).copy(searchComplete = false))
                stopSearch = {
                    progress.value = progress.value.copy(complete = true, error = "검색 취소")
                    batch?.let { b ->
                        emit(b.copy(searchComplete = true, stations = b.stations.map {
                            it.copy(
                                routePending = false,
                                routeError = if (it.routePending) "미조회 · 검색 중단" else it.routeError,
                                detour = if (it.detour?.pending == true) eotaek.juyulow.core.model.DetourEstimate(
                                    false,
                                    error = "경로 확인 불가"
                                ) else it.detour
                            )
                        }))
                    }
                }
                activeCandidates = entries.associate { it.stationId to it.candidate }
                cached = origin to initial
                if (eligible) httpJob = scope.launch {
                    try {
                        val baseline = detours.estimate(origin, conditions)
                        val semaphore = Semaphore(2)
                        var stopped = false
                        coroutineScope {
                            initial.forEach { station ->
                                launch {
                                    semaphore.withPermit {
                                        if (!active() || stopped) return@withPermit
                                        try {
                                            val via = detours.estimate(origin, conditions, station)
                                            val latest =
                                                batch?.stations?.find { it.id == station.id }
                                                    ?: return@withPermit
                                            replace(
                                                latest.copy(
                                                    detour = eotaek.juyulow.core.model.DetourEstimate(
                                                        false,
                                                        via.meters - baseline.meters,
                                                        via.seconds - baseline.seconds,
                                                        via.tollWon - baseline.tollWon,
                                                        assumptions = via.assumptions
                                                    )
                                                )
                                            )
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            ApiLog.event(
                                                "Journey",
                                                "detour",
                                                "failed",
                                                "W"
                                            ) {
                                                mapOf(
                                                    "stationId" to station.id,
                                                    "stopBatch" to (e is KakaoRequestFailure && e.stopBatch)
                                                )
                                            }
                                            if (e is KakaoRequestFailure && e.stopBatch) stopped =
                                                true
                                            batch?.stations?.find { it.id == station.id }?.let {
                                                replace(
                                                    it.copy(
                                                        detour = eotaek.juyulow.core.model.DetourEstimate(
                                                            false,
                                                            error = "경유 비용 확인 불가"
                                                        )
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) { /* Finalize all outstanding estimates below. */
                    } finally {
                        batch?.let { b ->
                            emit(b.copy(stations = b.stations.map {
                                if (it.detour?.pending == true) it.copy(
                                    detour = eotaek.juyulow.core.model.DetourEstimate(
                                        false,
                                        error = "경유 비용 확인 불가"
                                    )
                                ) else it
                            }))
                        }
                    }
                }
                for (entry in entries) {
                    while (active() && paused) delay(25)
                    if (!active()) break
                    val trip = mutex.withLock {
                        if (!active()) return@withLock null
                        check(navigation.state.value.status != NaviStatus.UNCERTAIN) { "TRIP_UNCERTAIN" }
                        check(pending == null) { "ROUTE_UNCERTAIN" }
                        check(canInteract()) { "PARKING_REQUIRED" }
                        val before = navigation.refresh().value().also(::publish)
                        val hit = tripCache[entry.candidate.key]?.takeIf {
                            it.first.sameTripOriginAs(before)
                        }?.second
                        ApiLog.event(
                            "Journey",
                            "trip",
                            if (hit != null) "cache_hit" else "cache_miss"
                        ) {
                            mapOf(
                                "stationId" to entry.stationId,
                                "reason" to if (hit != null) "SAME_ORIGIN_AND_OPTION" else "MISSING_OR_ORIGIN_CHANGED"
                            )
                        }
                        hit ?: when (val result = navigation.trips(listOf(entry.candidate))) {
                            is NaviResult.Success -> result.value.single().also {
                                val after =
                                    it.origin ?: navigation.refresh().value().also(::publish)
                                if (before.sameTripOriginAs(after)) tripCache[entry.candidate.key] =
                                    (it.origin ?: before) to it
                                else tripCache.remove(entry.candidate.key)
                            }

                            is NaviResult.Failure -> null
                        }
                    }
                    if (!active()) break
                    val old = batch!!.stations.first { it.id == entry.stationId }
                    replace(
                        old.copy(
                            outboundMeters = trip?.meters,
                            outboundSeconds = trip?.seconds,
                            routePending = false,
                            routeError = if (trip == null) "경로 확인 불가" else null
                        )
                    )
                    if (trip != null && batch.firstReadyId == null) emit(batch.copy(firstReadyId = entry.stationId))
                    cached = origin to batch.stations
                    if (navigation.state.value.status == NaviStatus.UNCERTAIN) break
                    yield()
                }
                if (active()) {
                    emit(batch!!.copy(searchComplete = true, stations = batch.stations.map {
                        if (it.routePending) it.copy(
                            routePending = false,
                            routeError = "미조회 · 내비 상태 불확실, 앱을 다시 시작해 주세요"
                        ) else it
                    }))
                    if (batch.firstReadyId == null) {
                        cached = previousCache; activeCandidates = previousCandidates
                    }
                    progress.value = progress.value.copy(
                        complete = true,
                        error = if (batch.firstReadyId == null) "경로를 확인하지 못했어요. 내비 상태를 확인해 주세요." else null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                batch?.let {
                    emit(
                        it.copy(
                            searchComplete = true,
                            stations = it.stations.map { s ->
                                if (s.routePending) s.copy(
                                    routePending = false,
                                    routeError = "검색 중단"
                                ) else s
                            })
                    )
                }
                if (active()) progress.value =
                    progress.value.copy(complete = true, error = e.message ?: "검색 중단")
            }
        }
        return SearchHandle(progress.asStateFlow()) { if (active()) cancelActiveSearch() }
    }

    override suspend fun select(
        session: ComparisonSession,
        station: Station,
        mode: JourneyMode
    ): NavigationSelection = scope.async {
        paused = true
        try {
            mutex.withLock {
                check(pending == null && navigation.state.value.status != NaviStatus.UNCERTAIN) { "ROUTE_UNCERTAIN" }
            check(mode in session.context.journey.availableModes && station in session.stations) { "UNSUPPORTED_SELECTION" }
                check(cached?.second?.any { it.id == station.id && it.outboundMeters == station.outboundMeters && it.outboundSeconds == station.outboundSeconds } == true) { "SEARCH_EXPIRED" }
            check(station.outboundMeters != null && station.outboundSeconds != null) { "ROUTE_UNAVAILABLE" }
            val expected = requireNotNull(session.context.journey.navigation)
            val current = navigation.refresh().value()
            publish(current)
            check(same(expected, current)) { "CONTEXT_CHANGED" }
            val candidate = activeCandidates[station.id] ?: error("UNKNOWN_STATION")
            check(canInteract()) { "PARKING_REQUIRED" }
            val selection = NavigationSelection(
                UUID.randomUUID().toString(), station.id, station.name,
                mode,
                if (mode == JourneyMode.WAYPOINT) requireNotNull(expected.destination).name else station.name,
                if (mode == JourneyMode.WAYPOINT) station.name else null,
                requireNotNull(station.outboundMeters),
                requireNotNull(station.outboundSeconds) + session.context.conditions.refuelSeconds,
                session.context.conditions.refuelSeconds,
                session.context.conditions,
                outboundOnly = true,
                referencePriceWonPerL = session.context.referencePriceWonPerL,
                comparison = eotaek.juyulow.core.model.compareStation(
                    session.context,
                    station,
                    JourneyMode.DESTINATION
                ),
                routeWaypoints = if (mode == JourneyMode.WAYPOINT) listOf(station.name) + expected.waypoints.map { it.name } else emptyList()
            )
            pending = selection
            pendingExpected = current
            try {
                val result =
                    navigation.setRoute(current, candidate, mode == JourneyMode.WAYPOINT).value()
                publish(result)
                lastSelection = selection
                pending = null
                cancelActiveSearch()
                selection
            } catch (error: Exception) {
                // Pre-submission failures remain retryable; ambiguous mutations never do.
                if (navigation.state.value.status != NaviStatus.UNCERTAIN) pending = null
                paused = false
                throw error
            }
            }
        } finally {
            paused = false
        }
    }.await()

    override suspend fun reconcile(): NavigationSelection? = scope.async {
        mutex.withLock {
            val snapshot = navigation.refresh().value()
            publish(snapshot)
            pending?.let { selection ->
                val c = activeCandidates[selection.stationId] ?: error("UNKNOWN_STATION")
                if (snapshot.confirmsRoute(
                        requireNotNull(pendingExpected),
                        c,
                        selection.mode == JourneyMode.WAYPOINT
                    )
                ) {
                    lastSelection = selection; pending = null
                    return@withLock selection
                }
            }
            null
        }
    }.await()

    override fun selection(id: String) = lastSelection?.takeIf { it.id == id }
    override suspend fun openNavigation() {
        navigation.openNavigation().value()
    }

    override suspend fun release() {
        cancelActiveSearch(); closed = true; scope.cancel(); navigation.release()
    }

    private fun same(a: NaviSnapshot, b: NaviSnapshot) = a.sameRouteTargetsAs(b)
}
