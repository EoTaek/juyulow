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
import eotaek.juyulow.core.data.ApiLog
import eotaek.juyulow.sdkFields

/** Low-level adapter; does not correlate callbacks, retry requests or change routes automatically. */
class PleosNaviClient internal constructor(private val api: NaviHelperApi) : NaviClient {
    constructor(context: Context) : this(AndroidNaviHelperApi(context))

    private enum class State { NEW, CONNECTING, CONNECTED, FAILED, RELEASED }

    private val clientId = ApiLog.id()
    private val lock = Any()
    private var state = State.NEW
    private var listener: NaviClientListener? = null
    private var listenerRegistered = false

    private val sdkListener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) =
            deliver {
                ApiLog.event(
                    "Navi",
                    "onCurrentLocationInfo",
                    "callback"
                ) {
                    mapOf(
                        "clientId" to clientId,
                        "currentLocationInfo" to sdkFields(currentLocationInfo)
                    )
                }
                onCurrentLocationInfo(currentLocationInfo)
            }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) =
            deliver {
                ApiLog.event(
                    "Navi",
                    "onDestinationInfo",
                    "callback"
                ) { mapOf("clientId" to clientId, "destinationInfo" to sdkFields(destinationInfo)) }
                onDestinationInfo(destinationInfo)
            }

        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) =
            deliver {
                ApiLog.event("Navi", "onWayPointInfo", "callback") {
                    mapOf(
                        "clientId" to clientId,
                        "waypointInfoList" to sdkFields(waypointInfoList)
                    )
                }
                onWayPointInfo(waypointInfoList)
            }

        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) =
            deliver {
                ApiLog.event("Navi", "onRouteStateInfo", "callback") {
                    mapOf(
                        "clientId" to clientId,
                        "routeStateInfo" to sdkFields(routeStateInfo)
                    )
                }
                onRouteStateInfo(routeStateInfo)
            }

        override fun onTripInfo(tripInfo: TripInfo) = deliver {
            ApiLog.event("Navi", "onTripInfo", "callback") {
                mapOf(
                    "clientId" to clientId,
                    "tripInfo" to sdkFields(tripInfo)
                )
            }
            onTripInfo(tripInfo)
        }
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) =
            deliver {
                ApiLog.event("Navi", "onRouteStarted", "callback") {
                    mapOf(
                        "clientId" to clientId,
                        "routeStartInfo" to sdkFields(routeStartInfo)
                    )
                }
                onRouteStarted(routeStartInfo)
            }

        override fun onRoutingCompleted() = deliver {
            ApiLog.event(
                "Navi",
                "onRoutingCompleted",
                "callback"
            ) { mapOf("clientId" to clientId) }; onRoutingCompleted()
        }

        override fun onRouteCancelled() = deliver {
            ApiLog.event(
                "Navi",
                "onRouteCancelled",
                "callback"
            ) { mapOf("clientId" to clientId) }; onRouteCancelled()
        }

        override fun onDestinationChanged() = deliver {
            ApiLog.event(
                "Navi",
                "onDestinationChanged",
                "callback"
            ) { mapOf("clientId" to clientId) }; onDestinationChanged()
        }
        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) =
            deliver {
                ApiLog.event(
                    "Navi",
                    "onWaypointChanged",
                    "callback"
                ) {
                    mapOf(
                        "clientId" to clientId,
                        "waypointChangedInfo" to sdkFields(waypointChangedInfo)
                    )
                }
                onWaypointChanged(waypointChangedInfo)
            }

        override fun onError(errorCode: NaviErrorCode) = deliver {
            ApiLog.event("Navi", "onError", "callback") {
                mapOf(
                    "clientId" to clientId,
                    "errorCode" to sdkFields(errorCode)
                )
            }
            onError(errorCode)
        }
    }

    override fun initialize(listener: NaviClientListener) {
        ApiLog.event("Navi", "initialize", "start", "I") { mapOf("clientId" to clientId) }
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
                ApiLog.call(
                    "Navi",
                    "addListener",
                    { mapOf("clientId" to clientId) }) { api.addListener(sdkListener) }
                state = State.CONNECTED
                listener
            } catch (cause: Exception) {
                failed(cause)
                return
            }
        }
        ApiLog.event("Navi", "initialize", "connected", "I")
        target?.onConnected()
    }

    private fun failed(cause: Throwable) {
        ApiLog.error("Navi", "initialize", cause)
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
        if (target == null) ApiLog.event(
            "Navi",
            "deliver",
            "ignored_callback"
        ) { mapOf("clientId" to clientId, "state" to state.name) }
        target?.block()
    }

    private inline fun request(operation: String, block: NaviHelperApi.() -> Unit) {
        ApiLog.call("Navi", operation, { mapOf("clientId" to clientId) }) {
            synchronized(lock) {
                check(state == State.CONNECTED) { "NaviClient is not connected: $state" }
                api.block()
            }
        }
    }

    override fun getCurrentLocationInfo() =
        request("getCurrentLocationInfo") { getCurrentLocationInfo() }

    override fun getDestinationInfo() = request("getDestinationInfo") { getDestinationInfo() }
    override fun getWaypointInfo() = request("getWaypointInfo") { getWaypointInfo() }
    override fun getRouteStateInfo() = request("getRouteStateInfo") { getRouteStateInfo() }
    override fun requestTrip(request: TripRequest) = request("requestTrip") {
        ApiLog.event("Navi", "requestTrip", "arguments") {
            mapOf(
                "clientId" to clientId,
                "value" to sdkFields(request)
            )
        }
        requestTrip(request)
    }

    override fun requestRouteWithWaypoints(request: ai.pleos.playground.navi.data.RouteWithWaypointsRequest) =
        request("requestRouteWithWaypoints") {
            ApiLog.event(
                "Navi",
                "requestRouteWithWaypoints",
                "arguments"
            ) { mapOf("clientId" to clientId, "value" to sdkFields(request)) }
            requestRouteWithWaypoints(request)
        }

    override fun cancelRoute() = request("cancelRoute") { cancelRoute() }
    override fun requestRoute(route: RouteInfo) = request("requestRoute") {
        ApiLog.event("Navi", "requestRoute", "arguments") {
            mapOf(
                "clientId" to clientId,
                "value" to sdkFields(route)
            )
        }
        requestRoute(route)
    }

    override fun addWaypoint(waypoint: RequestWaypointInfo) = request("addWaypoint") {
        ApiLog.event("Navi", "addWaypoint", "arguments") {
            mapOf(
                "clientId" to clientId,
                "value" to sdkFields(waypoint)
            )
        }
        addWaypoint(waypoint)
    }
    override fun requestNavigationScreen(screen: NavigationScreenRequest) =
        request("requestNavigationScreen") {
            ApiLog.event(
                "Navi",
                "requestNavigationScreen",
                "arguments"
            ) { mapOf("clientId" to clientId, "type" to screen.javaClass.simpleName) }
            requestNavigationScreen(screen)
        }

    override fun release() {
        ApiLog.event("Navi", "release", "start", "I")
        synchronized(lock) {
            if (state == State.RELEASED) return
            state = State.RELEASED
            listener = null
            try {
                if (listenerRegistered) ApiLog.call(
                    "Navi",
                    "removeListener",
                    { mapOf("clientId" to clientId) }) { api.removeListener(sdkListener) }
            } finally {
                listenerRegistered = false
                ApiLog.call("Navi", "release", { mapOf("clientId" to clientId) }) { api.release() }
            }
        }
    }
}
