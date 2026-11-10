package eotaek.juyulow

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import eotaek.juyulow.core.model.NaviResult
import eotaek.juyulow.navigation.NaviTripLedger
import eotaek.juyulow.navigation.PleosNaviClient
import eotaek.juyulow.navigation.PleosNavigationRepository
import eotaek.juyulow.navigation.verifiedStations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class VerifiedStationsIntegrationTest {
    @Test
    fun allEightTripsAndDestinationsAreConfirmedAndRestored() = runBlocking {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("verifiedStationsProbe") == "true"
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = PleosNaviClient(context)
        val repo = PleosNavigationRepository(client, NaviTripLedger())
        val car = Car.createCar(context)
        var changed = false
        suspend fun restore() {
            withContext(Dispatchers.Main) { client.cancelRoute() }
            withTimeout(40.seconds) {
                while (true) {
                    delay(500.milliseconds)
                    val result = repo.refresh()
                    check(result is NaviResult.Success) { "RESTORE_READ_FAILED: $result" }
                    if (result.value.destination == null && result.value.waypoints.isEmpty() && result.value.mode == "SAFE_GUIDING") break
                }
            }
            Log.i("VerifiedStationsProbe", "original empty route restored")
        }
        try {
            val restrictions =
                car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            check(!restrictions.currentCarUxRestrictions.isRequiresDistractionOptimization)
            val original = (repo.initialize() as NaviResult.Success).value
            check(original.destination == null && original.waypoints.isEmpty() && original.mode == "SAFE_GUIDING")
            val candidates = verifiedStations.map { it.candidate }

            if (InstrumentationRegistry.getArguments().getString("waypointPolicyProbe") == "true") {
                changed = true
                val destination =
                    (repo.setRoute(original, candidates[7], false) as NaviResult.Success).value
                val first = repo.setRoute(destination, candidates[0], true)
                check(first is NaviResult.Success) { "FIRST: $first" }
                val second = repo.setRoute(first.value, candidates[1], true)
                check(second is NaviResult.Success) { "SECOND: $second" }
                assertEquals(candidates[7].point.poiId, second.value.destination?.poiId)
                assertEquals(
                    listOf(candidates[1].point.poiId, candidates[0].point.poiId),
                    second.value.waypoints.map { it.poiId })
                check(repo.setRoute(second.value, candidates[2], true) is NaviResult.Failure)
                val replaced = repo.setRoute(second.value, candidates[3], false)
                check(replaced is NaviResult.Success) { "REPLACE: $replaced" }
                check(replaced.value.waypoints.isEmpty())
                restore(); changed = false
                return@runBlocking
            }
            val trips = repo.trips(candidates)
            check(trips is NaviResult.Success) { "TRIPS: $trips" }
            assertEquals(8, trips.value.size)
            for (candidate in candidates) {
                val before = (repo.refresh() as NaviResult.Success).value
                changed = true
                val result = repo.setRoute(before, candidate, false)
                check(result is NaviResult.Success) { "${candidate.point.name}: $result / ${repo.state.value}" }
                assertEquals(candidate.point.poiId, result.value.destination?.poiId)
                Log.i(
                    "VerifiedStationsProbe",
                    "confirmed ${candidate.point.poiId} ${candidate.point.name}"
                )
                restore(); changed = false
            }
        } finally {
            try {
                if (changed) withContext(NonCancellable) { restore() }
            } finally {
                repo.release(); car.disconnect()
            }
        }
    }
}
