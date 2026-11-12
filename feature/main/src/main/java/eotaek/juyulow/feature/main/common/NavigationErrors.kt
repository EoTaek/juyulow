package eotaek.juyulow.feature.main.common

internal fun navigationFailureMessage(error: Exception, fallback: String): String =
    when (error.message) {
        "OPINET_KEY_MISSING", "OPINET_AUTH" -> "오피넷 인증 설정을 확인해 주세요."
        "OPINET_LIMIT" -> "오피넷 호출 한도를 초과했어요. 나중에 다시 시도해 주세요."
        "OPINET_NETWORK", "OPINET_TIMEOUT", "OPINET_HTTP" -> "오피넷에 연결하지 못했어요. 네트워크를 확인하고 다시 시도해 주세요."
        "OPINET_INVALID_RESPONSE", "OPINET_API_ERROR" -> "오피넷 응답을 확인하지 못했어요. 나중에 다시 시도해 주세요."
        "SEARCH_EXPIRED" -> "출발 위치나 경로 옵션이 바뀌어 이전 경로 정보를 사용할 수 없어요."
        "CONTEXT_CHANGED" -> "내비게이션 상태가 바뀌었어요. 현재 목적지를 확인한 뒤 다시 선택해 주세요."
        "TRIP_UNCERTAIN", "REPEAT_CANDIDATE" -> "경로 조회 응답을 구분할 수 없어 같은 주유소를 다시 조회할 수 없어요."
        "PARKING_REQUIRED" -> "정차 후 다시 시도해 주세요."
        "CATALOG_NOT_VERIFIED" -> "검증된 주유소 후보를 준비하지 못했어요."
        else -> fallback
    }
