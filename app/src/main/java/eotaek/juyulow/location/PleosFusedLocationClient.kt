package eotaek.juyulow.location

import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import eotaek.juyulow.core.data.ApiLog
import eotaek.juyulow.sdkFields

class PleosFusedLocationClient internal constructor(private val api: FusedLocationApi) :
    FusedLocationClient {
    constructor(context: Context) : this(AndroidFusedLocationApi(context))

    private enum class State { NEW, INITIALIZING, INITIALIZED, FAILED, RELEASED }

    private val clientId = ApiLog.id()
    private val lock = Any()
    private var state = State.NEW
    private var listener: FusedLocationClientListener? = null
    private var subscriptionAttempted = false
    private var subscribed = false
    private var initializationAttempted = false

    private val updates = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) =
            deliver(updatesOnly = true) {
                ApiLog.event("Location", "onChanged", "callback") {
                    mapOf(
                        "clientId" to clientId,
                        "location" to sdkFields(location)
                    )
                }
                onChanged(location)
            }

        override fun onError(exception: Exception) =
            deliver(updatesOnly = true) {
                ApiLog.event("Location", "onError", "callback") {
                    mapOf(
                        "clientId" to clientId,
                        "exception" to sdkFields(exception)
                    )
                }
                onError(exception)
            }
    }

    override fun isSupported(): Boolean = synchronized(lock) {
        check(state != State.RELEASED) { "FusedLocationClient is released" }
        ApiLog.call("Location", "isSupported") {
            api.isSupported().also { supported ->
                ApiLog.event(
                    "Location",
                    "isSupported",
                    "response"
                ) { mapOf("supported" to supported) }
            }
        }
    }

    override fun initialize(listener: FusedLocationClientListener) {
        ApiLog.event("Location", "initialize", "start", "I") { mapOf("clientId" to clientId) }
        synchronized(lock) {
            check(state == State.NEW) { "Initialize once; create a new FusedLocationClient" }
            this.listener = listener
            state = State.INITIALIZING
            try {
                checkSupported()
                initializationAttempted = true
                ApiLog.call(
                    "Location",
                    "initialize",
                    { mapOf("clientId" to clientId) }) { api.initialize() }
                state = State.INITIALIZED
            } catch (cause: Exception) {
                state = State.FAILED
                ApiLog.error("Location", "initialize", cause)
                listener.onInitializationFailed(cause)
                return
            }
        }
        ApiLog.event("Location", "initialize", "returned", "I")
        listener.onInitialized()
    }

    private fun checkSupported() {
        if (!api.isSupported()) throw UnsupportedOperationException("FusedLocation is not supported")
    }

    override fun getLastKnownLocation() = request("getLastKnownLocation") {
        api.getLastKnownLocation(object : FusedLocationListener {
            override fun onChanged(location: LocationData?) =
                deliver {
                    ApiLog.event(
                        "Location",
                        "onLastKnownLocation",
                        "callback"
                    ) { mapOf("clientId" to clientId, "location" to sdkFields(location)) }
                    onLastKnownLocation(location)
                }

            override fun onError(exception: Exception) = deliver {
                ApiLog.event("Location", "onError", "callback") {
                    mapOf(
                        "clientId" to clientId,
                        "exception" to sdkFields(exception)
                    )
                }
                onError(exception)
            }
        })
    }

    override fun registerFusedLocationCallback() = request("registerFusedLocationCallback") {
        check(!subscriptionAttempted) { "Location updates already registered or require cleanup" }
        subscriptionAttempted = true
        subscribed = true
        try {
            api.registerFusedLocationCallback(updates)
        } catch (cause: Exception) {
            subscribed = false
            throw cause
        }
    }

    override fun unregisterFusedLocationCallback() = request("unregisterFusedLocationCallback") {
        subscribed = false
        if (subscriptionAttempted) {
            ApiLog.call(
                "Location",
                "unregisterFusedLocationCallback",
                { mapOf("clientId" to clientId) }) { api.unregisterFusedLocationCallback(updates) }
            subscriptionAttempted = false
        }
    }

    override fun showLocationAccessBlockedPopup() =
        request("showLocationAccessBlockedPopup") { api.showLocationAccessBlockedPopup() }

    private inline fun request(operation: String, block: () -> Unit) {
        ApiLog.call("Location", operation, { mapOf("clientId" to clientId) }) {
            synchronized(lock) {
                check(state == State.INITIALIZED) { "FusedLocationClient is not initialized: $state" }
                block()
            }
        }
    }

    private inline fun deliver(
        updatesOnly: Boolean = false,
        block: FusedLocationClientListener.() -> Unit
    ) {
        val target = synchronized(lock) {
            if (state == State.INITIALIZED && (!updatesOnly || subscribed)) listener else null
        }
        if (target == null) ApiLog.event(
            "Location",
            "deliver",
            "ignored_callback"
        ) { mapOf("clientId" to clientId, "state" to state.name) }
        target?.block()
    }

    override fun release() {
        ApiLog.event("Location", "release", "start", "I")
        synchronized(lock) {
            if (state == State.RELEASED) return
            state = State.RELEASED
            listener = null
            subscribed = false
            try {
                if (subscriptionAttempted) ApiLog.call(
                    "Location",
                    "unregisterFusedLocationCallback",
                    { mapOf("clientId" to clientId) }) { api.unregisterFusedLocationCallback(updates) }
            } finally {
                subscriptionAttempted = false
                if (initializationAttempted) ApiLog.call(
                    "Location",
                    "release",
                    { mapOf("clientId" to clientId) }) { api.release() }
            }
        }
    }
}
