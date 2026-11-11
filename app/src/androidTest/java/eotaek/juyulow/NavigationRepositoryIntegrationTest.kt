package eotaek.juyulow

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eotaek.juyulow.core.model.*
import eotaek.juyulow.navigation.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

/** Opt in explicitly; never executes as part of the normal UI regression suite. */
@RunWith(AndroidJUnit4::class)
class NavigationRepositoryIntegrationTest {
    @Test fun readsRealNavigationState() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("navigationProbe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = PleosNavigationRepository(PleosNaviClient(context), NaviTripLedger())
        try {
            val result = repo.initialize()
            Log.i("NavigationProbe", "read=$result state=${repo.state.value}")
            assertTrue("Expected all four real SDK responses: $result", result is NaviResult.Success)
        } finally { repo.release() }
    }

    @Test fun verifiedFixtureTripAndRouteRoundTrip() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("navigationMutationProbe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // No hard-coded assumption about POI sub-ID or road-snapped response coordinates.
        fun arg(key: String) = requireNotNull(args.getString(key)) { "Missing $key" }
        val candidate = NaviCandidate(NaviPoint(arg("latitude").toDouble(), arg("longitude").toDouble(),
            arg("poiName"), arg("poiId"), arg("poiSubId")),
            arg("responseLatitude").toDouble(), arg("responseLongitude").toDouble(),
            arg("routeResponseLatitude").toDouble(), arg("routeResponseLongitude").toDouble())
        val client = PleosNaviClient(context)
        val repo = PleosNavigationRepository(client, NaviTripLedger())
        val car = Car.createCar(context)
        var original: NaviSnapshot? = null
        var mutated = false
        try {
            val restrictions = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            check(!restrictions.currentCarUxRestrictions.isRequiresDistractionOptimization) { "Parking required" }
            original = (repo.initialize() as NaviResult.Success).value
            check(original.waypoints.size <= 1) { "Need a free waypoint slot" }
            // Verify that the initial route can be restored before changing it.
            original.destination?.let { route(it, original.routeOption) }
            original.waypoints.forEach { check(it.subId != null) }
            val mode = arg("routeMode")
            check(mode == "destination" || mode == "waypoint")
            var expected = original
            if (mode == "waypoint" && expected.destination == null) {
                val seed = NaviCandidate(NaviPoint(37.535225,127.009125,"SOSEOL_HANNAM","36khq926enpkcm5nqhe4","0"),
                    37.5351374,127.0089799,37.53539166,127.00923888)
                mutated = true
                val seeded = repo.setRoute(expected,seed,false)
                Log.i("NavigationProbe", "seed=$seeded")
                expected = (seeded as NaviResult.Success).value
            }
            val trip = repo.trips(listOf(candidate))
            Log.i("NavigationProbe", "trip=$trip")
            assertTrue(trip is NaviResult.Success)
            mutated = true // A failed submission may still have changed the actual route.
            val result = repo.setRoute(expected, candidate, mode == "waypoint")
            Log.i("NavigationProbe", "route=$result observed=${repo.state.value}")
            assertTrue(result is NaviResult.Success)
        } finally {
            try {
                if (mutated && original != null) {
                    val saved = original
                    withContext(Dispatchers.Main) {
                        val destination = saved.destination
                        if (destination == null) client.cancelRoute()
                        else if (saved.waypoints.isEmpty()) client.requestRoute(route(destination, saved.routeOption))
                        else client.requestRouteWithWaypoints(ai.pleos.playground.navi.data.RouteWithWaypointsRequest(
                            route(destination, saved.routeOption), saved.waypoints.mapIndexed { i, p ->
                                ai.pleos.playground.navi.data.RequestWaypointInfo(latitude=p.latitude,longitude=p.longitude,
                                    poiName=p.name,poiId=p.poiId,poiSubId=p.subId!!,address=p.address,poiProvider=p.provider,
                                    waypointIndex=if(i==0) ai.pleos.playground.navi.constants.WaypointIndex.FIRST else ai.pleos.playground.navi.constants.WaypointIndex.SECOND)
                            }))
                    }
                    withTimeout(30_000.milliseconds) {
                        while (true) {
                            delay(1_000.milliseconds)
                            val restored = repo.refresh()
                            if (restored is NaviResult.Success && restored.value.destination == saved.destination &&
                                restored.value.waypoints == saved.waypoints && restored.value.routeOption == saved.routeOption) break
                            check(restored is NaviResult.Success) { "RESTORE_READ_FAILED: $restored" }
                        }
                    }
                    Log.i("NavigationProbe", "original route restored and verified")
                }
            } finally { repo.release(); car.disconnect() }
        }
    }

    private fun route(p: NaviPoint, option: String) = ai.pleos.playground.navi.data.RouteInfo(
        latitude=p.latitude,longitude=p.longitude,poiName=p.name,poiId=p.poiId,
        poiSubId=requireNotNull(p.subId),address=p.address,poiProvider=p.provider,
        routeOption=ai.pleos.playground.navi.constants.RouteOption.valueOf(option))
}
