package eotaek.juyulow.navigation

import ai.pleos.playground.navi.constants.NaviErrorCode
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
import androidx.annotation.MainThread

/**
 * One explicitly owned NaviHelper session. Call lifecycle and request methods on the main thread.
 * Requests return after submission, not after navigation has completed. Results use [NaviClientListener].
 */
@MainThread
interface NaviClient {
    /** Initialize once. A failed or released client must be replaced with a new instance. */
    fun initialize(listener: NaviClientListener)
    fun getCurrentLocationInfo()
    fun getDestinationInfo()
    fun getWaypointInfo()
    fun getRouteStateInfo()
    fun requestTrip(request: TripRequest)
    fun requestRoute(route: RouteInfo)
    fun addWaypoint(waypoint: RequestWaypointInfo)
    fun requestNavigationScreen(screen: NavigationScreenRequest)
    fun release()
}

/**
 * Callbacks run on the SDK callback thread; marshal UI work to the main thread as needed.
 * Only the information, trip, route, waypoint and error callbacks listed here are forwarded.
 * Callbacks must return promptly and must not throw.
 */
interface NaviClientListener {
    fun onConnected() = Unit
    fun onConnectionFailed(cause: Throwable) = Unit
    fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) = Unit
    fun onDestinationInfo(destinationInfo: DestinationInfo) = Unit
    fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) = Unit
    fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = Unit

    /** Raw SDK response: no request ID, candidate matching or freshness guarantee is added. */
    fun onTripInfo(tripInfo: TripInfo) = Unit
    fun onRouteStarted(routeStartInfo: RouteStartInfo) = Unit
    fun onRoutingCompleted() = Unit
    fun onRouteCancelled() = Unit
    fun onDestinationChanged() = Unit
    fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) = Unit
    fun onError(errorCode: NaviErrorCode) = Unit
}
