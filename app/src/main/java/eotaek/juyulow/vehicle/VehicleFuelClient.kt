package eotaek.juyulow.vehicle

import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import androidx.annotation.MainThread

/** Fuel-only Vehicle SDK adapter. Owners check capabilities before reading/subscribing. */
@MainThread
interface VehicleFuelClient {
    fun initialize(listener: VehicleFuelClientListener)
    fun checkCumulativeFuelEconomyCapabilities()
    fun checkCurrentFuelEconomyCapabilities()
    fun checkFuelPercentCapabilities()
    fun getCumulativeFuelEconomy()
    fun getCurrentFuelEconomy()
    fun getFuelPercent()
    fun registerCumulativeFuelEconomy()
    fun registerCurrentFuelEconomy()
    fun registerFuelPercent()
    fun unregisterCumulativeFuelEconomy()
    fun unregisterCurrentFuelEconomy()
    fun unregisterFuelPercent()

    /** Wait for outstanding get/capability callbacks before releasing, as required by the SDK. */
    fun release()
}

enum class FuelProperty { CUMULATIVE_ECONOMY, CURRENT_ECONOMY, FUEL_PERCENT }

/** SDK callback thread; callbacks must not throw or block. Values retain SDK units/nullability. */
interface VehicleFuelClientListener {
    /** initialize() returned; does not assert IPC readiness or capability support. */
    fun onInitialized() = Unit
    fun onInitializationFailed(cause: Exception) = Unit
    fun onCapabilities(property: FuelProperty, capability: CapabilityConfig) = Unit

    /** Fuel economy in L/100km, without conversion, rounding or fallback. */
    fun onFuelEconomy(property: FuelProperty, litersPer100Km: Float?) = Unit
    fun onFuelPercent(percent: Int?) = Unit
    fun onError(property: FuelProperty, cause: Exception) = Unit
}
