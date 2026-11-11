package eotaek.juyulow.navigation

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointCmdType
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
import ai.pleos.playground.navi.data.TripSummary
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PleosNaviClientTest {
    @Test fun `waypoint route and cancellation obey connection gate and submit unchanged`() {
        val request = ai.pleos.playground.navi.data.RouteWithWaypointsRequest(route, listOf(waypoint))
        assertThrows(IllegalStateException::class.java) { client.requestRouteWithWaypoints(request) }
        assertThrows(IllegalStateException::class.java) { client.cancelRoute() }
        connect()
        client.requestRouteWithWaypoints(request)
        client.cancelRoute()
        assertSame(request, api.waypointRoute)
        assertEquals(listOf("routeWithWaypoints", "cancelRoute"), api.calls.takeLast(2))
    }
    private val api = FakeNaviHelperApi()
    private val client = PleosNaviClient(api)
    private val listener = RecordingListener()
    private val tripRequest = TripRequest(37.4, 127.1, "navi-poi", "주유소", null)
    private val route = RouteInfo(
        latitude = 37.4, longitude = 127.1, poiName = "주유소",
        poiId = "navi-poi", address = "주소", poiSubId = "0"
    )
    private val waypoint = RequestWaypointInfo(
        latitude = 37.4, longitude = 127.1, poiName = "주유소",
        waypointIndex = WaypointIndex.FIRST, poiId = "navi-poi", address = "주소", poiSubId = "0"
    )

    @Test
    fun `requests are rejected before initialization and while connecting`() {
        requests().forEach { assertThrows(IllegalStateException::class.java) { it.run() } }
        client.initialize(listener)
        requests().forEach { assertThrows(IllegalStateException::class.java) { it.run() } }
        assertEquals(listOf("initialize"), api.calls)
    }

    @Test
    fun `listener is installed before connected callback can submit a request`() {
        client.initialize(object : NaviClientListener {
            override fun onConnected() {
                client.getCurrentLocationInfo()
            }
        })
        api.connected()
        assertEquals(listOf("initialize", "addListener", "location"), api.calls)
    }

    @Test
    fun `all requested operations submit unchanged SDK arguments without inventing completion`() {
        connect()
        requests().forEach { it.run() }
        assertEquals(
            listOf(
                "initialize", "addListener", "location", "destination", "waypoints", "state",
                "trip", "route", "addWaypoint", "screen"
            ), api.calls
        )
        assertSame(tripRequest, api.tripRequest)
        assertSame(route, api.route)
        assertSame(waypoint, api.waypoint)
        assertSame(NavigationScreenRequest.Foreground, api.screen)
        assertEquals(listOf("connected"), listener.events)
    }

    @Test
    fun `SDK results errors and route events are forwarded in callback order`() {
        connect()
        val location = CurrentLocationInfo(37.4, 127.1, 0.0, "주소", "route", null)
        val destination = DestinationInfo(
            37.5,
            127.2,
            name = "목적지",
            address = "주소",
            remainDistance = 1_000,
            remainTime = 120,
            tollFare = 500,
            poiId = "dest",
            poiSubId = null
        )
        val waypoints = listOf(
            WaypointInfo(
                "경유지", "주소", 37.4, 127.1,
                remainDistance = 500, remainTime = 60, poiId = "via", poiSubId = null
            )
        )
        val state = RouteStateInfo(
            true, NaviMode.ROUTE_GUIDING, false,
            RouteOption.RECOMMENDED, 0, emptyList(), null
        )
        val trip = TripInfo(1_200, 300, 0, 0.0, TripSummary(emptyList(), emptyList()))
        val started = RouteStartInfo(300, 1_200, 0, "주유소", 0)
        val changed = WaypointChangedInfo(WaypointIndex.FIRST, WaypointCmdType.ADD, started)
        api.listener.onCurrentLocationInfo(location)
        api.listener.onDestinationInfo(destination)
        api.listener.onWayPointInfo(waypoints)
        api.listener.onRouteStateInfo(state)
        api.listener.onTripInfo(trip)
        // Identical delayed/duplicate data cannot be assigned a new request ID by the adapter.
        api.listener.onTripInfo(trip)
        api.listener.onRouteStarted(started)
        api.listener.onRoutingCompleted()
        api.listener.onRouteCancelled()
        api.listener.onDestinationChanged()
        api.listener.onWaypointChanged(changed)
        api.listener.onError(NaviErrorCode.CAUSE_SEARCH_ROUTE)
        assertEquals(
            listOf(
                "connected", location, destination, waypoints, state, trip, trip,
                started, "completed", "cancelled", "destinationChanged", changed,
                NaviErrorCode.CAUSE_SEARCH_ROUTE
            ), listener.events
        )
        assertSame(trip, listener.events[5])
    }

    @Test
    fun `initial connection failure is separate from navigation errors and does not retry`() {
        client.initialize(listener)
        val failure = IllegalStateException("service unavailable")
        api.failed(failure)
        api.failed(failure)
        api.connected()
        assertEquals(listOf(failure), listener.events)
        requests().forEach { assertThrows(IllegalStateException::class.java) { it.run() } }
        assertThrows(IllegalStateException::class.java) { client.initialize(listener) }
        client.release()
        assertEquals(listOf("initialize", "release"), api.calls)
    }

    @Test
    fun `synchronous initialization exception is delivered as connection failure`() {
        val failure = SecurityException("permission")
        api.initializeFailure = failure
        client.initialize(listener)
        assertEquals(listOf(failure), listener.events)
        client.release()
        assertEquals(listOf("initialize", "release"), api.calls)
    }

    @Test
    fun `partially failed listener registration is cleaned up`() {
        val failure = IllegalStateException("addListener failed")
        api.registrationFailure = failure
        client.initialize(listener)
        api.connected()
        assertEquals(listOf(failure), listener.events)
        client.release()
        assertSame(api.listener, api.removedListener)
        assertEquals(listOf("initialize", "addListener", "removeListener", "release"), api.calls)
    }

    @Test
    fun `release removes same listener before releasing and ignores subsequent SDK callbacks`() {
        connect()
        api.connected() // Duplicate connection notifications must not register twice.
        client.release()
        client.release()
        api.listener.onRoutingCompleted()
        api.listener.onError(NaviErrorCode.CAUSE_SEARCH_ROUTE)
        api.connected()
        assertEquals(listOf("connected"), listener.events)
        assertSame(api.listener, api.removedListener)
        assertEquals(listOf("initialize", "addListener", "removeListener", "release"), api.calls)
        requests().forEach { assertThrows(IllegalStateException::class.java) { it.run() } }
        assertThrows(IllegalStateException::class.java) { client.initialize(listener) }
    }

    @Test
    fun `release during connection suppresses late success and failure`() {
        client.initialize(listener)
        client.release()
        api.connected()
        api.failed(IllegalStateException("late failure"))
        assertTrue(listener.events.isEmpty())
        assertEquals(listOf("initialize", "release"), api.calls)
    }

    @Test
    fun `SDK is released even if removing listener throws`() {
        connect()
        val failure = IllegalStateException("remove failed")
        api.removalFailure = failure
        assertSame(failure, assertThrows(IllegalStateException::class.java) { client.release() })
        client.release()
        assertEquals(listOf("initialize", "addListener", "removeListener", "release"), api.calls)
        assertThrows(IllegalStateException::class.java) { client.getDestinationInfo() }
    }

    @Test
    fun `synchronous request failure reaches caller and is never automatically retried`() {
        connect()
        val failure = SecurityException("permission revoked")
        api.requestFailure = failure
        assertSame(
            failure,
            assertThrows(SecurityException::class.java) { client.requestTrip(tripRequest) })
        assertEquals(listOf("initialize", "addListener", "trip"), api.calls)
        assertEquals(listOf("connected"), listener.events)
        client.release()
    }

    @Test
    fun `release before initialization is terminal and idempotent`() {
        client.release()
        client.release()
        assertEquals(listOf("release"), api.calls)
        assertThrows(IllegalStateException::class.java) { client.initialize(listener) }
    }

    private fun connect() {
        client.initialize(listener)
        api.connected()
    }

    private fun requests(): List<Runnable> = listOf(
        Runnable { client.getCurrentLocationInfo() }, Runnable { client.getDestinationInfo() },
        Runnable { client.getWaypointInfo() }, Runnable { client.getRouteStateInfo() },
        Runnable { client.requestTrip(tripRequest) }, Runnable { client.requestRoute(route) },
        Runnable { client.addWaypoint(waypoint) },
        Runnable { client.requestNavigationScreen(NavigationScreenRequest.Foreground) }
    )

    private class RecordingListener : NaviClientListener {
        val events = mutableListOf<Any>()
        override fun onConnected() {
            events += "connected"
        }

        override fun onConnectionFailed(cause: Throwable) {
            events += cause
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            events += currentLocationInfo
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            events += destinationInfo
        }

        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) {
            events.add(waypointInfoList)
        }

        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            events += routeStateInfo
        }

        override fun onTripInfo(tripInfo: TripInfo) {
            events += tripInfo
        }

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            events += routeStartInfo
        }

        override fun onRoutingCompleted() {
            events += "completed"
        }

        override fun onRouteCancelled() {
            events += "cancelled"
        }

        override fun onDestinationChanged() {
            events += "destinationChanged"
        }

        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            events += waypointChangedInfo
        }

        override fun onError(errorCode: NaviErrorCode) {
            events += errorCode
        }
    }

    private class FakeNaviHelperApi : NaviHelperApi {
        var waypointRoute: ai.pleos.playground.navi.data.RouteWithWaypointsRequest? = null
        val calls = mutableListOf<String>()
        lateinit var connected: () -> Unit
        lateinit var failed: (Throwable) -> Unit
        lateinit var listener: NaviHelperEventListener
        var removedListener: NaviHelperEventListener? = null
        var tripRequest: TripRequest? = null
        var route: RouteInfo? = null
        var waypoint: RequestWaypointInfo? = null
        var screen: NavigationScreenRequest? = null
        var initializeFailure: Exception? = null
        var registrationFailure: Exception? = null
        var removalFailure: Exception? = null
        var requestFailure: Exception? = null

        override fun initialize(onConnected: () -> Unit, onFailed: (Throwable) -> Unit) {
            calls += "initialize"
            initializeFailure?.let { throw it }
            connected = onConnected
            failed = onFailed
        }

        override fun addListener(listener: NaviHelperEventListener) {
            calls += "addListener"
            this.listener = listener
            registrationFailure?.let { throw it }
        }

        override fun removeListener(listener: NaviHelperEventListener) {
            calls += "removeListener"
            removedListener = listener
            removalFailure?.let { throw it }
        }

        override fun getCurrentLocationInfo() {
            calls += "location"
        }

        override fun getDestinationInfo() {
            calls += "destination"
        }

        override fun getWaypointInfo() {
            calls += "waypoints"
        }

        override fun getRouteStateInfo() {
            calls += "state"
        }

        override fun requestTrip(request: TripRequest) {
            calls += "trip"
            requestFailure?.let { throw it }
            tripRequest = request
        }

        override fun requestRouteWithWaypoints(request: ai.pleos.playground.navi.data.RouteWithWaypointsRequest) { calls += "routeWithWaypoints"; waypointRoute = request }
        override fun cancelRoute() { calls += "cancelRoute" }
        override fun requestRoute(route: RouteInfo) {
            calls += "route"; this.route = route
        }

        override fun addWaypoint(waypoint: RequestWaypointInfo) {
            calls += "addWaypoint"; this.waypoint = waypoint
        }

        override fun requestNavigationScreen(screen: NavigationScreenRequest) {
            calls += "screen"; this.screen = screen
        }

        override fun release() {
            calls += "release"
        }
    }
}
