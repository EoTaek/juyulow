package eotaek.juyulow.vehicle

import ai.pleos.playground.vehicle.api.Fuel
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.listener.FuelEconomyListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import android.content.Context
import eotaek.juyulow.core.data.ApiLog
import eotaek.juyulow.sdkFields
import java.util.EnumSet

class PleosVehicleFuelClient internal constructor(private val api: VehicleFuelApi) :
    VehicleFuelClient {
    constructor(context: Context) : this(AndroidVehicleFuelApi(context))

    private enum class State { NEW, INITIALIZING, INITIALIZED, FAILED, RELEASED }

    private val clientId = ApiLog.id()
    private val lock = Any()
    private var state = State.NEW
    private var listener: VehicleFuelClientListener? = null
    private var fuel: Fuel? = null
    private var initializationAttempted = false
    private val attempted = EnumSet.noneOf(FuelProperty::class.java)
    private val subscribed = EnumSet.noneOf(FuelProperty::class.java)
    private val cumulativeUpdates = economyListener(FuelProperty.CUMULATIVE_ECONOMY)
    private val currentUpdates = economyListener(FuelProperty.CURRENT_ECONOMY)
    private val percentUpdates = object : FuelPercentListener {
        override fun onFuelPercentUpdated(value: Int?) =
            deliver(FuelProperty.FUEL_PERCENT, updatesOnly = true) {
                ApiLog.event("Vehicle", "onFuelPercent", "callback") {
                    mapOf(
                        "clientId" to clientId,
                        "value" to sdkFields(value)
                    )
                }
                onFuelPercent(value)
            }

        override fun onFailed(e: Exception) =
            deliver(
                FuelProperty.FUEL_PERCENT,
                updatesOnly = true
            ) {
                ApiLog.event("Vehicle", "onError", "callback") {
                    mapOf(
                        "clientId" to clientId,
                        "property" to sdkFields(FuelProperty.FUEL_PERCENT),
                        "e" to sdkFields(e)
                    )
                }
                onError(FuelProperty.FUEL_PERCENT, e)
            }
    }

    override fun initialize(listener: VehicleFuelClientListener) {
        ApiLog.event("Vehicle", "initialize", "start", "I") { mapOf("clientId" to clientId) }
        synchronized(lock) {
            check(state == State.NEW) { "Initialize once; create a new VehicleFuelClient" }
            this.listener = listener
            state = State.INITIALIZING
            try {
                initializationAttempted = true
                ApiLog.call(
                    "Vehicle",
                    "initialize",
                    { mapOf("clientId" to clientId) }) { api.initialize() }
                fuel = ApiLog.call(
                    "Vehicle",
                    "getFuel",
                    { mapOf("clientId" to clientId) }) { api.getFuel() }
                state = State.INITIALIZED
            } catch (cause: Exception) {
                state = State.FAILED
                ApiLog.error("Vehicle", "initialize", cause)
                listener.onInitializationFailed(cause)
                return
            }
        }
        ApiLog.event("Vehicle", "initialize", "returned", "I")
        listener.onInitialized()
    }

    override fun checkCumulativeFuelEconomyCapabilities() =
        request("checkCumulativeFuelEconomyCapabilities") {
            checkCumulativeFuelEconomyCapabilities(
                capabilityResult(FuelProperty.CUMULATIVE_ECONOMY),
                errorResult(FuelProperty.CUMULATIVE_ECONOMY)
            )
        }

    override fun checkCurrentFuelEconomyCapabilities() =
        request("checkCurrentFuelEconomyCapabilities") {
            checkCurrentFuelEconomyCapabilities(
                capabilityResult(FuelProperty.CURRENT_ECONOMY),
                errorResult(FuelProperty.CURRENT_ECONOMY)
            )
        }

    override fun checkFuelPercentCapabilities() = request("checkFuelPercentCapabilities") {
        checkFuelPercentCapabilities(
            capabilityResult(FuelProperty.FUEL_PERCENT),
            errorResult(FuelProperty.FUEL_PERCENT)
        )
    }

    override fun getCumulativeFuelEconomy() = request("getCumulativeFuelEconomy") {
        getCumulativeFuelEconomy(
            economyResult(FuelProperty.CUMULATIVE_ECONOMY),
            errorResult(FuelProperty.CUMULATIVE_ECONOMY)
        )
    }

    override fun getCurrentFuelEconomy() = request("getCurrentFuelEconomy") {
        getCurrentFuelEconomy(
            economyResult(FuelProperty.CURRENT_ECONOMY),
            errorResult(FuelProperty.CURRENT_ECONOMY)
        )
    }

    override fun getFuelPercent() = request("getFuelPercent") {
        getFuelPercent(
            { value ->
                deliver(FuelProperty.FUEL_PERCENT) {
                    ApiLog.event(
                        "Vehicle",
                        "onFuelPercent",
                        "callback"
                    ) { mapOf("clientId" to clientId, "value" to sdkFields(value)) }
                    onFuelPercent(value)
                }
            },
            errorResult(FuelProperty.FUEL_PERCENT)
        )
    }

    override fun registerCumulativeFuelEconomy() = register(FuelProperty.CUMULATIVE_ECONOMY) {
        registerCumulativeFuelEconomy(cumulativeUpdates)
    }

    override fun registerCurrentFuelEconomy() = register(FuelProperty.CURRENT_ECONOMY) {
        registerCurrentFuelEconomy(currentUpdates)
    }

    override fun registerFuelPercent() = register(FuelProperty.FUEL_PERCENT) {
        registerFuelPercent(percentUpdates)
    }

    override fun unregisterCumulativeFuelEconomy() = unregister(FuelProperty.CUMULATIVE_ECONOMY)
    override fun unregisterCurrentFuelEconomy() = unregister(FuelProperty.CURRENT_ECONOMY)
    override fun unregisterFuelPercent() = unregister(FuelProperty.FUEL_PERCENT)

    private fun capabilityResult(property: FuelProperty): (CapabilityConfig) -> Unit = { value ->
        deliver(property) {
            ApiLog.event("Vehicle", "onCapabilities", "callback") {
                mapOf(
                    "clientId" to clientId,
                    "property" to sdkFields(property),
                    "value" to sdkFields(value)
                )
            }
            onCapabilities(property, value)
        }
    }

    private fun economyResult(property: FuelProperty): (Float?) -> Unit = { value ->
        deliver(property) {
            ApiLog.event("Vehicle", "onFuelEconomy", "callback") {
                mapOf(
                    "clientId" to clientId,
                    "property" to sdkFields(property),
                    "value" to sdkFields(value)
                )
            }
            onFuelEconomy(property, value)
        }
    }

    private fun errorResult(property: FuelProperty): (Exception) -> Unit = { cause ->
        deliver(property) {
            ApiLog.event("Vehicle", "onError", "callback") {
                mapOf(
                    "clientId" to clientId,
                    "property" to sdkFields(property),
                    "cause" to sdkFields(cause)
                )
            }
            onError(property, cause)
        }
    }

    private fun economyListener(property: FuelProperty) = object : FuelEconomyListener {
        override fun onFuelEconomyUpdated(efficiency: Float?) =
            deliver(property, updatesOnly = true) {
                ApiLog.event("Vehicle", "onFuelEconomy", "callback") {
                    mapOf(
                        "clientId" to clientId,
                        "property" to sdkFields(property),
                        "efficiency" to sdkFields(efficiency)
                    )
                }
                onFuelEconomy(property, efficiency)
            }

        override fun onFailed(e: Exception) =
            deliver(property, updatesOnly = true) {
                ApiLog.event("Vehicle", "onError", "callback") {
                    mapOf(
                        "clientId" to clientId,
                        "property" to sdkFields(property),
                        "e" to sdkFields(e)
                    )
                }
                onError(property, e)
            }
    }

    private inline fun deliver(
        property: FuelProperty,
        updatesOnly: Boolean = false,
        block: VehicleFuelClientListener.() -> Unit
    ) {
        val target = synchronized(lock) {
            if (state == State.INITIALIZED && (!updatesOnly || property in subscribed)) listener else null
        }
        if (target == null) ApiLog.event(
            "Vehicle",
            "deliver",
            "ignored_callback"
        ) { mapOf("clientId" to clientId, "state" to state.name) }
        target?.block()
    }

    private inline fun request(operation: String, block: Fuel.() -> Unit) {
        ApiLog.call("Vehicle", operation, { mapOf("clientId" to clientId) }) {
            synchronized(lock) {
                check(state == State.INITIALIZED) { "VehicleFuelClient is not initialized: $state" }
                checkNotNull(fuel).block()
            }
        }
    }

    private inline fun register(property: FuelProperty, block: Fuel.() -> Unit) =
        request("register") {
            check(property !in attempted) { "Fuel subscription already registered or requires cleanup: $property" }
            attempted.add(property)
            subscribed.add(property)
            try {
                block()
            } catch (cause: Exception) {
                subscribed.remove(property)
                throw cause
            }
        }

    private fun unregister(property: FuelProperty) = request("unregister") {
        subscribed.remove(property)
        if (property in attempted) {
            removeSubscription(property)
            attempted.remove(property)
        }
    }

    private fun removeSubscription(property: FuelProperty) {
        when (property) {
            FuelProperty.CUMULATIVE_ECONOMY -> checkNotNull(fuel).unregisterCumulativeFuelEconomy(
                cumulativeUpdates
            )

            FuelProperty.CURRENT_ECONOMY -> checkNotNull(fuel).unregisterCurrentFuelEconomy(
                currentUpdates
            )

            FuelProperty.FUEL_PERCENT -> checkNotNull(fuel).unregisterFuelPercent(percentUpdates)
        }
    }

    override fun release() {
        ApiLog.event("Vehicle", "release", "start", "I")
        synchronized(lock) {
            if (state == State.RELEASED) return
            state = State.RELEASED
            listener = null
            subscribed.clear()
            var failure: Exception? = null
            fun cleanup(action: () -> Unit) {
                try {
                    action()
                } catch (cause: Exception) {
                    val first = failure
                    if (first == null) failure =
                        cause else if (first !== cause) first.addSuppressed(cause)
                }
            }
            attempted.forEach { property -> cleanup { removeSubscription(property) } }
            attempted.clear()
            if (initializationAttempted) cleanup {
                ApiLog.call(
                    "Vehicle",
                    "release",
                    { mapOf("clientId" to clientId) }) { api.release() }
            }
            fuel = null
            failure?.let { throw it }
        }
    }
}
