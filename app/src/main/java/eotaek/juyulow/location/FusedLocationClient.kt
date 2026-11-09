package eotaek.juyulow.location

import ai.pleos.playground.location.fused.model.LocationData
import androidx.annotation.MainThread

/** Explicitly owned session. Initialization submission does not guarantee service connectivity. */
@MainThread
interface FusedLocationClient {
    fun isSupported(): Boolean
    fun initialize(listener: FusedLocationClientListener)
    fun getLastKnownLocation()
    fun registerFusedLocationCallback()
    fun unregisterFusedLocationCallback()
    fun showLocationAccessBlockedPopup()
    fun release()
}

/** SDK callback thread; null locations and original errors are preserved. Do not throw or block. */
interface FusedLocationClientListener {
    fun onInitialized() = Unit
    fun onInitializationFailed(cause: Exception) = Unit
    fun onLastKnownLocation(location: LocationData?) = Unit
    fun onChanged(location: LocationData?) = Unit
    fun onError(cause: Exception) = Unit
}
