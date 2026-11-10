package eotaek.juyulow.navigation

import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.RoutePointPoiType
import ai.pleos.playground.navi.constants.RoutePointSettingType
import ai.pleos.playground.navi.constants.RoutePointType
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RoutePointInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.TripSummary
import ai.pleos.playground.navi.data.WaypointInfo
import eotaek.juyulow.core.model.NaviCandidate
import eotaek.juyulow.core.model.NaviPoint
import eotaek.juyulow.core.model.NaviResult
import eotaek.juyulow.core.model.NaviStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PleosNavigationRepositoryTest {
    private class Fake : NaviClient {
        lateinit var listener: NaviClientListener
        var missing = false
        var destinationId = "original"
        var destinationSubId: String? = "0"
        var waypointIds = emptyList<String>()
        var routeCalls = 0
        var released = 0
        var tripReply: ((TripRequest) -> TripInfo)? = null
        var completeRoute = true
        var delayedRouteReads = 0
        var waypointCoordinates = 37.0 to 127.0
        var mode = NaviMode.ROUTE_GUIDING
        override fun initialize(listener: NaviClientListener) { this.listener = listener; listener.onConnected() }
        override fun getCurrentLocationInfo() { listener.onCurrentLocationInfo(CurrentLocationInfo(37.0,127.0,address="",routeId="r",currentRouteIndex=0)) }
        override fun getDestinationInfo() { if (!missing) listener.onDestinationInfo(DestinationInfo(37.0,127.0,name=destinationId,address="",remainDistance=10,remainTime=5,tollFare=0,poiId=destinationId,poiSubId=destinationSubId)) }
        override fun getWaypointInfo() { listener.onWayPointInfo(waypointIds.map { WaypointInfo(name=it,address="",latitude=waypointCoordinates.first,longitude=waypointCoordinates.second,remainDistance=1,remainTime=1,poiId=it,poiSubId="0") }) }
        override fun getRouteStateInfo() {
            val observedMode = if (routeCalls > 0 && delayedRouteReads-- > 0) NaviMode.SAFE_GUIDING else mode
            listener.onRouteStateInfo(RouteStateInfo(true,observedMode,false,RouteOption.RECOMMENDED,0,emptyList(),null))
        }
        override fun requestTrip(request: TripRequest) { tripReply?.let { listener.onTripInfo(it(request)) } }
        override fun requestRoute(route: RouteInfo) { routeCalls++; destinationId=route.poiId; waypointIds=emptyList(); if (completeRoute) listener.onRoutingCompleted() }
        override fun requestRouteWithWaypoints(request: RouteWithWaypointsRequest) { routeCalls++; destinationId=request.destination.poiId; waypointIds=request.waypoints.map { it.poiId }; listener.onRoutingCompleted() }
        override fun cancelRoute() = Unit
        override fun addWaypoint(waypoint: RequestWaypointInfo) { routeCalls++; waypointIds=listOf(waypoint.poiId)+waypointIds; listener.onRoutingCompleted() }
        override fun requestNavigationScreen(screen: NavigationScreenRequest) = Unit
        override fun release() { released++ }
    }
    private val candidate = NaviCandidate(NaviPoint(37.0,127.0,"station","station","0"),37.0,127.0)
    @Test
    fun missingWaypointCoordinatesAllowConfirmedIdentityAndOrder() = runTest {
        val fake=Fake(); fake.destinationSubId=null; fake.waypointCoordinates=0.0 to 0.0
        val repo=PleosNavigationRepository(fake,NaviTripLedger(),StandardTestDispatcher(testScheduler),clock={testScheduler.currentTime},operationTimeout=1000)
        val snapshot=(repo.initialize() as NaviResult.Success).value
        assertTrue(repo.setRoute(snapshot, candidate, true) is NaviResult.Success)
        assertEquals(NaviStatus.READY, repo.state.value.status)
        assertEquals(listOf("station"),fake.waypointIds)
        assertEquals("original",fake.destinationId)
        repo.release()
    }
    @Test fun opaqueDestinationIsPreservedWithoutInventingSubId() = runTest {
        val fake=Fake(); fake.destinationSubId=null; fake.waypointIds=listOf("existing")
        val repo=PleosNavigationRepository(fake,NaviTripLedger(),StandardTestDispatcher(testScheduler))
        val snapshot=(repo.initialize() as NaviResult.Success).value
        assertTrue(repo.setRoute(snapshot,candidate,true) is NaviResult.Success)
        assertEquals("original",fake.destinationId); assertNull(fake.destinationSubId)
        assertEquals(listOf("station","existing"),fake.waypointIds)
        repo.release()
    }
    @Test fun tripAndRouteCoordinatesCanDifferWithoutWeakeningMatching() = runTest {
        val fake=Fake()
        val repo=PleosNavigationRepository(fake,NaviTripLedger(),StandardTestDispatcher(testScheduler))
        val snapshot=(repo.initialize() as NaviResult.Success).value
        val distinct=candidate.copy(responseLatitude=37.1,responseLongitude=127.1)
        assertTrue(repo.setRoute(snapshot,distinct,false) is NaviResult.Success)
        repo.release()
    }
    @Test fun routeEventBeforeGuidanceUpdateWaitsForObservedState() = runTest {
        val fake=Fake(); fake.delayedRouteReads=2
        val repo=PleosNavigationRepository(fake,NaviTripLedger(),StandardTestDispatcher(testScheduler))
        val snapshot=(repo.initialize() as NaviResult.Success).value
        val result=repo.setRoute(snapshot,candidate,false)
        assertTrue(result is NaviResult.Success)
        assertEquals(1,fake.routeCalls)
        assertTrue(testScheduler.currentTime >= 500)
        repo.release()
    }
    @Test fun routeEventWithoutMatchingStateTimesOutWithoutResubmitting() = runTest {
        val fake=Fake(); fake.delayedRouteReads=Int.MAX_VALUE
        val repo=PleosNavigationRepository(fake,NaviTripLedger(),StandardTestDispatcher(testScheduler),clock={testScheduler.currentTime},operationTimeout=1000)
        val snapshot=(repo.initialize() as NaviResult.Success).value
        assertTrue(repo.setRoute(snapshot,candidate,false) is NaviResult.Failure)
        assertEquals(1,fake.routeCalls)
        assertEquals(NaviStatus.UNCERTAIN,repo.state.value.status)
        repo.release()
    }
    private fun response(id: String) = TripInfo(1200,300,0,0.0,TripSummary(emptyList(),listOf(
        RoutePointInfo(37.0,127.0,"station",id,1.0,RoutePointPoiType.entries.first(),
            RoutePointType.DESTINATION,RoutePointSettingType.entries.first()))))
    @Test fun tripPreservesMetersAndSecondsAndRejectsRepeatedCandidate() = runTest {
        val fake=Fake(); fake.tripReply={ response(it.poiId) }
        val repo=PleosNavigationRepository(fake,NaviTripLedger(),StandardTestDispatcher(testScheduler))
        repo.initialize()
        val result=(repo.trips(listOf(candidate)) as NaviResult.Success).value.single()
        assertEquals(1200,result.meters); assertEquals(300,result.seconds)
        assertTrue(repo.trips(listOf(candidate)) is NaviResult.Failure)
        repo.release()
    }
    @Test fun mismatchedTripStopsBatchAndLocksLedger() = runTest {
        val fake=Fake(); fake.tripReply={ response("unrelated") }; val ledger=NaviTripLedger()
        val repo=PleosNavigationRepository(fake,ledger,StandardTestDispatcher(testScheduler))
        repo.initialize()
        assertTrue(repo.trips(listOf(candidate,candidate.copy(point=candidate.point.copy(poiId="next")))) is NaviResult.Failure)
        assertEquals(setOf("station"),ledger.requested); assertTrue(ledger.uncertain)
        repo.release()
    }
    @Test fun missingRouteEventIsNotSuccessAndRefreshCanConfirmWithoutResubmission() = runTest {
        val fake=Fake(); fake.completeRoute=false
        val repo=PleosNavigationRepository(fake,NaviTripLedger(),StandardTestDispatcher(testScheduler),operationTimeout=10)
        val snapshot=(repo.initialize() as NaviResult.Success).value
        assertTrue(repo.setRoute(snapshot,candidate,false) is NaviResult.Failure)
        assertEquals(NaviStatus.UNCERTAIN,repo.state.value.status)
        assertTrue(repo.setRoute(snapshot,candidate,false) is NaviResult.Failure)
        assertTrue(repo.refresh() is NaviResult.Success)
        assertEquals(NaviStatus.READY,repo.state.value.status)
        assertEquals(1,fake.routeCalls); repo.release()
    }
    @Test fun readsAndConfirmsReplacement() = runTest {
        val fake=Fake(); val repo=PleosNavigationRepository(fake,NaviTripLedger(),StandardTestDispatcher(testScheduler))
        val snapshot=(repo.initialize() as NaviResult.Success).value
        assertEquals("original",snapshot.destination!!.poiId)
        assertTrue(repo.setRoute(snapshot,candidate,false) is NaviResult.Success)
        assertEquals("station",repo.state.value.snapshot!!.destination!!.poiId)
        repo.release(); repo.release(); assertEquals(1,fake.released)
    }
    @Test fun preservesOrderAndRejectsFullWaypoints() = runTest {
        val fake=Fake(); fake.waypointIds=listOf("existing")
        val repo=PleosNavigationRepository(fake,NaviTripLedger(),StandardTestDispatcher(testScheduler))
        val snapshot=(repo.initialize() as NaviResult.Success).value
        assertTrue(repo.setRoute(snapshot,candidate,true) is NaviResult.Success)
        assertEquals(listOf("station","existing"),fake.waypointIds)
        val other=candidate.copy(point=candidate.point.copy(poiId="other"))
        assertTrue(repo.setRoute(repo.state.value.snapshot!!,other,true) is NaviResult.Failure)
        assertEquals(1,fake.routeCalls); repo.release()
    }
    @Test fun changedContextDoesNotSubmit() = runTest {
        val fake=Fake(); val repo=PleosNavigationRepository(fake,NaviTripLedger(),StandardTestDispatcher(testScheduler))
        val snapshot=(repo.initialize() as NaviResult.Success).value
        fake.destinationId="changed"
        assertTrue(repo.setRoute(snapshot,candidate,false) is NaviResult.Failure)
        assertEquals(0,fake.routeCalls); repo.release()
    }
    @Test fun missingReadNeverBecomesNoDestinationAndLocksLaterReads() = runTest {
        val fake=Fake(); fake.missing=true
        val repo=PleosNavigationRepository(fake,NaviTripLedger(),StandardTestDispatcher(testScheduler),readTimeout=10)
        assertTrue(repo.initialize() is NaviResult.Failure)
        assertNull(repo.state.value.snapshot)
        assertEquals(setOf("location","waypoints","route"),repo.state.value.receivedFields)
        fake.missing=false
        assertTrue(repo.refresh() is NaviResult.Failure); repo.release()
    }
    @Test fun tripTimeoutLocksProcessLedgerAcrossReplacement() = runTest {
        val ledger=NaviTripLedger(); val fake=Fake()
        val repo=PleosNavigationRepository(fake,ledger,StandardTestDispatcher(testScheduler),operationTimeout=10)
        repo.initialize()
        assertTrue(repo.trips(listOf(candidate)) is NaviResult.Failure)
        assertTrue(ledger.uncertain)
        assertTrue(repo.trips(listOf(candidate)) is NaviResult.Failure)
        repo.release()
    }
    @Test fun rejectsDuplicateAndUnverifiedPointsWithoutMutation() = runTest {
        val fake=Fake(); val repo=PleosNavigationRepository(fake,NaviTripLedger(),StandardTestDispatcher(testScheduler))
        val snapshot=(repo.initialize() as NaviResult.Success).value
        val duplicate=candidate.copy(point=candidate.point.copy(poiId="original"))
        assertTrue(repo.setRoute(snapshot,duplicate,true) is NaviResult.Failure)
        assertTrue(repo.setRoute(snapshot,candidate.copy(point=candidate.point.copy(subId=null)),false) is NaviResult.Failure)
        assertEquals(0,fake.routeCalls); repo.release()
    }
    @Test fun concurrentOperationIsBusyAndReleaseTerminatesPendingTrip() = runTest {
        val fake=Fake(); val repo=PleosNavigationRepository(fake,NaviTripLedger(),StandardTestDispatcher(testScheduler))
        repo.initialize()
        val pending=async { repo.trips(listOf(candidate)) }; runCurrent()
        assertEquals(NaviResult.Failure("BUSY"),repo.refresh())
        repo.release(); assertTrue(pending.await() is NaviResult.Failure)
        assertEquals(NaviStatus.RELEASED,repo.state.value.status)
    }
    @Test fun explicitEmptyDestinationDiffersFromMissingResponse() = runTest {
        val fake=Fake(); fake.destinationId=""; fake.mode=NaviMode.SAFE_GUIDING
        val repo=PleosNavigationRepository(fake,NaviTripLedger(),StandardTestDispatcher(testScheduler))
        assertNull((repo.initialize() as NaviResult.Success).value.destination)
        repo.release()
    }
}
