package eotaek.juyulow

/** Explicit public SDK fields; never falls back to SDK object toString(). */
internal fun sdkFields(value: Any?): Any? = when (value) {
    null, is String, is Number, is Boolean -> value
    is Enum<*> -> value.name
    is Throwable -> mapOf(
        "exceptionType" to value.javaClass.simpleName,
        "errorCode" to value.message?.takeIf {
            it in setOf(
                "ERROR_NOT_SUPPORTED_PROPERTY",
                "ERROR_NOT_SUPPORTED_AREA",
                "ERROR_NOT_SUPPORTED_VALUE_TYPE",
                "ERROR_SECURITY_PERMISSION",
                "ERROR_SERVICE_CONNECTION_FAILURE",
                "ERROR_EMPTY_RESULT",
                "ERROR_DRIVING_RESTRICTED",
                "ERROR_INTERNAL_SERVICE",
                "ERROR_UNDEFINED"
            )
        })

    is Iterable<*> -> value.map(::sdkFields)
    is ai.pleos.playground.location.fused.model.LocationData -> mapOf(
        "latitude" to sdkFields(value.latitude),
        "longitude" to sdkFields(value.longitude),
        "altitude" to sdkFields(value.altitude),
        "heading" to sdkFields(value.heading),
        "confidence" to sdkFields(value.confidence),
        "timestampMillis" to sdkFields(value.timestampMillis)
    )

    is ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig -> mapOf(
        "accessType" to sdkFields(
            value.accessType
        )
    )

    is ai.pleos.playground.navi.data.CurrentLocationInfo -> mapOf(
        "latitude" to sdkFields(value.latitude),
        "longitude" to sdkFields(value.longitude),
        "heading" to sdkFields(value.heading),
        "address" to sdkFields(value.address),
        "routeId" to sdkFields(value.routeId),
        "currentRouteIndex" to sdkFields(value.currentRouteIndex)
    )

    is ai.pleos.playground.navi.data.DestinationInfo -> mapOf(
        "latitude" to sdkFields(value.latitude),
        "longitude" to sdkFields(value.longitude),
        "displayLatitude" to sdkFields(value.displayLatitude),
        "displayLongitude" to sdkFields(value.displayLongitude),
        "name" to sdkFields(value.name),
        "address" to sdkFields(value.address),
        "remainDistance" to sdkFields(value.remainDistance),
        "remainTime" to sdkFields(value.remainTime),
        "tollFare" to sdkFields(value.tollFare),
        "poiId" to sdkFields(value.poiId),
        "poiSubId" to sdkFields(value.poiSubId),
        "poiProvider" to sdkFields(value.poiProvider)
    )

    is ai.pleos.playground.navi.data.WaypointInfo -> mapOf(
        "name" to sdkFields(value.name),
        "address" to sdkFields(value.address),
        "latitude" to sdkFields(value.latitude),
        "longitude" to sdkFields(value.longitude),
        "displayLatitude" to sdkFields(value.displayLatitude),
        "displayLongitude" to sdkFields(value.displayLongitude),
        "remainDistance" to sdkFields(value.remainDistance),
        "remainTime" to sdkFields(value.remainTime),
        "poiId" to sdkFields(value.poiId),
        "poiSubId" to sdkFields(value.poiSubId),
        "poiProvider" to sdkFields(value.poiProvider)
    )

    is ai.pleos.playground.navi.data.TripInfo -> mapOf(
        "distance" to sdkFields(value.distance),
        "eta" to sdkFields(value.eta),
        "estimatedSocAtDest" to sdkFields(value.estimatedSocAtDest),
        "energyConsumption" to sdkFields(value.energyConsumption),
        "summary" to sdkFields(value.summary)
    )

    is ai.pleos.playground.navi.data.TripSummary -> mapOf("routePoints" to sdkFields(value.routePoints))
    is ai.pleos.playground.navi.data.TripRequest -> mapOf(
        "latitude" to sdkFields(value.latitude),
        "longitude" to sdkFields(value.longitude),
        "poiId" to sdkFields(value.poiId),
        "poiName" to sdkFields(value.poiName),
        "poiSubId" to sdkFields(value.poiSubId)
    )

    is ai.pleos.playground.navi.data.RouteInfo -> mapOf(
        "latitude" to sdkFields(value.latitude),
        "longitude" to sdkFields(value.longitude),
        "displayLatitude" to sdkFields(value.displayLatitude),
        "displayLongitude" to sdkFields(value.displayLongitude),
        "poiName" to sdkFields(value.poiName),
        "poiId" to sdkFields(value.poiId),
        "address" to sdkFields(value.address),
        "poiSubId" to sdkFields(value.poiSubId),
        "routeOption" to sdkFields(value.routeOption),
        "poiProvider" to sdkFields(value.poiProvider),
        "isMobileRequest" to sdkFields(value.isMobileRequest)
    )

    is ai.pleos.playground.navi.data.RequestWaypointInfo -> mapOf(
        "latitude" to sdkFields(value.latitude),
        "longitude" to sdkFields(value.longitude),
        "poiName" to sdkFields(value.poiName),
        "waypointIndex" to sdkFields(value.waypointIndex),
        "poiId" to sdkFields(value.poiId),
        "address" to sdkFields(value.address),
        "poiSubId" to sdkFields(value.poiSubId),
        "poiProvider" to sdkFields(value.poiProvider)
    )

    is ai.pleos.playground.navi.data.RouteWithWaypointsRequest -> mapOf(
        "destination" to sdkFields(
            value.destination
        ), "waypoints" to sdkFields(value.waypoints)
    )

    is ai.pleos.playground.navi.data.RouteStartInfo -> mapOf(
        "duration" to sdkFields(value.duration),
        "distance" to sdkFields(value.distance),
        "tollFare" to sdkFields(value.tollFare),
        "name" to sdkFields(value.name),
        "remainDestinationChargingValue" to sdkFields(value.remainDestinationChargingValue)
    )

    is ai.pleos.playground.navi.data.WaypointChangedInfo -> mapOf(
        "index" to sdkFields(value.index),
        "cmdType" to sdkFields(value.cmdType),
        "routeStartInfo" to sdkFields(value.routeStartInfo)
    )

    is ai.pleos.playground.navi.data.RouteStateInfo -> mapOf(
        "termsAgreement" to sdkFields(value.termsAgreement),
        "naviMode" to sdkFields(value.naviMode),
        "isRouting" to sdkFields(value.isRouting),
        "routeOption" to sdkFields(value.routeOption),
        "remainDestinationChargingInt" to sdkFields(value.remainDestinationChargingInt),
        "remainWaypointChargingInt" to sdkFields(value.remainWaypointChargingInt)
    )

    is ai.pleos.playground.navi.data.RoutePointInfo -> mapOf(
        "latitude" to sdkFields(value.latitude),
        "longitude" to sdkFields(value.longitude),
        "poiName" to sdkFields(value.poiName),
        "poiId" to sdkFields(value.poiId),
        "ratioOnRoute" to sdkFields(value.ratioOnRoute),
        "poiType" to sdkFields(value.poiType),
        "pointType" to sdkFields(value.pointType),
        "settingType" to sdkFields(value.settingType)
    )

    else -> mapOf("type" to value.javaClass.simpleName)
}
