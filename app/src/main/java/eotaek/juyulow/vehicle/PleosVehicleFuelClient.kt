package eotaek.juyulow.vehicle

import ai.pleos.playground.vehicle.api.Fuel
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.listener.FuelEconomyListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import android.content.Context
import java.util.EnumSet

class PleosVehicleFuelClient internal constructor(private val api: VehicleFuelApi) :
    VehicleFuelClient {
    constructor(context: Context) : this(AndroidVehicleFuelApi(context))

    private enum class State { NEW, INITIALIZING, INITIALIZED, FAILED, RELEASED }

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
            deliver(FuelProperty.FUEL_PERCENT, updatesOnly = true) { onFuelPercent(value) }

        override fun onFailed(e: Exception) =
            deliver(
                FuelProperty.FUEL_PERCENT,
                updatesOnly = true
            ) { onError(FuelProperty.FUEL_PERCENT, e) }
    }

    override fun initialize(listener: VehicleFuelClientListener) {
        synchronized(lock) {
            check(state == State.NEW) { "Initialize once; create a new VehicleFuelClient" }
            this.listener = listener
            state = State.INITIALIZING
            try {
                initializationAttempted = true
                api.initialize()
                fuel = api.getFuel()
                state = State.INITIALIZED
            } catch (cause: Exception) {
                state = State.FAILED
                listener.onInitializationFailed(cause)
                return
            }
        }
        listener.onInitialized()
    }

    override fun checkCumulativeFuelEconomyCapabilities() = request {
        checkCumulativeFuelEconomyCapabilities(
            capabilityResult(FuelProperty.CUMULATIVE_ECONOMY),
            errorResult(FuelProperty.CUMULATIVE_ECONOMY)
        )
    }

    override fun checkCurrentFuelEconomyCapabilities() = request {
        checkCurrentFuelEconomyCapabilities(
            capabilityResult(FuelProperty.CURRENT_ECONOMY),
            errorResult(FuelProperty.CURRENT_ECONOMY)
        )
    }

    override fun checkFuelPercentCapabilities() = request {
        checkFuelPercentCapabilities(
            capabilityResult(FuelProperty.FUEL_PERCENT),
            errorResult(FuelProperty.FUEL_PERCENT)
        )
    }

    override fun getCumulativeFuelEconomy() = request {
        getCumulativeFuelEconomy(
            economyResult(FuelProperty.CUMULATIVE_ECONOMY),
            errorResult(FuelProperty.CUMULATIVE_ECONOMY)
        )
    }

    override fun getCurrentFuelEconomy() = request {
        getCurrentFuelEconomy(
            economyResult(FuelProperty.CURRENT_ECONOMY),
            errorResult(FuelProperty.CURRENT_ECONOMY)
        )
    }

    override fun getFuelPercent() = request {
        getFuelPercent(
            { value -> deliver(FuelProperty.FUEL_PERCENT) { onFuelPercent(value) } },
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
        deliver(property) { onCapabilities(property, value) }
    }

    private fun economyResult(property: FuelProperty): (Float?) -> Unit = { value ->
        deliver(property) { onFuelEconomy(property, value) }
    }

    private fun errorResult(property: FuelProperty): (Exception) -> Unit = { cause ->
        deliver(property) { onError(property, cause) }
    }

    private fun economyListener(property: FuelProperty) = object : FuelEconomyListener {
        override fun onFuelEconomyUpdated(efficiency: Float?) =
            deliver(property, updatesOnly = true) { onFuelEconomy(property, efficiency) }

        override fun onFailed(e: Exception) =
            deliver(property, updatesOnly = true) { onError(property, e) }
    }

    private inline fun deliver(
        property: FuelProperty,
        updatesOnly: Boolean = false,
        block: VehicleFuelClientListener.() -> Unit
    ) {
        val target = synchronized(lock) {
            if (state == State.INITIALIZED && (!updatesOnly || property in subscribed)) listener else null
        }
        target?.block()
    }

    private inline fun request(block: Fuel.() -> Unit) = synchronized(lock) {
        check(state == State.INITIALIZED) { "VehicleFuelClient is not initialized: $state" }
        checkNotNull(fuel).block()
    }

    private inline fun register(property: FuelProperty, block: Fuel.() -> Unit) = request {
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

    private fun unregister(property: FuelProperty) = request {
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
            if (initializationAttempted) cleanup { api.release() }
            fuel = null
            failure?.let { throw it }
        }
    }
}
