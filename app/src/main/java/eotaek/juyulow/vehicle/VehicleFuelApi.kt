package eotaek.juyulow.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Fuel
import android.content.Context

internal interface VehicleFuelApi {
    fun initialize()
    fun getFuel(): Fuel
    fun release()
}

internal class AndroidVehicleFuelApi(context: Context) : VehicleFuelApi {
    private val vehicle = Vehicle(context.applicationContext)
    override fun initialize() = vehicle.initialize()
    override fun getFuel() = vehicle.getFuel()
    override fun release() = vehicle.release()
}
