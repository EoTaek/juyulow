package eotaek.juyulow.core.model

data class NaviPoint(val latitude: Double, val longitude: Double, val name: String,
    val poiId: String, val subId: String?, val address: String = "", val provider: String = "")
data class NaviLocation(val latitude: Double, val longitude: Double, val routeId: String,
    val routeIndex: Int?, val observedAtMillis: Long)
data class NaviSnapshot(val location: NaviLocation, val destination: NaviPoint?,
    val waypoints: List<NaviPoint>, val mode: String, val routing: Boolean,
    val routeOption: String, val observedAtMillis: Long)

/** Observation timestamps do not change route identity; position uses the SDK response tolerance. */
fun NaviSnapshot.sameContextAs(other: NaviSnapshot): Boolean = destination == other.destination &&
        waypoints == other.waypoints && mode == other.mode && routing == other.routing &&
        routeOption == other.routeOption && location.routeId == other.location.routeId &&
        location.routeIndex == other.location.routeIndex &&
        kotlin.math.abs(location.latitude - other.location.latitude) <= 0.000001 &&
        kotlin.math.abs(location.longitude - other.location.longitude) <= 0.000001

/** Confirmed route targets survive movement and rerouting; target changes require confirmation. */
fun NaviSnapshot.sameRouteTargetsAs(other: NaviSnapshot): Boolean {
    fun NaviPoint.key() =
        if (poiId.isBlank()) listOf(name, latitude, longitude) else listOf(poiId, subId)
    return destination?.key() == other.destination?.key() &&
            waypoints.map { it.key() } == other.waypoints.map { it.key() }
}

/** Only comparison metrics depend on the original position and routing option. */
fun NaviSnapshot.sameTripOriginAs(other: NaviSnapshot): Boolean {
    if (routeOption != other.routeOption) return false
    val lat1 = Math.toRadians(location.latitude)
    val lat2 = Math.toRadians(other.location.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(other.location.longitude - location.longitude)
    val a = kotlin.math.sin(dLat / 2).let { it * it } +
            kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * kotlin.math.sin(dLon / 2)
        .let { it * it }
    val meters = 6_371_000.0 * 2 * kotlin.math.asin(kotlin.math.sqrt(a.coerceIn(0.0, 1.0)))
    return meters <= 50.0
}
enum class NaviStatus { IDLE, CONNECTING, READY, READING, FAILED, UNCERTAIN, RELEASED }
data class NaviState(val status: NaviStatus = NaviStatus.IDLE, val snapshot: NaviSnapshot? = null,
                     val receivedFields: Set<String> = emptySet(), val error: String? = null,
                     val tripCompleted: Int = 0, val tripTotal: Int = 0
)
data class NaviCandidate(val point: NaviPoint, val responseLatitude: Double, val responseLongitude: Double,
                         val routeResponseLatitude: Double = responseLatitude,
                         val routeResponseLongitude: Double = responseLongitude,
                         val coordinateOnly: Boolean = false
) {
    val key: String get() = if (coordinateOnly) "coordinate:${point.latitude}:${point.longitude}:${point.name}" else point.poiId
}
data class NaviTrip(val candidate: NaviCandidate, val meters: Int, val seconds: Int, val observedAtMillis: Long)
sealed interface NaviResult<out T> {
    data class Success<T>(val value: T) : NaviResult<T>
    data class Failure(val reason: String) : NaviResult<Nothing>
}

/** Missing waypoint coordinates alone do not invalidate the observed route. */
fun NaviSnapshot.confirmsRoute(
    expected: NaviSnapshot,
    candidate: NaviCandidate,
    asWaypoint: Boolean
): Boolean {
    val wanted = if (asWaypoint) listOf(candidate.point) + expected.waypoints else emptyList()
    fun near(a: Double, b: Double) = kotlin.math.abs(a - b) <= 0.000001
    fun matches(actual: NaviPoint, target: NaviPoint, candidateTarget: Boolean): Boolean {
        val identity = if (candidateTarget && candidate.coordinateOnly)
            actual.name == target.name
        else actual.poiId == target.poiId
        if (!identity) return false
        // Missing coordinates are tolerated only for identified waypoints.
        if (asWaypoint && actual.latitude == 0.0 && actual.longitude == 0.0)
            return target.poiId.isNotBlank()
        val lat = if (candidateTarget) candidate.routeResponseLatitude else target.latitude
        val lon = if (candidateTarget) candidate.routeResponseLongitude else target.longitude
        return near(actual.latitude, lat) && near(actual.longitude, lon)
    }
    return destination != null &&
            (if (asWaypoint) destination == expected.destination else matches(
                destination,
                candidate.point,
                true
            )) &&
            waypoints.size == wanted.size &&
            waypoints.zip(wanted).withIndex().all { (index, pair) ->
                matches(pair.first, pair.second, asWaypoint && index == 0)
            } &&
            routeOption == expected.routeOption && mode in listOf(
        "ROUTE_GUIDING",
        "SIMULATION_GUIDING"
    )
}
