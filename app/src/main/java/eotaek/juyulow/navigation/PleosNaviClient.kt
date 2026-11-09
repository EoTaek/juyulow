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
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context

/** Low-level adapter; does not correlate callbacks, retry requests or change routes automatically. */
class PleosNaviClient internal constructor(private val api: NaviHelperApi) : NaviClient {
    constructor(context: Context) : this(AndroidNaviHelperApi(context))

    private enum class State { NEW, CONNECTING, CONNECTED, FAILED, RELEASED }

    private val lock = Any()
    private var state = State.NEW
    private var listener: NaviClientListener? = null
    private var listenerRegistered = false

    private val sdkListener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) =
            deliver { onCurrentLocationInfo(currentLocationInfo) }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) =
            deliver { onDestinationInfo(destinationInfo) }

        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) =
            deliver { onWayPointInfo(waypointInfoList) }

        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) =
            deliver { onRouteStateInfo(routeStateInfo) }

        override fun onTripInfo(tripInfo: TripInfo) = deliver { onTripInfo(tripInfo) }
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) =
            deliver { onRouteStarted(routeStartInfo) }

        override fun onRoutingCompleted() = deliver { onRoutingCompleted() }
        override fun onRouteCancelled() = deliver { onRouteCancelled() }
        override fun onDestinationChanged() = deliver { onDestinationChanged() }
        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) =
            deliver { onWaypointChanged(waypointChangedInfo) }

        override fun onError(errorCode: NaviErrorCode) = deliver { onError(errorCode) }
    }

    override fun initialize(listener: NaviClientListener) {
        synchronized(lock) {
            check(state == State.NEW) { "NaviClient can only be initialized once; create a new client." }
            this.listener = listener
            state = State.CONNECTING
        }
        try {
            api.initialize(onConnected = ::connected, onFailed = ::failed)
        } catch (cause: Exception) {
            failed(cause)
        }
    }

    private fun connected() {
        val target = synchronized(lock) {
            if (state != State.CONNECTING) return
            try {
                // Remember attempted registration so release also cleans up a partially failed add.
                listenerRegistered = true
                api.addListener(sdkListener)
                state = State.CONNECTED
                listener
            } catch (cause: Exception) {
                failed(cause)
                return
            }
        }
        target?.onConnected()
    }

    private fun failed(cause: Throwable) {
        val target = synchronized(lock) {
            if (state != State.CONNECTING) return
            state = State.FAILED
            listener
        }
        target?.onConnectionFailed(cause)
    }

    private inline fun deliver(block: NaviClientListener.() -> Unit) {
        val target = synchronized(lock) {
            if (state == State.CONNECTED) listener else null
        }
        target?.block()
    }

    private inline fun request(block: NaviHelperApi.() -> Unit) {
        synchronized(lock) {
            check(state == State.CONNECTED) { "NaviClient is not connected: $state" }
            api.block()
        }
    }

    override fun getCurrentLocationInfo() = request { getCurrentLocationInfo() }
    override fun getDestinationInfo() = request { getDestinationInfo() }
    override fun getWaypointInfo() = request { getWaypointInfo() }
    override fun getRouteStateInfo() = request { getRouteStateInfo() }
    override fun requestTrip(request: TripRequest) = request { requestTrip(request) }
    override fun requestRoute(route: RouteInfo) = request { requestRoute(route) }
    override fun addWaypoint(waypoint: RequestWaypointInfo) = request { addWaypoint(waypoint) }
    override fun requestNavigationScreen(screen: NavigationScreenRequest) =
        request { requestNavigationScreen(screen) }

    override fun release() {
        synchronized(lock) {
            if (state == State.RELEASED) return
            state = State.RELEASED
            listener = null
            try {
                if (listenerRegistered) api.removeListener(sdkListener)
            } finally {
                listenerRegistered = false
                api.release()
            }
        }
    }
}
