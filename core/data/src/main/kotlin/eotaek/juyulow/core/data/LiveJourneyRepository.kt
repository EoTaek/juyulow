package eotaek.juyulow.core.data

import eotaek.juyulow.core.model.ComparisonSession
import eotaek.juyulow.core.model.JourneyContext
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.core.model.NaviCandidate
import eotaek.juyulow.core.model.NaviResult
import eotaek.juyulow.core.model.NaviSnapshot
import eotaek.juyulow.core.model.NaviState
import eotaek.juyulow.core.model.NaviStatus
import eotaek.juyulow.core.model.NavigationSelection
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
data class VerifiedStation(val candidate: NaviCandidate, val brand: String, val priceWonPerL: Int)

/** One root-owned session; screen cancellation never cancels an SDK request already submitted. */
class LiveJourneyRepository(
    private val navigationFactory: () -> NavigationRepository,
    private val catalog: List<VerifiedStation>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val canInteract: suspend () -> Boolean = { true },
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
            cached?.first
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

    override suspend fun select(
        session: ComparisonSession,
        station: Station,
        mode: JourneyMode
    ): NavigationSelection = scope.async {
        mutex.withLock {
            check(pending == null) { "ROUTE_UNCERTAIN" }
            check(mode in session.context.journey.availableModes && station in session.stations) { "UNSUPPORTED_SELECTION" }
            check(cached?.second?.contains(station) == true) { "SEARCH_EXPIRED" }
            val expected = requireNotNull(session.context.journey.navigation)
            val current = navigation.refresh().value()
            publish(current)
            check(same(expected, current)) { "CONTEXT_CHANGED" }
            val candidate = catalog.single { it.candidate.point.poiId == station.id }.candidate
            check(canInteract()) { "PARKING_REQUIRED" }
            val selection = NavigationSelection(
                UUID.randomUUID().toString(), station.id, station.name,
                mode,
                if (mode == JourneyMode.WAYPOINT) requireNotNull(expected.destination).name else station.name,
                if (mode == JourneyMode.WAYPOINT) station.name else null,
                station.outboundMeters,
                station.outboundSeconds + session.context.conditions.refuelSeconds,
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
                val c = catalog.single { it.candidate.point.poiId == selection.stationId }.candidate
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
