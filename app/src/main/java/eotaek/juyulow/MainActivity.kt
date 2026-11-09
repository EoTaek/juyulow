package eotaek.juyulow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import eotaek.juyulow.core.design.theme.JUYULOWTheme
import eotaek.juyulow.core.model.VehicleFailureReason
import eotaek.juyulow.vehicle.VehicleReadException
import eotaek.juyulow.vehicle.VehicleRuntime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {
    private val vehicleRuntime: VehicleRuntime by inject()
    private val energyPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            vehicleRuntime.startup.onEnergyPermissionResult(it)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive && vehicleRuntime.startup.needsEnergyPermission) {
                    if (VehicleRuntime.hasEnergyPermission(this@MainActivity)) {
                        vehicleRuntime.startup.onEnergyPermissionResult(true)
                    } else if (vehicleRuntime.canRequestEnergyPermission() && vehicleRuntime.startup.claimPermissionRequest()) {
                        try {
                            energyPermission.launch(VehicleRuntime.ENERGY_PERMISSION)
                        } catch (error: RuntimeException) {
                            vehicleRuntime.startup.onEnergyPermissionUnavailable(
                                VehicleReadException(
                                    VehicleFailureReason.PERMISSION,
                                    "permission-request",
                                    error
                                )
                            )
                        }
                    }
                    delay(500.milliseconds)
                }
            }
        }
        setContent {
            JUYULOWTheme {
                JuyulowApp()
            }
        }
    }
}
