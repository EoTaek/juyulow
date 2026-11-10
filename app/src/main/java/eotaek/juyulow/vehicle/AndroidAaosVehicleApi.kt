package eotaek.juyulow.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyConfig
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.car.hardware.property.PropertyNotAvailableException
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import eotaek.juyulow.core.model.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class AndroidAaosVehicleApi(private val context: Context) : AaosVehicleApi {
    private var car: Car? = null
    @Volatile
    private var manager: CarPropertyManager? = null
    private var closed = false

    override suspend fun connect(): Unit = suspendCancellableCoroutine { continuation ->
        try {
            car = Car.createCar(
                context,
                Handler(Looper.getMainLooper()),
                Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT
            ) { connected, ready ->
                if (closed) return@createCar
                if (ready) {
                    manager = connected.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                    if (continuation.isActive) {
                        if (manager != null) continuation.resume(Unit)
                        else continuation.resumeWithException(
                            VehicleReadException(
                                VehicleFailureReason.CONNECTION,
                                "connect"
                            )
                        )
                    }
                } else {
                    manager = null
                    if (continuation.isActive) continuation.resumeWithException(
                        VehicleReadException(VehicleFailureReason.CONNECTION, "connect")
                    )
                }
            }
            if (car == null && continuation.isActive) continuation.resumeWithException(
                VehicleReadException(VehicleFailureReason.CONNECTION, "connect")
            )
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resumeWithException(
                VehicleReadException(VehicleFailureReason.CONNECTION, "connect", error)
            )
        }
    }

    @Suppress("DEPRECATION") // STATUS_* also covers older AAOS releases supported by minSdk 30.
    override fun read(property: VehicleProperty): VehicleValue? {
        val id = when (property) {
            VehicleProperty.FUEL_CAPACITY -> VehiclePropertyIds.INFO_FUEL_CAPACITY
            VehicleProperty.FUEL_LEVEL -> VehiclePropertyIds.FUEL_LEVEL
            VehicleProperty.FUEL_TYPES -> VehiclePropertyIds.INFO_FUEL_TYPE
            else -> error("Not an AAOS fuel property: $property")
        }
        val permission =
            if (property == VehicleProperty.FUEL_LEVEL) Car.PERMISSION_ENERGY else Car.PERMISSION_CAR_INFO
        if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
            throw VehicleReadException(VehicleFailureReason.PERMISSION, "permission")
        val properties =
            manager ?: throw VehicleReadException(VehicleFailureReason.CONNECTION, "get")
        val config = properties.getCarPropertyConfig(id)
            ?: throw VehicleReadException(VehicleFailureReason.UNSUPPORTED, "config")
        if (config.access != CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ &&
            config.access != CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE
        )
            throw VehicleReadException(VehicleFailureReason.UNSUPPORTED, "config")
        val result = try {
            properties.getProperty<Any>(id, 0)
        } catch (error: PropertyNotAvailableException) {
            throw VehicleReadException(VehicleFailureReason.UNAVAILABLE, "get", error)
        }
        if (result == null) throw VehicleReadException(VehicleFailureReason.UNAVAILABLE, "get")
        when (result.status) {
            CarPropertyValue.STATUS_UNAVAILABLE -> throw VehicleReadException(
                VehicleFailureReason.UNAVAILABLE,
                "status"
            )

            CarPropertyValue.STATUS_AVAILABLE -> Unit
            else -> throw VehicleReadException(VehicleFailureReason.ERROR, "status")
        }
        return when (val raw = result.value) {
            is Number -> VehicleValue.Number(raw.toDouble())
            is Array<*> -> raw.map {
                (it as? Int) ?: throw VehicleReadException(
                    VehicleFailureReason.INVALID_VALUE,
                    "value"
                )
            }
                .let(VehicleValue::FuelTypes)

            is IntArray -> VehicleValue.FuelTypes(raw.toList())
            else -> null
        }
    }

    override fun canRequestPermission(): Boolean {
        val restrictions =
            car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        return restrictions?.currentCarUxRestrictions?.isRequiresDistractionOptimization == false
    }

    override fun disconnect() {
        closed = true
        manager = null
        car?.disconnect()
        car = null
    }
}
