package eotaek.juyulow.core.data

import eotaek.juyulow.core.model.NaviCandidate
import eotaek.juyulow.core.model.NaviPoint
import eotaek.juyulow.core.model.NaviSnapshot
import eotaek.juyulow.core.model.RefuelConditions

class OpinetCandidates(
    private val client: OpinetClient,
    private val coordinates: KatecCoordinates = KatecCoordinates()
) {
    suspend fun search(origin: NaviSnapshot, conditions: RefuelConditions): List<VerifiedStation> {
        val center = coordinates.toKatec(origin.location.latitude, origin.location.longitude)
        return client.around(
            center.x,
            center.y,
            conditions.searchRadiusKm,
            conditions.comparisonFuel
        ).mapNotNull { station ->
            val point = try {
                coordinates.toWgs84(station.x, station.y)
            } catch (_: IllegalArgumentException) {
                return@mapNotNull null
            }
            VerifiedStation(
                NaviCandidate(
                    NaviPoint(point.latitude, point.longitude, station.name, "", null),
                    point.latitude, point.longitude, coordinateOnly = true
                ),
                brands[station.brandCode] ?: "기타", station.priceWonPerL, station.id
            )
        }
    }

    private val brands = mapOf(
        "SKE" to "SK에너지", "GSC" to "GS칼텍스", "HDO" to "HD현대오일뱅크",
        "SOL" to "S-OIL", "RTO" to "알뜰", "RTE" to "알뜰", "RTX" to "고속도로알뜰", "NHO" to "농협알뜰"
    )
}
