package eotaek.juyulow.vehicle

import android.util.Log

internal fun logVehicleStartup(message: String, error: Throwable?) {
    Log.w("VehicleStartup", message, error)
}
