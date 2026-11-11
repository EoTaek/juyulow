package eotaek.juyulow.navigation

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** This integration is available only while the platform explicitly permits the parked UI. */
internal suspend fun canInteractWithNavigation(context: Context): Boolean =
    withContext(Dispatchers.IO) {
        var car: Car? = null
        try {
            car = Car.createCar(context)
            val manager =
                car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            manager?.currentCarUxRestrictions?.isRequiresDistractionOptimization == false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        } finally {
            car?.disconnect()
        }
    }
