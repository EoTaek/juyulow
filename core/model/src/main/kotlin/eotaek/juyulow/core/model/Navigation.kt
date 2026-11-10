package eotaek.juyulow.core.model

data class NaviPoint(val latitude: Double, val longitude: Double, val name: String,
    val poiId: String, val subId: String?, val address: String = "", val provider: String = "")
data class NaviLocation(val latitude: Double, val longitude: Double, val routeId: String,
    val routeIndex: Int?, val observedAtMillis: Long)
data class NaviSnapshot(val location: NaviLocation, val destination: NaviPoint?,
    val waypoints: List<NaviPoint>, val mode: String, val routing: Boolean,
    val routeOption: String, val observedAtMillis: Long)
enum class NaviStatus { IDLE, CONNECTING, READY, READING, FAILED, UNCERTAIN, RELEASED }
data class NaviState(val status: NaviStatus = NaviStatus.IDLE, val snapshot: NaviSnapshot? = null,
    val receivedFields: Set<String> = emptySet(), val error: String? = null)
data class NaviCandidate(val point: NaviPoint, val responseLatitude: Double, val responseLongitude: Double,
    val routeResponseLatitude: Double = responseLatitude, val routeResponseLongitude: Double = responseLongitude)
data class NaviTrip(val candidate: NaviCandidate, val meters: Int, val seconds: Int, val observedAtMillis: Long)
sealed interface NaviResult<out T> {
    data class Success<T>(val value: T) : NaviResult<T>
    data class Failure(val reason: String) : NaviResult<Nothing>
}
