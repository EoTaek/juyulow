package eotaek.juyulow.vehicle

import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import eotaek.juyulow.core.model.VehicleFailureReason
import eotaek.juyulow.core.model.VehicleProperty
import eotaek.juyulow.core.model.VehicleValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** All mutations/SDK calls are confined to Main. Callbacks are queued, even if SDK calls back inline. */
internal class PleosStartupSource(
    private val scope: CoroutineScope,
    private val factory: () -> VehicleFuelClient,
    private val diagnostic: (String, Throwable?) -> Unit,
) : StartupVehicleSource {
    private var client: VehicleFuelClient? = null
    private var initializationError: Exception? = null
    private var initialized = false
    private var closing = false
    private val pending = mutableMapOf<FuelProperty, (Any?, Exception?) -> Unit>()

    private val listener = object : VehicleFuelClientListener {
        override fun onInitializationFailed(cause: Exception) {
            initializationError = cause
        }

        override fun onCapabilities(property: FuelProperty, capability: CapabilityConfig) =
            deliver(property, capability)

        override fun onFuelEconomy(property: FuelProperty, litersPer100Km: Float?) =
            deliver(property, litersPer100Km)

        override fun onFuelPercent(percent: Int?) = deliver(FuelProperty.FUEL_PERCENT, percent)
        override fun onError(property: FuelProperty, cause: Exception) =
            deliver(property, null, cause)
    }

    private fun deliver(property: FuelProperty, value: Any?, error: Exception? = null) {
        scope.launch {
            yield() // Never release/request the next stage inside the native callback's stack.
            pending.remove(property)?.invoke(value, error)
            releaseIfDrained()
        }
    }

    override suspend fun read(property: VehicleProperty): VehicleValue? {
        pleosStage("initialize") {
            if (!initialized) {
                initialized = true
                try {
                    client = factory()
                    client!!.initialize(listener)
                } catch (error: Exception) {
                    initializationError = error
                }
            }
            initializationError?.let { throw it }
        }
        val fuelProperty = when (property) {
            VehicleProperty.CUMULATIVE_ECONOMY -> FuelProperty.CUMULATIVE_ECONOMY
            VehicleProperty.CURRENT_ECONOMY -> FuelProperty.CURRENT_ECONOMY
            VehicleProperty.FUEL_PERCENT -> FuelProperty.FUEL_PERCENT
            else -> error("Not a Pleos fuel property: $property")
        }
        val capability = pleosStage("capability") {
            request(fuelProperty) { client ->
                when (fuelProperty) {
                    FuelProperty.CUMULATIVE_ECONOMY -> client.checkCumulativeFuelEconomyCapabilities()
                    FuelProperty.CURRENT_ECONOMY -> client.checkCurrentFuelEconomyCapabilities()
                    FuelProperty.FUEL_PERCENT -> client.checkFuelPercentCapabilities()
                }
            } as CapabilityConfig
        }
        if (capability.accessType != AccessType.READ && capability.accessType != AccessType.READ_WRITE)
            throw VehicleReadException(VehicleFailureReason.UNSUPPORTED, "capability")
        val raw = pleosStage("get") {
            request(fuelProperty) { client ->
                when (fuelProperty) {
                    FuelProperty.CUMULATIVE_ECONOMY -> client.getCumulativeFuelEconomy()
                    FuelProperty.CURRENT_ECONOMY -> client.getCurrentFuelEconomy()
                    FuelProperty.FUEL_PERCENT -> client.getFuelPercent()
                }
            }
        }
        return (raw as? Number)?.toDouble()?.let(VehicleValue::Number)
    }

    private suspend fun <T> pleosStage(stage: String, block: suspend () -> T): T =
        vehicleStage(stage) {
            try {
                block()
            } catch (error: Exception) {
                // Vehicle 2.3.2 AAR obfuscates VehicleException. Its published source emits these
                // exact ErrorCode names as messages; keep other Exception types intact.
                val reason = when (error.message) {
                    "ERROR_NOT_SUPPORTED_PROPERTY", "ERROR_NOT_SUPPORTED_AREA",
                    "ERROR_NOT_SUPPORTED_VALUE_TYPE" -> VehicleFailureReason.UNSUPPORTED

                    "ERROR_SECURITY_PERMISSION" -> VehicleFailureReason.PERMISSION
                    "ERROR_SERVICE_CONNECTION_FAILURE" -> VehicleFailureReason.CONNECTION
                    "ERROR_EMPTY_RESULT", "ERROR_DRIVING_RESTRICTED" -> VehicleFailureReason.UNAVAILABLE
                    else -> throw error
                }
                throw VehicleReadException(reason, stage, error)
            }
        }

    private suspend fun request(property: FuelProperty, call: (VehicleFuelClient) -> Unit): Any? =
        suspendCancellableCoroutine { continuation ->
            check(property !in pending)
            pending[property] = { value, error ->
                if (continuation.isActive) {
                    if (error == null) continuation.resume(value) else continuation.resumeWithException(
                        error
                    )
                }
            }
            try {
                call(checkNotNull(client))
            } catch (error: Exception) {
                pending.remove(property)?.invoke(null, error)
            }
            // Keep cancelled requests pending until their actual SDK callback arrives.
        }

    override fun close() {
        closing = true; releaseIfDrained()
    }

    private fun releaseIfDrained() {
        if (!closing || pending.isNotEmpty()) return
        val owned = client ?: return
        client = null
        try {
            owned.release()
        } catch (error: Exception) {
            diagnostic("provider=PLEOS stage=release failed", error)
        }
    }
}
