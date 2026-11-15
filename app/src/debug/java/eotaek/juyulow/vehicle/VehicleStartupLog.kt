package eotaek.juyulow.vehicle

import eotaek.juyulow.core.data.ApiLog

internal fun logVehicleStartup(message: String, error: Throwable?) {
    ApiLog.event(
        "Vehicle",
        "startup",
        "diagnostic",
        "W"
    ) { mapOf("stage" to message.substringBefore(" detail=")) }
    if (error != null) ApiLog.error("Vehicle", "startup", error)
}
