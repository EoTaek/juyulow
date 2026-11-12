package eotaek.juyulow

import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.RouteInfo
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import eotaek.juyulow.core.data.InMemoryComparisonSessionRepository
import eotaek.juyulow.core.data.KakaoDetourEtaRepository
import eotaek.juyulow.core.data.KatecCoordinates
import eotaek.juyulow.core.data.LiveJourneyRepository
import eotaek.juyulow.core.data.OpinetCandidates
import eotaek.juyulow.core.data.OpinetClient
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.core.model.NaviCandidate
import eotaek.juyulow.core.model.NaviPoint
import eotaek.juyulow.core.model.NaviResult
import eotaek.juyulow.core.model.NaviSnapshot
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.core.model.confirmsRoute
import eotaek.juyulow.core.model.resolveRefuelConditions
import eotaek.juyulow.core.model.sameRouteTargetsAs
import eotaek.juyulow.navigation.NaviTripLedger
import eotaek.juyulow.navigation.PleosNaviClient
import eotaek.juyulow.navigation.PleosNavigationRepository
import eotaek.juyulow.navigation.canInteractWithNavigation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in real network/SDK test; restores the original destination, including no destination. */
class ProgressiveSearchIntegrationTest {
    @Test
    fun incrementalSearchAndInflightRouteSubmission(): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("progressiveSearchProbe") == "true")
        val mutate = args.getString("progressiveMutationProbe") == "true"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = PleosNaviClient(context)
        val navigation = PleosNavigationRepository(client, NaviTripLedger())
        val source = OpinetCandidates(OpinetClient.create(BuildConfig.OPINET_API_KEY))
        val live =
            LiveJourneyRepository(
                { navigation }, candidateSource = { a, c -> source.search(a, c) },
                canInteract = { canInteractWithNavigation(context) },
                detours = BuildConfig.KAKAO_REST_API_KEY.takeIf { it.isNotBlank() }
                    ?.let { KakaoDetourEtaRepository.create(it) })
        var original: NaviSnapshot? = null
        var changed = false
        suspend fun read(): NaviSnapshot = withTimeout(45_000) {
            while (true) {
                val result = navigation.refresh()
                if (result is NaviResult.Success) return@withTimeout result.value
                delay(250)
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }

        suspend fun restore(saved: NaviSnapshot) {
            withContext(Dispatchers.Main) {
                val d = saved.destination
                if (d == null) client.cancelRoute()
                else client.requestRoute(
                    RouteInfo(
                        latitude = d.latitude,
                        longitude = d.longitude,
                        poiName = d.name,
                        poiId = d.poiId,
                        poiSubId = d.subId ?: "",
                        address = d.address,
                        poiProvider = d.provider,
                        routeOption = RouteOption.valueOf(saved.routeOption)
                    )
                )
            }
            withTimeout(30_000) {
                while (true) {
                    delay(500)
                    val actual = read()
                    if (actual.sameRouteTargetsAs(saved) && actual.routeOption == saved.routeOption) break
                }
            }
        }
        try {
            live.initialize()
            original = requireNotNull(live.context.value.navigation)
            assumeTrue(
                "Existing waypoints are preserved; mutation probe requires an empty list",
                original.waypoints.isEmpty()
            )
            if (mutate && original.destination == null) {
                val point = KatecCoordinates().toWgs84(312623.2596234215, 548572.9766224765)
                val seed = NaviCandidate(
                    NaviPoint(
                        point.latitude,
                        point.longitude,
                        "ProgressiveProbeDestination",
                        "",
                        null
                    ), point.latitude, point.longitude, coordinateOnly = true
                )
                changed = true
                assertTrue(
                    "Seed destination",
                    navigation.setRoute(original, seed, false) is NaviResult.Success
                )
                live.refresh()
            }
            val sessions = InMemoryComparisonSessionRepository()
            val conditions = resolveRefuelConditions(RefuelSettings())
            val handle = requireNotNull(live.startSearch(live.context.value, conditions, sessions))
            val first =
                withTimeout(120_000) { handle.state.first { it.session?.firstReadyId != null || it.complete } }
            val batch = requireNotNull(first.session) { first.error.orEmpty() }
            assertNotNull("A successful Pleos ETA gates entry", batch.firstReadyId)
            Log.i(
                "ProgressiveProbe",
                "first ready=" + batch.stations.count { it.outboundMeters != null } + " pending=" + batch.stations.count { it.routePending } + " total=" + batch.stations.size
            )
            if (mutate) {
                for (mode in listOf(JourneyMode.WAYPOINT, JourneyMode.DESTINATION)) {
                    live.refresh()
                    val next = if (mode == JourneyMode.WAYPOINT) handle else requireNotNull(
                        live.startSearch(
                            live.context.value,
                            conditions,
                            sessions
                        )
                    )
                    val ready =
                        withTimeout(120_000) { next.state.first { it.session?.firstReadyId != null || it.complete } }.session!!
                    val station = ready.stations.first { it.id == ready.firstReadyId }
                    changed = true
                    val selection = withTimeout(90_000) { live.select(ready, station, mode) }
                    val actual = read()
                    assertTrue(
                        "Confirmed " + mode, actual.confirmsRoute(
                            ready.context.journey.navigation!!,
                            NaviCandidate(
                                NaviPoint(
                                    station.latitude,
                                    station.longitude,
                                    station.name,
                                    "",
                                    null
                                ), station.latitude, station.longitude, coordinateOnly = true
                            ), mode == JourneyMode.WAYPOINT
                        )
                    )
                    assertFalse(next.state.value.session!!.stations.any { it.routePending })
                    assertEquals(
                        ready.context.referencePriceWonPerL,
                        selection.referencePriceWonPerL
                    )
                    Log.i("ProgressiveProbe", "confirmed " + mode + " remaining batch stopped")
                    // A baseline destination with no waypoints is required for each independent CTA.
                    restore(batch.context.journey.navigation!!)
                    live.refresh()
                }
            }
            val completedHandle = if (mutate) requireNotNull(
                live.startSearch(
                    live.context.value,
                    conditions,
                    sessions
                )
            ) else handle
            val complete =
                withTimeout(180_000) { completedHandle.state.first { it.complete } }.session!!
            if (!mutate) assertEquals(batch.id, complete.id)
            assertFalse(complete.stations.any { it.routePending })
            val estimates =
                withTimeout(60_000) { completedHandle.state.first { it.session!!.stations.none { s -> s.detour?.pending == true } } }.session!!
            Log.i(
                "ProgressiveProbe",
                "finished ready=" + complete.stations.count { it.outboundMeters != null } + " failed=" + complete.stations.count { it.routeError != null } + " detourReady=" + estimates.stations.count { it.detour?.meters != null } + " detourFailed=" + estimates.stations.count { it.detour?.error != null })

        } finally {
            withContext(NonCancellable) {
                live.cancelActiveSearch()
                try {
                    if (changed) {
                        read(); restore(requireNotNull(original)); Log.i(
                            "ProgressiveProbe",
                            "original route restored"
                        )
                    }
                } finally {
                    live.release()
                }
            }
        }
    }
}
