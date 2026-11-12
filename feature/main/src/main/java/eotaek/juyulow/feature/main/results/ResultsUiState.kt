package eotaek.juyulow.feature.main.results

import eotaek.juyulow.core.model.ComparisonSession
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.core.model.StationSort
import eotaek.juyulow.core.model.compareStation

data class ResultsUiState(
    val session: ComparisonSession? = null,
    val mode: JourneyMode = JourneyMode.DESTINATION,
    val sort: StationSort = StationSort.SAVING,
    val selectedId: String? = null,
    val searching: Boolean = false,
    val settingRoute: Boolean = false,
    val error: String? = null,
    val completedSelectionId: String? = null,
    val confirmReplacement: Boolean = false,
    val routeUncertain: Boolean = false,
    val comparisonExpired: Boolean = false,
) {
    val busy: Boolean get() = searching || settingRoute
    val evaluated: List<EvaluatedStation>
        get() {
            val batch = session ?: return emptyList()
            val rows =
                batch.stations.filter { it.outboundMeters != null && it.outboundSeconds != null }
                    .map { EvaluatedStation(it, compareStation(batch.context, it, mode)) }
            return when (sort) {
                StationSort.SAVING -> rows.sortedByDescending { it.comparison.netSavingWon }
                StationSort.TIME -> rows.sortedBy { it.comparison.costDurationSeconds }
            }
        }
    val selected: EvaluatedStation? get() = evaluated.find { it.station.id == selectedId }
}
