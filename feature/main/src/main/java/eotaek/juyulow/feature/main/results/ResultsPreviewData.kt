package eotaek.juyulow.feature.main.results

import eotaek.juyulow.core.model.DetourEstimate
import eotaek.juyulow.core.model.JourneyMode
import eotaek.juyulow.core.model.NaviLocation
import eotaek.juyulow.core.model.NaviPoint
import eotaek.juyulow.core.model.NaviSnapshot

/** SDK/API 호출 없이 실제 결과 화면의 상태 분기를 재현한다. */
internal data class ResultsPreviewCase(val name: String, val state: ResultsUiState) {
    override fun toString(): String = name
}

internal fun resultsPreviewCases(): List<ResultsPreviewCase> {
    val cases = selectionPreviewCases()
    val ready = cases.first { it.name == "경유 추정 성공" }.state
    val session = requireNotNull(ready.session)
    val pending = session.stations[1].copy(
        outboundMeters = null, outboundSeconds = null, routePending = true, detour = null,
    )
    val failed = session.stations[2].copy(
        outboundMeters = null, outboundSeconds = null,
        routeError = "Pleos 경로 조회에 실패했어요.", detour = null,
    )
    return cases + listOf(
        ResultsPreviewCase("ETA 성공·조회 중·실패 혼합", ready.copy(
            session = session.copy(
                stations = listOf(session.stations.first(), pending, failed),
                searchComplete = false,
            ),
        )),
        ResultsPreviewCase("ETA 전체 실패", ready.copy(
            session = session.copy(stations = listOf(failed)), selectedId = null,
        )),
        ResultsPreviewCase("검색 결과 없음", ready.copy(
            session = session.copy(stations = emptyList()), selectedId = null,
        )),
        ResultsPreviewCase("재검색 중", ready.copy(searching = true)),
        ResultsPreviewCase("재검색 실패·기존 결과 유지", ready.copy(
            error = "주유소 검색에 실패했어요. 잠시 후 다시 시도해 주세요.",
        )),
        ResultsPreviewCase("경로 설정 중", ready.copy(settingRoute = true)),
        ResultsPreviewCase("경로 설정 결과 불명", ready.copy(
            routeUncertain = true, error = "경로 설정 결과를 확인하지 못했어요.",
        )),
        ResultsPreviewCase("세션 없음", ResultsUiState(
            error = "검색 결과가 없어요. 홈에서 다시 찾아 주세요.",
        )),
    )
}

internal fun selectionPreviewCases(): List<ResultsPreviewCase> {
    val timestamp = 1_794_700_800_000L
    val destination = NaviPoint(37.5445, 127.0374, "서울숲", "preview-destination", null)
    val navigation = NaviSnapshot(
        location = NaviLocation(37.6, 127.0, "preview-route", 0, timestamp),
        destination = destination, waypoints = emptyList(), mode = "ROUTE_GUIDING",
        routing = false, routeOption = "RECOMMENDED", observedAtMillis = timestamp,
    )
    val original = previewSession()
    val session = original.copy(
        context = original.context.copy(journey = original.context.journey.copy(
            destinationOnly = true, stationSource = "오피넷", navigation = navigation,
        )),
        stations = original.stations.mapIndexed { index, station ->
            station.copy(
                returnMeters = null, returnSeconds = null, viaMeters = null, viaSeconds = null,
                tollWon = null, observedAtMillis = timestamp, source = "오피넷", sourceIndex = index,
            )
        },
        createdAtMillis = timestamp, firstReadyId = "station-0",
    )
    val ready = ResultsUiState(session, JourneyMode.WAYPOINT, selectedId = "station-0")
    val estimate = DetourEstimate(
        pending = false, meters = 1_800, seconds = 240, tollWon = 0,
        assumptions = "동일 출발지·목적지·경로 옵션 기준",
    )
    fun withEstimate(value: DetourEstimate) = ready.copy(
        session = session.copy(stations = session.stations.map { it.copy(detour = value) }),
    )
    val success = withEstimate(estimate)
    return listOf(
        ResultsPreviewCase("목적지 없음", ready.copy(
            mode = JourneyMode.DESTINATION,
            session = session.copy(context = session.context.copy(journey = session.context.journey.copy(
                destination = null, baselineMeters = null, baselineSeconds = null,
                navigation = navigation.copy(destination = null, mode = "IDLE"),
            ))),
        )),
        ResultsPreviewCase("목적지 있음·목적지 대체", ready.copy(mode = JourneyMode.DESTINATION)),
        ResultsPreviewCase("목적지 있음·경유 추정 미제공", ready),
        ResultsPreviewCase("경유 추정 성공", success),
        ResultsPreviewCase("경유 추정 조회 중", withEstimate(DetourEstimate())),
        ResultsPreviewCase("경유 추정 실패", withEstimate(DetourEstimate(
            pending = false, error = "경유 비용을 확인하지 못했어요.",
        ))),
        ResultsPreviewCase("이전 검색 기준", success.copy(comparisonExpired = true)),
        ResultsPreviewCase("추가 지출", ready.copy(selectedId = "station-2")),
    )
}
