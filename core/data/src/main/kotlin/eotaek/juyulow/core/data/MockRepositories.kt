package eotaek.juyulow.core.data

import eotaek.juyulow.core.model.ComparisonContext
import eotaek.juyulow.core.model.ComparisonSession
import eotaek.juyulow.core.model.JourneyContext
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.core.model.NavigationSelection
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.core.model.Station
import eotaek.juyulow.core.model.compareStation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.random.Random

fun generateStations(journey: JourneyContext, random: Random = Random.Default): List<Station> {
    val names =
        listOf("한길 셀프주유소", "초록 주유소", "도담 에너지", "하늘 셀프주유소", "숲길 주유소", "한빛 에너지", "가온 셀프주유소", "늘봄 주유소")
    val brands = listOf("S-OIL", "GS칼텍스", "현대오일뱅크", "SK에너지")
    val count = random.nextInt(3, 9)
    return names.take(count).mapIndexed { index, name ->
        val outboundMeters = random.nextInt(8, 59) * 100
        val outboundSeconds = random.nextInt(3, 16) * 60
        Station(
            id = "station-$index", name = name, brand = brands[index % brands.size],
            priceWonPerL = random.nextInt(1590, 1746),
            latitude = 37.6194 + random.nextInt(-30, 31) / 1000.0,
            longitude = 127.0598 + random.nextInt(-30, 31) / 1000.0,
            outboundMeters = outboundMeters, outboundSeconds = outboundSeconds,
            returnMeters = random.nextInt(9, 66) * 100, returnSeconds = random.nextInt(4, 18) * 60,
            viaMeters = maxOf(
                outboundMeters + 500,
                journey.baselineMeters + random.nextInt(5, 86) * 100
            ),
            viaSeconds = maxOf(
                outboundSeconds + 180,
                journey.baselineSeconds + random.nextInt(2, 19) * 60
            ),
            tollWon = if (random.nextDouble() > .78) 500 else 0,
        )
    }
}

class MockStationRepository : StationRepository {
    override suspend fun search(journey: JourneyContext): List<Station> {
        delay(850)
        return generateStations(journey)
    }
}

/** One active batch: starting a new home search replaces the old, already-popped flow. */
class InMemoryComparisonSessionRepository : ComparisonSessionRepository {
    private var session: ComparisonSession? = null
    override fun create(
        journey: JourneyContext,
        settings: RefuelSettings,
        stations: List<Station>
    ): ComparisonSession {
        val next = ComparisonSession(
            UUID.randomUUID().toString(),
            ComparisonContext(journey, settings, stations.maxOfOrNull { it.priceWonPerL } ?: 0),
            stations.toList(),
        )
        session = next
        return next
    }

    override fun get(id: String): ComparisonSession? = session?.takeIf { it.id == id }
}

class MockJourneyRepository : JourneyRepository {
    private val mutableContext = MutableStateFlow(JourneyContext())
    override val context = mutableContext.asStateFlow()
    private var lastSelection: NavigationSelection? = null

    override fun setHasDestination(hasDestination: Boolean) {
        mutableContext.value = JourneyContext(destination = if (hasDestination) "서울숲" else null)
        lastSelection = null
    }

    override suspend fun select(
        session: ComparisonSession,
        station: Station,
        mode: JourneyMode
    ): NavigationSelection {
        require(station in session.stations)
        val comparison = compareStation(session.context, station, mode)
        val next = NavigationSelection(
            id = UUID.randomUUID().toString(),
            stationId = station.id,
            stationName = station.name,
            mode = mode,
            destination = when (mode) {
                JourneyMode.WAYPOINT -> requireNotNull(session.context.journey.destination)
                JourneyMode.DESTINATION -> station.name
                JourneyMode.ROUND_TRIP -> "현재 위치"
            },
            waypoint = if (mode == JourneyMode.DESTINATION) null else station.name,
            distanceMeters = comparison.distanceMeters,
            durationSeconds = comparison.totalSeconds,
            refuelSeconds = session.context.settings.refuelSeconds,
        )
        lastSelection = next
        mutableContext.value = JourneyContext(
            next.destination,
            next.distanceMeters,
            comparison.drivingSeconds,
            next.waypoint
        )
        return next
    }

    override fun selection(id: String): NavigationSelection? = lastSelection?.takeIf { it.id == id }
}
