package eotaek.juyulow.core.data

import eotaek.juyulow.core.model.JourneyContext
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.core.model.resolveRefuelConditions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MockRepositoriesTest {
    @Test
    fun generatedBatchesHaveBoundsUniqueIdsAndIndependentReturns() {
        val counts = mutableSetOf<Int>()
        repeat(100) { seed ->
            val route = JourneyContext(baselineMeters = 50000, baselineSeconds = 5000)
            val stations = generateStations(route, Random(seed))
            counts.add(stations.size)
            assertTrue(stations.size in 3..8)
            assertEquals(stations.size, stations.distinctBy { it.id }.size)
            assertTrue(stations.all { it.viaMeters!! > route.baselineMeters!! && it.viaSeconds!! > route.baselineSeconds!! })
            assertTrue(stations.all { it.priceWonPerL in 1590..1745 })
        }
        assertEquals((3..8).toSet(), counts)
    }

    @Test
    fun eachModeUpdatesHomeImmediatelyWithoutChangingSearchSnapshot() = runTest {
        JourneyMode.entries.forEach { mode ->
            val repository = MockJourneyRepository()
            val session = InMemoryComparisonSessionRepository().create(
                repository.context.value,
                resolveRefuelConditions(RefuelSettings()),
                generateStations(repository.context.value, Random(1))
            )
            val station = session.stations.first()
            val result = repository.select(session, station, mode)
            assertEquals(result, repository.selection(result.id))
            assertEquals(result.destination, repository.context.value.destination)
            assertEquals(
                result.durationSeconds - result.refuelSeconds,
                repository.context.value.baselineSeconds
            )
            assertEquals("서울숲", session.context.journey.destination)
            assertEquals(28400, session.context.journey.baselineMeters)
            when (mode) {
                JourneyMode.WAYPOINT -> {
                    assertEquals("서울숲", result.destination); assertEquals(
                        station.name,
                        result.waypoint
                    )
                }

                JourneyMode.DESTINATION -> {
                    assertEquals(station.name, result.destination); assertNull(result.waypoint)
                }

                JourneyMode.ROUND_TRIP -> {
                    assertEquals("현재 위치", result.destination); assertEquals(
                        station.name,
                        result.waypoint
                    )
                }
            }
        }
    }

    @Test
    fun priceReferenceIsSharedAndSessionCopiesMutableInput() {
        val stations = generateStations(JourneyContext(), Random(2)).toMutableList()
        val expectedPrice = stations.maxOf { it.priceWonPerL }
        val repository = InMemoryComparisonSessionRepository()
        val first =
            repository.create(JourneyContext(), resolveRefuelConditions(RefuelSettings()), stations)
        stations.clear()
        assertEquals(expectedPrice, first.context.referencePriceWonPerL)
        assertFalse(first.stations.isEmpty())
        assertEquals(first, repository.get(first.id))
        repository.create(JourneyContext(), resolveRefuelConditions(RefuelSettings()), emptyList())
        assertNull(repository.get(first.id))
    }
}
