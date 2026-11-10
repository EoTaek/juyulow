package eotaek.juyulow.vehicle

import eotaek.juyulow.core.model.VehicleProperty
import eotaek.juyulow.core.model.VehicleValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

internal interface AaosVehicleApi {
    suspend fun connect()

    /** Blocking API: called on IO, never Main. */
    fun read(property: VehicleProperty): VehicleValue?
    fun canRequestPermission(): Boolean
    fun disconnect()
}

internal class AaosStartupSource(
    private val scope: CoroutineScope,
    private val factory: () -> AaosVehicleApi,
    private val diagnostic: (String, Throwable?) -> Unit,
) : StartupVehicleSource {
    private var api: AaosVehicleApi? = null
    private var connection: Deferred<Unit>? = null
    private var reads = 0
    private var closing = false

    private suspend fun connected(): AaosVehicleApi {
        check(!closing)
        if (connection == null) {
            val created = factory().also { api = it }
            connection = scope.async { created.connect() }
        }
        vehicleStage("connect") { connection!!.await() }
        return checkNotNull(api)
    }

    override suspend fun read(property: VehicleProperty): VehicleValue? {
        val connected = connected()
        reads++
        // Await timeout must not disconnect a synchronous read still running in the IO pool.
        val operation = scope.async(Dispatchers.IO) { connected.read(property) }
        operation.invokeOnCompletion { scope.launch { reads--; releaseIfIdle() } }
        return vehicleStage("get") { operation.await() }
    }

    suspend fun canRequestPermission(): Boolean = vehicleStage("restrictions") {
        connected().canRequestPermission()
    }

    override fun close() {
        closing = true; releaseIfIdle()
    }

    private fun releaseIfIdle() {
        if (!closing || reads != 0) return
        val owned = api ?: return
        api = null
        connection?.cancel()
        try {
            owned.disconnect()
        } catch (error: Exception) {
            diagnostic("provider=AAOS stage=release failed", error)
        }
    }
}
