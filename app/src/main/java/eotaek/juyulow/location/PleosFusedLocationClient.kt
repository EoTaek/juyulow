package eotaek.juyulow.location

import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context

class PleosFusedLocationClient internal constructor(private val api: FusedLocationApi) :
    FusedLocationClient {
    constructor(context: Context) : this(AndroidFusedLocationApi(context))

    private enum class State { NEW, INITIALIZING, INITIALIZED, FAILED, RELEASED }

    private val lock = Any()
    private var state = State.NEW
    private var listener: FusedLocationClientListener? = null
    private var subscriptionAttempted = false
    private var subscribed = false
    private var initializationAttempted = false

    private val updates = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) =
            deliver(updatesOnly = true) { onChanged(location) }

        override fun onError(exception: Exception) =
            deliver(updatesOnly = true) { onError(exception) }
    }

    override fun isSupported(): Boolean = synchronized(lock) {
        check(state != State.RELEASED) { "FusedLocationClient is released" }
        api.isSupported()
    }

    override fun initialize(listener: FusedLocationClientListener) {
        synchronized(lock) {
            check(state == State.NEW) { "Initialize once; create a new FusedLocationClient" }
            this.listener = listener
            state = State.INITIALIZING
            try {
                checkSupported()
                initializationAttempted = true
                api.initialize()
                state = State.INITIALIZED
            } catch (cause: Exception) {
                state = State.FAILED
                listener.onInitializationFailed(cause)
                return
            }
        }
        listener.onInitialized()
    }

    private fun checkSupported() {
        if (!api.isSupported()) throw UnsupportedOperationException("FusedLocation is not supported")
    }

    override fun getLastKnownLocation() = request {
        api.getLastKnownLocation(object : FusedLocationListener {
            override fun onChanged(location: LocationData?) =
                deliver { onLastKnownLocation(location) }

            override fun onError(exception: Exception) = deliver { onError(exception) }
        })
    }

    override fun registerFusedLocationCallback() = request {
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

    override fun unregisterFusedLocationCallback() = request {
        subscribed = false
        if (subscriptionAttempted) {
            api.unregisterFusedLocationCallback(updates)
            subscriptionAttempted = false
        }
    }

    override fun showLocationAccessBlockedPopup() = request { api.showLocationAccessBlockedPopup() }

    private inline fun request(block: () -> Unit) = synchronized(lock) {
        check(state == State.INITIALIZED) { "FusedLocationClient is not initialized: $state" }
        block()
    }

    private inline fun deliver(
        updatesOnly: Boolean = false,
        block: FusedLocationClientListener.() -> Unit
    ) {
        val target = synchronized(lock) {
            if (state == State.INITIALIZED && (!updatesOnly || subscribed)) listener else null
        }
        target?.block()
    }

    override fun release() {
        synchronized(lock) {
            if (state == State.RELEASED) return
            state = State.RELEASED
            listener = null
            subscribed = false
            try {
                if (subscriptionAttempted) api.unregisterFusedLocationCallback(updates)
            } finally {
                subscriptionAttempted = false
                if (initializationAttempted) api.release()
            }
        }
    }
}
