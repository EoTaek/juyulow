package eotaek.juyulow.navigation

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.RoutePointType
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import eotaek.juyulow.core.data.NavigationRepository
import eotaek.juyulow.core.model.NaviCandidate
import eotaek.juyulow.core.model.NaviLocation
import eotaek.juyulow.core.model.NaviPoint
import eotaek.juyulow.core.model.NaviResult
import eotaek.juyulow.core.model.NaviSnapshot
import eotaek.juyulow.core.model.NaviState
import eotaek.juyulow.core.model.NaviStatus
import eotaek.juyulow.core.model.NaviTrip
import eotaek.juyulow.core.model.confirmsRoute
import eotaek.juyulow.core.model.sameRouteTargetsAs
import eotaek.juyulow.core.model.sameTripOriginAs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/** Process-wide ledger deliberately survives client replacement: reconnect is not cancellation. */
class NaviTripLedger {
    internal val requested = mutableSetOf<String>()
    internal var uncertain = false
}

class PleosNavigationRepository(
    private val client: NaviClient,
    private val ledger: NaviTripLedger,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val readTimeout: Long = 10_000,
    private val operationTimeout: Long = 30_000,
) : NavigationRepository {
    private val mutableState = MutableStateFlow(NaviState())
    override val state = mutableState.asStateFlow()
    private val mutex = Mutex()
    private val events = Channel<Any>(Channel.UNLIMITED)
    private var connected = false
    private var closed = false
    private var readUncertain = false
    private var routeUncertain = false
    private var pendingRoute: ((NaviSnapshot) -> Boolean)? = null
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var refreshJob: Job? = null
    private fun changed() {
        events.trySend(Changed)
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(100.milliseconds)
            if (!mutex.isLocked && connected && !closed && !readUncertain) refresh()
        }
    }
    private object Connected
    private object Changed
    private data class Failed(val reason: String)
    private val listener = object : NaviClientListener {
        override fun onConnected() { events.trySend(Connected) }
        override fun onConnectionFailed(cause: Throwable) { events.trySend(Failed(cause.toString())) }
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) { events.trySend(currentLocationInfo) }
        override fun onDestinationInfo(destinationInfo: DestinationInfo) { events.trySend(destinationInfo) }
        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) { events.trySend(waypointInfoList.toList()) }
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) { events.trySend(routeStateInfo) }
        override fun onTripInfo(tripInfo: TripInfo) { events.trySend(tripInfo) }
        override fun onRoutingCompleted() { scope.launch { changed() } }
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) { scope.launch { changed() } }
        override fun onDestinationChanged() { scope.launch { changed() } }
        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) { scope.launch { changed() } }
        override fun onRouteCancelled() { scope.launch { changed() } }
        override fun onError(errorCode: NaviErrorCode) { events.trySend(Failed(errorCode.toString())) }
    }
    private fun fail(reason: String): Nothing = throw IllegalStateException(reason)
    private suspend fun next(): Any = events.receive().also { if (it is Failed) fail(it.reason) }
    private suspend fun <T> operation(block: suspend () -> T): NaviResult<T> = withContext(dispatcher) {
        if (!mutex.tryLock()) return@withContext NaviResult.Failure("BUSY")
        try {
            check(!closed) { "RELEASED" }
            NaviResult.Success(block())
        } catch (e: CancellationException) {
            mutableState.value = state.value.copy(status = if (closed) NaviStatus.RELEASED else NaviStatus.UNCERTAIN, error = "CANCELLED")
            if (e is TimeoutCancellationException) NaviResult.Failure("TIMEOUT") else throw e
        } catch (e: Exception) {
            mutableState.value = state.value.copy(status = when {
                closed -> NaviStatus.RELEASED
                readUncertain || routeUncertain || ledger.uncertain -> NaviStatus.UNCERTAIN
                else -> NaviStatus.FAILED
            }, error = e.message)
            NaviResult.Failure(e.message ?: "FAILED")
        } finally { mutex.unlock() }
    }
    override suspend fun initialize() = operation {
        check(state.value.status == NaviStatus.IDLE) { "ALREADY_INITIALIZED" }
        mutableState.value = NaviState(NaviStatus.CONNECTING)
        withTimeout(readTimeout.milliseconds) {
            client.initialize(listener)
            check(next() === Connected) { "CONNECTION_FAILED" }
            connected = true
        }
        read()
    }
    override suspend fun refresh() = operation { read() }
    private suspend fun read(): NaviSnapshot {
        check(connected && !readUncertain) { "READ_UNAVAILABLE" }
        mutableState.value = state.value.copy(status = NaviStatus.READING, receivedFields = emptySet(), error = null)
        var location: CurrentLocationInfo? = null
        var destination: DestinationInfo? = null
        var waypoints: List<WaypointInfo>? = null
        var route: RouteStateInfo? = null
        val fields = mutableSetOf<String>()
        try {
            // Drain notifications before requesting. This cannot guarantee server-side attribution.
            while (true) {
                val queued = events.tryReceive().getOrNull() ?: break
                if (queued is TripInfo) ledger.uncertain = true
            }
            client.getCurrentLocationInfo(); client.getDestinationInfo(); client.getWaypointInfo(); client.getRouteStateInfo()
            withTimeout(readTimeout.milliseconds) {
                while (fields.size < 4) {
                    when (val e = next()) {
                        is CurrentLocationInfo -> { location = e; fields += "location" }
                        is DestinationInfo -> { destination = e; fields += "destination" }
                        is RouteStateInfo -> { route = e; fields += "route" }
                        is List<*> -> { @Suppress("UNCHECKED_CAST")
                            waypoints = e as List<WaypointInfo>; fields += "waypoints" }
                    }
                    mutableState.value = state.value.copy(receivedFields = fields.toSet())
                }
            }
        } catch (e: Exception) { readUncertain = true; throw e }
        val l = location!!; val r = route!!; val d = destination!!
        check(r.naviMode != NaviMode.NOT_WORKING) { "NAVIGATION_UNAVAILABLE" }
        check(valid(l.latitude, l.longitude)) { "INVALID_LOCATION" }
        val dest = if (r.naviMode == NaviMode.SAFE_GUIDING && d.poiId.isBlank() && d.name.isBlank()) null
        else d.point().also {
            check(
                valid(
                    it.latitude,
                    it.longitude
                ) && it.name.isNotBlank()
            ) { "UNKNOWN_DESTINATION" }
        }
        val result = NaviSnapshot(NaviLocation(l.latitude, l.longitude, l.routeId, l.currentRouteIndex, clock()),
            dest, waypoints!!.map { it.point() }, r.naviMode.name, r.isRouting, r.routeOption.name, clock())
        if (pendingRoute?.invoke(result) == true) { routeUncertain = false; pendingRoute = null }
        mutableState.value = NaviState(
            if (routeUncertain || ledger.uncertain) NaviStatus.UNCERTAIN else NaviStatus.READY,
            result,
            fields.toSet()
        )
        return result
    }
    override suspend fun trips(candidates: List<NaviCandidate>) = operation {
        check(!ledger.uncertain) { "TRIP_UNCERTAIN" }
        check(candidates.isNotEmpty() && candidates.map { it.key }
            .distinct().size == candidates.size) { "INVALID_CANDIDATES" }
        candidates.forEach { validate(it); check(it.coordinateOnly || it.key !in ledger.requested) { "REPEAT_CANDIDATE" } }
        val before = read()
        check(!ledger.uncertain) { "TRIP_UNCERTAIN" }
        mutableState.value = state.value.copy(tripCompleted = 0, tripTotal = candidates.size)
        try {
            val result = candidates.map { c ->
                ledger.requested += c.key
                val requestName =
                    if (c.coordinateOnly) c.point.name + " #" + java.util.UUID.randomUUID() else c.point.name
                client.requestTrip(
                    TripRequest(
                        c.point.latitude,
                        c.point.longitude,
                        c.point.poiId,
                        requestName,
                        c.point.subId
                    )
                )
                withTimeout(operationTimeout.milliseconds) {
                    var trip: TripInfo? = null
                    while (trip == null) {
                        val event = next()
                        if (event is TripInfo && (!c.coordinateOnly ||
                                    event.summary.routePoints.singleOrNull()?.poiName == requestName)
                        ) trip = event
                    }
                    val points = trip.summary.routePoints
                    check(
                        points.size == 1 && points.single().pointType == RoutePointType.DESTINATION && (if (c.coordinateOnly)
                            points.single().poiName == requestName && distanceMeters(
                                points.single().latitude,
                                points.single().longitude,
                                c.point.latitude,
                                c.point.longitude
                            ) <= 150.0
                        else points.single().poiId == c.point.poiId &&
                                near(
                                    points.single().latitude,
                                    c.responseLatitude
                                ) && near(points.single().longitude, c.responseLongitude))
                    ) { "TRIP_MISMATCH" }
                    check(trip.distance >= 0 && trip.eta >= 0) { "INVALID_TRIP" }
                    mutableState.value =
                        state.value.copy(tripCompleted = state.value.tripCompleted + 1)
                    NaviTrip(c, trip.distance, trip.eta, clock(), before)
                }
            }
            check(before.sameTripOriginAs(read())) { "CONTEXT_CHANGED" }
            result
        } catch (e: Exception) { ledger.uncertain = true; throw e }
    }
    override suspend fun setRoute(expected: NaviSnapshot, candidate: NaviCandidate, asWaypoint: Boolean) = operation {
        check(!routeUncertain) { "ROUTE_UNCERTAIN" }
        validate(candidate)
        val current = read()
        check(expected.sameRouteTargetsAs(current)) { "CONTEXT_CHANGED" }
        val point = candidate.point
        if (asWaypoint) {
            check(current.destination != null && current.waypoints.isEmpty()) { "WAYPOINT_UNAVAILABLE" }
            check((current.waypoints + current.destination!!).none {
                if (candidate.coordinateOnly) distanceMeters(
                    it.latitude,
                    it.longitude,
                    point.latitude,
                    point.longitude
                ) < 1.0 else it.poiId == point.poiId
            }) { "DUPLICATE_POINT" }
        }
        val destination = if (asWaypoint) current.destination!! else point
        pendingRoute = { actual -> actual.confirmsRoute(current, candidate, asWaypoint) }
        routeUncertain = true
        if (asWaypoint) client.addWaypoint(
            RequestWaypointInfo(
                latitude = point.latitude,
                longitude = point.longitude,
                poiName = point.name,
                waypointIndex = WaypointIndex.FIRST,
                poiId = point.poiId,
                poiSubId = point.subId ?: "",
            address=point.address, poiProvider=point.provider))
        else client.requestRoute(destination.route(current.routeOption))
        val deadline = clock() + operationTimeout
        withTimeout(operationTimeout.milliseconds) { while (next() !== Changed) { /* Await route event. */ } }
        var actual = read()
        while (routeUncertain) {
            check(clock() < deadline) { "ROUTE_NOT_CONFIRMED" }
            delay(250.milliseconds)
            actual = read()
        }
        actual
    }
    override suspend fun openNavigation() = operation { check(connected); client.requestNavigationScreen(NavigationScreenRequest.Foreground) }
    override suspend fun release() = withContext(dispatcher) {
        if (!closed) { closed = true; connected = false; scope.cancel(); events.close(); try { client.release() }
            finally { mutableState.value = state.value.copy(status = NaviStatus.RELEASED) } }
    }
    private fun validate(c: NaviCandidate) {
        check(
            (c.coordinateOnly && c.point.poiId.isBlank() || c.point.poiId.isNotBlank() && c.point.subId != null) && valid(
                c.point.latitude,
                c.point.longitude
            ) &&
            valid(c.responseLatitude, c.responseLongitude) && valid(c.routeResponseLatitude, c.routeResponseLongitude)) { "UNVERIFIED_POINT" }
    }

    private fun distanceMeters(
        lat: Double,
        lon: Double,
        targetLat: Double,
        targetLon: Double
    ): Double {
        if (!valid(lat, lon)) return Double.POSITIVE_INFINITY
        val a = Math.toRadians(lat)
        val b = Math.toRadians(targetLat)
        val dLat = b - a
        val dLon = Math.toRadians(targetLon - lon)
        val h = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(a) * kotlin.math.cos(b) * kotlin.math.sin(dLon / 2).let { it * it }
        return 6371000.0 * 2 * kotlin.math.asin(kotlin.math.sqrt(h.coerceIn(0.0, 1.0)))
    }
    private fun near(a: Double, b: Double) = abs(a - b) <= 0.000001
    private fun valid(lat: Double, lon: Double) = lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
    private fun DestinationInfo.point() = NaviPoint(latitude, longitude, name, poiId, poiSubId, address, poiProvider)
    private fun WaypointInfo.point() = NaviPoint(latitude, longitude, name, poiId, poiSubId, address, poiProvider)
    private fun NaviPoint.route(option: String) = RouteInfo(latitude = latitude, longitude = longitude, poiName = name,
        poiId = poiId,
        poiSubId = subId ?: if (poiId.isBlank()) "" else fail("MISSING_SUB_ID"),
        address = address,
        poiProvider = provider,
        routeOption = RouteOption.valueOf(option)
    )
}
