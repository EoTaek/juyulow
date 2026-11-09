package eotaek.juyulow.navigation

import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context

/** Injectable SDK boundary so lifecycle and callback handling can be tested without a vehicle. */
internal interface NaviHelperApi {
    fun initialize(onConnected: () -> Unit, onFailed: (Throwable) -> Unit)
    fun addListener(listener: NaviHelperEventListener)
    fun removeListener(listener: NaviHelperEventListener)
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

internal class AndroidNaviHelperApi(context: Context) : NaviHelperApi {
    private val helper = NaviHelper(context.applicationContext)

    override fun initialize(onConnected: () -> Unit, onFailed: (Throwable) -> Unit) =
        helper.initialize(onConnected, onFailed)

    override fun addListener(listener: NaviHelperEventListener) = helper.addListener(listener)
    override fun removeListener(listener: NaviHelperEventListener) = helper.removeListener(listener)
    override fun getCurrentLocationInfo() = helper.getCurrentLocationInfo()
    override fun getDestinationInfo() = helper.getDestinationInfo()
    override fun getWaypointInfo() = helper.getWaypointInfo()
    override fun getRouteStateInfo() = helper.getRouteStateInfo()
    override fun requestTrip(request: TripRequest) = helper.requestTrip(request)
    override fun requestRoute(route: RouteInfo) = helper.requestRoute(route)
    override fun addWaypoint(waypoint: RequestWaypointInfo) = helper.addWaypoint(waypoint)
    override fun requestNavigationScreen(screen: NavigationScreenRequest) =
        helper.requestNavigationScreen(screen)

    override fun release() = helper.release()
}
