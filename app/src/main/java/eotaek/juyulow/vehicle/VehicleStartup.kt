package eotaek.juyulow.vehicle

import eotaek.juyulow.core.data.InMemoryVehicleInfoRepository
import eotaek.juyulow.core.data.VehicleDefaultsRepository
import eotaek.juyulow.core.model.VehicleFailure
import eotaek.juyulow.core.model.VehicleFailureReason
import eotaek.juyulow.core.model.VehicleProperty
import eotaek.juyulow.core.model.VehicleProvider
import eotaek.juyulow.core.model.VehicleValue
import eotaek.juyulow.core.model.accepts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

internal interface StartupVehicleSource {
    suspend fun read(property: VehicleProperty): VehicleValue?
    fun close()
}

internal class VehicleReadException(
    val reason: VehicleFailureReason,
    val stage: String,
    cause: Throwable? = null,
) : Exception(cause?.message ?: reason.name, cause)

/** Per-stage deadline. Cancellation never implies that a native/IPC request was cancelled. */
internal suspend fun <T> vehicleStage(name: String, block: suspend () -> T): T = try {
    withTimeout(5_000.milliseconds) { block() }
} catch (error: TimeoutCancellationException) {
    throw VehicleReadException(VehicleFailureReason.TIMEOUT, name, error)
} catch (error: CancellationException) {
    throw error
} catch (error: VehicleReadException) {
    throw error
} catch (error: Exception) {
    val reason = when (error) {
        is SecurityException -> VehicleFailureReason.PERMISSION
        is UnsupportedOperationException -> VehicleFailureReason.UNSUPPORTED
        else -> VehicleFailureReason.ERROR
    }
    throw VehicleReadException(reason, name, error)
}

/** Main-dispatcher, process-scoped owner; Activity only delivers the permission result. */
internal class VehicleStartup(
    private val scope: CoroutineScope,
    private val repository: InMemoryVehicleInfoRepository,
    private val defaults: VehicleDefaultsRepository,
    private val pleos: StartupVehicleSource,
    private val aaos: StartupVehicleSource,
    private val automotive: Boolean,
    energyGranted: Boolean,
    private val diagnostic: (String, Throwable?) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var started = false
    private var permissionRequested = false
    private val energyPermission = CompletableDeferred<VehicleReadException?>().also {
        if (energyGranted || !automotive) it.complete(null)
    }

    val needsEnergyPermission: Boolean get() = automotive && !energyPermission.isCompleted && !permissionRequested

    fun claimPermissionRequest(): Boolean {
        if (!needsEnergyPermission) return false
        permissionRequested = true
        return true
    }

    fun onEnergyPermissionResult(granted: Boolean) {
        energyPermission.complete(
            if (granted) null else VehicleReadException(
                VehicleFailureReason.PERMISSION,
                "permission"
            )
        )
    }

    fun onEnergyPermissionUnavailable(error: VehicleReadException) {
        energyPermission.complete(error)
    }

    fun start() {
        if (started) return
        started = true
        scope.launch {
            try {
                defaults.defaults.collect(repository::applyDefaults)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                diagnostic("defaults stage=load failed; unset fields remain unset", error)
            }
        }
        VehicleProvider.entries.forEach { provider ->
            scope.launch {
                val source = if (provider == VehicleProvider.PLEOS) pleos else aaos
                try {
                    supervisorScope {
                        VehicleProperty.entries.filter { it.provider == provider }.map { property ->
                            launch {
                                var raw: VehicleValue? = null
                                val failure = try {
                                    if (!automotive) throw VehicleReadException(
                                        VehicleFailureReason.UNSUPPORTED,
                                        "platform"
                                    )
                                    if (property == VehicleProperty.FUEL_LEVEL) energyPermission.await()
                                        ?.let { throw it }
                                    raw = source.read(property)
                                    if (!property.accepts(raw)) throw VehicleReadException(
                                        VehicleFailureReason.INVALID_VALUE,
                                        "value"
                                    )
                                    null
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    val read = error as? VehicleReadException
                                    VehicleFailure(
                                        read?.reason ?: VehicleFailureReason.ERROR,
                                        read?.stage ?: "read",
                                        error.message
                                    )
                                }
                                repository.complete(property, raw, now(), failure)
                                if (failure != null) diagnostic(
                                    "property=$property provider=$provider stage=${failure.stage} reason=${failure.reason} " +
                                            "fallback=${repository.fields.value.getValue(property).origin} detail=${failure.message}",
                                    null
                                )
                            }
                        }.joinAll()
                    }
                } finally {
                    try {
                        source.close()
                    } catch (error: Exception) {
                        diagnostic("provider=$provider stage=release failed", error)
                    }
                }
            }
        }
    }
}
