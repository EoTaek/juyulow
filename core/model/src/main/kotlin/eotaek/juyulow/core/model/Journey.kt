package eotaek.juyulow.core.model

enum class JourneyMode { WAYPOINT, DESTINATION, ROUND_TRIP }
enum class StationSort { SAVING, TIME }

data class RefuelSettings(
    val liters: Int = 40,
    val efficiencyKmPerL: Int = 12,
    val refuelMinutes: Int = 5,
    val includeTimeCost: Boolean = false,
    // User override for this process; a new startup begins with vehicle data again.
    val litersOverride: Double? = null,
) {
    init {
        require(liters in 10..100 && liters % 10 == 0)
        require(efficiencyKmPerL in 1..30)
        require(refuelMinutes in 1..20)
        require(litersOverride == null || (litersOverride.isFinite() && litersOverride >= 0))
    }

    val refuelSeconds: Int get() = refuelMinutes * 60
    val timeWonPerHour: Int get() = if (includeTimeCost) 12_000 else 0
}

data class JourneyContext(
    val destination: String? = "서울숲",
    val baselineMeters: Int? = 28_400,
    val baselineSeconds: Int? = 2_520,
    val waypoint: String? = null,
    val navigation: NaviSnapshot? = null,
    // Live comparison uses outbound metrics; route application may still insert a waypoint.
    val destinationOnly: Boolean = false,
    val tripOrigin: NaviSnapshot? = null,
) {
    val hasDestination: Boolean get() = destination != null
    val defaultMode: JourneyMode get() = if (hasDestination && !destinationOnly) JourneyMode.WAYPOINT else JourneyMode.DESTINATION
    val availableModes: List<JourneyMode>
        get() = if (destinationOnly) (if (hasDestination && navigation != null && navigation.waypoints.isEmpty()) listOf(
            JourneyMode.WAYPOINT,
            JourneyMode.DESTINATION
        ) else listOf(JourneyMode.DESTINATION)) else if (hasDestination) JourneyMode.entries else listOf(
            JourneyMode.DESTINATION,
            JourneyMode.ROUND_TRIP
        )
}

data class Station(
    val id: String,
    val name: String,
    val brand: String,
    val priceWonPerL: Int,
    val latitude: Double,
    val longitude: Double,
    val outboundMeters: Int,
    val outboundSeconds: Int,
    val returnMeters: Int?,
    val returnSeconds: Int?,
    val viaMeters: Int?,
    val viaSeconds: Int?,
    // Incremental toll for WAYPOINT; total toll for DESTINATION / ROUND_TRIP.
    val tollWon: Int?,
    val observedAtMillis: Long? = null,
)

data class ComparisonContext(
    val journey: JourneyContext,
    val conditions: RefuelConditions,
    val referencePriceWonPerL: Int,
)

data class ComparisonSession(
    val id: String,
    val context: ComparisonContext,
    val stations: List<Station>
)

data class StationComparison(
    val distanceMeters: Int,
    val drivingSeconds: Int,
    val totalSeconds: Int,
    val costDistanceMeters: Int,
    val costDurationSeconds: Int,
    val fuelSavingWon: Double,
    val drivingCostWon: Double,
    val tollWon: Double?,
    val timeCostWon: Double,
) {
    val netSavingWon: Double get() = fuelSavingWon - drivingCostWon - (tollWon ?: 0.0) - timeCostWon
}

data class NavigationSelection(
    val id: String,
    val stationId: String,
    val stationName: String,
    val mode: JourneyMode,
    val destination: String,
    val waypoint: String?,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val refuelSeconds: Int,
    val conditions: RefuelConditions,
    val outboundOnly: Boolean = false,
    val routeWaypoints: List<String> = emptyList(),
)

/** Web journey.ts contract: metres, seconds, KRW; round only at display boundaries. */
fun compareStation(
    context: ComparisonContext,
    station: Station,
    mode: JourneyMode
): StationComparison {
    require(mode in context.journey.availableModes)
    require(mode != JourneyMode.WAYPOINT || context.journey.hasDestination)
    require(context.referencePriceWonPerL >= 0)
    val calculationMode = if (context.journey.destinationOnly) JourneyMode.DESTINATION else mode
    val distance = when (calculationMode) {
        JourneyMode.WAYPOINT -> requireNotNull(station.viaMeters)
        JourneyMode.DESTINATION -> station.outboundMeters
        JourneyMode.ROUND_TRIP -> station.outboundMeters + requireNotNull(station.returnMeters)
    }
    val drive = when (calculationMode) {
        JourneyMode.WAYPOINT -> requireNotNull(station.viaSeconds)
        JourneyMode.DESTINATION -> station.outboundSeconds
        JourneyMode.ROUND_TRIP -> station.outboundSeconds + requireNotNull(station.returnSeconds)
    }
    val extraDistance =
        distance - if (calculationMode == JourneyMode.WAYPOINT) requireNotNull(context.journey.baselineMeters) else 0
    val extraTime =
        drive - (if (calculationMode == JourneyMode.WAYPOINT) requireNotNull(context.journey.baselineSeconds) else 0) + context.conditions.refuelSeconds
    return StationComparison(
        distanceMeters = distance,
        drivingSeconds = drive,
        totalSeconds = drive + context.conditions.refuelSeconds,
        costDistanceMeters = extraDistance,
        costDurationSeconds = extraTime,
        fuelSavingWon = (context.referencePriceWonPerL - station.priceWonPerL).toDouble() * context.conditions.liters,
        drivingCostWon = extraDistance / 1000.0 / context.conditions.efficiencyKmPerL * context.referencePriceWonPerL,
        tollWon = station.tollWon?.toDouble(),
        timeCostWon = extraTime / 3600.0 * context.conditions.timeWonPerHour,
    )
}
