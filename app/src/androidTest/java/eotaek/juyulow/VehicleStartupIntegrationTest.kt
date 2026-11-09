package eotaek.juyulow

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eotaek.juyulow.core.data.VehicleInfoRepository
import eotaek.juyulow.core.model.VehicleFetchStatus
import eotaek.juyulow.core.model.VehicleProperty
import eotaek.juyulow.core.model.VehicleValueOrigin
import eotaek.juyulow.vehicle.VehicleRuntime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class VehicleStartupIntegrationTest {
    @Test
    fun availableStartupFieldsCompleteWithExplicitOrigins() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val energyGranted = VehicleRuntime.hasEnergyPermission(context)
        val repository = GlobalContext.get().get<VehicleInfoRepository>()
        val fields = withTimeout(20_000.milliseconds) {
            repository.fields.first { snapshot ->
                snapshot.all { (property, field) ->
                    (property == VehicleProperty.FUEL_LEVEL && !energyGranted) || field.status != VehicleFetchStatus.PENDING
                }
            }
        }
        assertEquals(VehicleProperty.entries.toSet(), fields.keys)
        fields.forEach { (property, field) ->
            Log.i("VehicleStartupTest", "$property unit=${property.unit} field=$field")
            when (field.status) {
                VehicleFetchStatus.SUCCESS -> {
                    assertEquals(VehicleValueOrigin.FETCH, field.origin)
                    assertNotNull(field.value)
                    assertNull(field.failure)
                }

                VehicleFetchStatus.FAILED -> assertNotNull(field.failure)
                VehicleFetchStatus.PENDING -> assertEquals(VehicleProperty.FUEL_LEVEL, property)
            }
        }
    }
}
