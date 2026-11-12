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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
) : JourneyRepository, StationRepository {
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
        val source = candidateSource ?: return search(journey)
        // Network work belongs to the caller; no orphan request survives a cancelled screen.
        check(initialized) { "NAVIGATION_NOT_READY" }
        check(canInteract()) { "PARKING_REQUIRED" }
        val origin = mutex.withLock { navigation.refresh().value().also(::publish) }
        check(requireNotNull(journey.navigation).sameTripOriginAs(origin)) { "CONTEXT_CHANGED" }
        val entries = source(origin, conditions)
        val fetchedAt = System.currentTimeMillis()
        return mutex.withLock {
            check(pending == null) { "ROUTE_UNCERTAIN" }
            val current = navigation.refresh().value()
            check(origin.sameTripOriginAs(current)) { "CONTEXT_CHANGED" }
            val candidates = entries.associate { it.stationId to it.candidate }
            val stations = entries.mapIndexed { index, entry ->
                check(canInteract()) { "PARKING_REQUIRED" }
                val candidate = entry.candidate
                val cachedTrip =
                    tripCache[candidate.key]?.takeIf { it.first.sameTripOriginAs(current) }?.second
                val trip = cachedTrip ?: when (val result = navigation.trips(listOf(candidate))) {
                    is NaviResult.Success -> result.value.single().also {
                        tripCache[candidate.key] = current to it
                    }

                    is NaviResult.Failure -> null
                }
                mutableNavigationState.value = mutableNavigationState.value.copy(
                    tripCompleted = index + 1, tripTotal = entries.size
                )
                Station(
                    entry.stationId,
                    candidate.point.name,
                    entry.brand,
                    entry.priceWonPerL,
                    candidate.point.latitude,
                    candidate.point.longitude,
                    trip?.meters,
                    trip?.seconds,
                    null,
                    null,
                    null,
                    null,
                    null,
                    fetchedAt,
                    routeError = if (trip == null) "경로를 확인하지 못했어요. 다시 검색해 주세요." else null,
                    source = "오피넷"
                )
            }
            val after = navigation.refresh().value()
            check(current.sameTripOriginAs(after)) { "CONTEXT_CHANGED" }
            activeCandidates = candidates
            cached = current to stations
            publish(after)
            stations
        }
    }

    override suspend fun select(
        session: ComparisonSession,
        station: Station,
        mode: JourneyMode
    ): NavigationSelection = scope.async {
        mutex.withLock {
            check(pending == null) { "ROUTE_UNCERTAIN" }
            check(mode in session.context.journey.availableModes && station in session.stations) { "UNSUPPORTED_SELECTION" }
            check(cached?.second?.contains(station) == true) { "SEARCH_EXPIRED" }
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
                selection
            } catch (error: Exception) {
                // Pre-submission failures remain retryable; ambiguous mutations never do.
                if (navigation.state.value.status != NaviStatus.UNCERTAIN) pending = null
                throw error
            }
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
        closed = true; scope.cancel(); navigation.release()
    }

    private fun same(a: NaviSnapshot, b: NaviSnapshot) = a.sameRouteTargetsAs(b)
}
