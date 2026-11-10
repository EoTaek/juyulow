package eotaek.juyulow.vehicle

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import eotaek.juyulow.core.data.InMemoryVehicleInfoRepository
import eotaek.juyulow.core.data.VehicleDefaultsRepository
import eotaek.juyulow.core.model.VehicleFailureReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Android entry point. Avoid resolving AAOS implementation classes on non-Automotive devices. */
internal class VehicleRuntime(
    context: Context,
    repository: InMemoryVehicleInfoRepository,
    defaults: VehicleDefaultsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @SuppressLint("PrivateApi") // android.car.Car is a public optional API; only test class presence, not hidden members.
    private val automotive =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) &&
                runCatching {
                    Class.forName(
                        "android.car.Car",
                        false,
                        context.classLoader
                    )
                }.isSuccess
    private val diagnostic: (String, Throwable?) -> Unit = ::logVehicleStartup
    private val aaos =
        AaosStartupSource(scope, { AndroidAaosVehicleApi(context.applicationContext) }, diagnostic)
    val startup = VehicleStartup(
        scope, repository, defaults,
        PleosStartupSource(
            scope,
            { PleosVehicleFuelClient(context.applicationContext) },
            diagnostic
        ),
        aaos, automotive, hasEnergyPermission(context), diagnostic
    )

    suspend fun canRequestEnergyPermission(): Boolean = try {
        startup.needsEnergyPermission && aaos.canRequestPermission()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        startup.onEnergyPermissionUnavailable(
            error as? VehicleReadException
                ?: VehicleReadException(
                    VehicleFailureReason.CONNECTION,
                    "permission-connect",
                    error
                )
        )
        false
    }

    companion object {
        const val ENERGY_PERMISSION = "android.car.permission.CAR_ENERGY"
        fun hasEnergyPermission(context: Context) =
            context.checkSelfPermission(ENERGY_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }
}
