package eotaek.juyulow.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import android.content.Context
import android.content.pm.PackageManager

internal interface FusedLocationApi {
    fun isSupported(): Boolean
    fun initialize()
    fun getLastKnownLocation(callback: FusedLocationListener)
    fun registerFusedLocationCallback(listener: FusedLocationListener)
    fun unregisterFusedLocationCallback(listener: FusedLocationListener)
    fun showLocationAccessBlockedPopup()
    fun release()
}

internal class AndroidFusedLocationApi(context: Context) : FusedLocationApi {
    private val context = context.applicationContext
    private val sdk = FusedLocation(this.context)

    override fun isSupported() = sdk.isSupported()
    override fun initialize() = sdk.initialize()

    override fun getLastKnownLocation(callback: FusedLocationListener) {
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("FUSED_LOCATION runtime permission is required")
        }
        sdk.getLastKnownLocation(callback)
    }

    override fun registerFusedLocationCallback(listener: FusedLocationListener) {
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("FUSED_LOCATION runtime permission is required")
        }
        sdk.registerFusedLocationCallback(listener)
    }

    override fun unregisterFusedLocationCallback(listener: FusedLocationListener) {
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("FUSED_LOCATION runtime permission is required")
        }
        sdk.unregisterFusedLocationCallback(listener)
    }

    override fun showLocationAccessBlockedPopup() = sdk.showLocationAccessBlockedPopup()
    override fun release() = sdk.release()

    private companion object {
        const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"
    }
}
