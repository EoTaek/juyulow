package eotaek.juyulow.vehicle

import eotaek.juyulow.core.data.InMemoryVehicleInfoRepository
import eotaek.juyulow.core.data.VehicleDefaultsRepository
import eotaek.juyulow.core.model.RefuelSettings
import eotaek.juyulow.core.model.RefuelValueSource
import eotaek.juyulow.core.model.VehicleFailureReason
import eotaek.juyulow.core.model.VehicleFetchStatus
import eotaek.juyulow.core.model.VehicleProperty
import eotaek.juyulow.core.model.VehicleValue
import eotaek.juyulow.core.model.VehicleValueOrigin
import eotaek.juyulow.core.model.resolveRefuelConditions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleStartupTest {
    private class Defaults : VehicleDefaultsRepository {
        override val defaults = MutableStateFlow(
            mapOf(
                VehicleProperty.CUMULATIVE_ECONOMY to VehicleValue.Number(100.0 / 12),
                VehicleProperty.FUEL_CAPACITY to VehicleValue.Number(50_000.0),
            )
        )

        override suspend fun save(property: VehicleProperty, value: VehicleValue?) =
            error("Fetch must not persist defaults")
    }

    private class Source(val result: suspend (VehicleProperty) -> VehicleValue?) :
        StartupVehicleSource {
        val reads = mutableListOf<VehicleProperty>()
        var closed = false
        override suspend fun read(property: VehicleProperty): VehicleValue? {
            reads += property; return result(property)
        }

        override fun close() {
            closed = true
        }
    }

    @Test
    fun partialSuccessUsesDefaultsWithoutOverwritingSuccessfulValuesOrRetrying() = runTest {
        val defaults = Defaults()
        val repo = InMemoryVehicleInfoRepository()
        val logs = mutableListOf<String>()
        val pleos =
            Source { if (it == VehicleProperty.CUMULATIVE_ECONOMY) VehicleValue.Number(8.5) else null }
        val aaos = Source { throw VehicleReadException(VehicleFailureReason.UNSUPPORTED, "config") }
        val startup = VehicleStartup(
            backgroundScope,
            repo,
            defaults,
            pleos,
            aaos,
            true,
            true,
            { msg, _ -> logs += msg },
            { 123L })
        startup.start(); startup.start(); runCurrent()
        assertEquals(6, pleos.reads.size + aaos.reads.size)
        assertTrue(pleos.closed && aaos.closed)
        assertEquals(
            VehicleValue.Number(8.5),
            repo.fields.value.getValue(VehicleProperty.CUMULATIVE_ECONOMY).value
        )
        assertEquals(
            123L,
            repo.fields.value.getValue(VehicleProperty.CUMULATIVE_ECONOMY).fetchedAtMillis
        )
        assertEquals(
            VehicleValueOrigin.DEFAULT,
            repo.fields.value.getValue(VehicleProperty.FUEL_CAPACITY).origin
        )
        assertEquals(
            VehicleValueOrigin.UNSET,
            repo.fields.value.getValue(VehicleProperty.FUEL_LEVEL).origin
        )
        assertEquals(5, logs.size)
        defaults.defaults.value =
            mapOf(VehicleProperty.CUMULATIVE_ECONOMY to VehicleValue.Number(20.0))
        runCurrent()
        assertEquals(
            VehicleValue.Number(8.5),
            repo.fields.value.getValue(VehicleProperty.CUMULATIVE_ECONOMY).value
        )
        assertNull(repo.fields.value.getValue(VehicleProperty.FUEL_CAPACITY).value)
    }

    @Test
    fun permissionWaitDoesNotBlockOtherFieldsAndDenialIsTerminal() = runTest {
        val repo = InMemoryVehicleInfoRepository()
        val source = Source { VehicleValue.Number(25.0) }
        val startup = VehicleStartup(
            backgroundScope,
            repo,
            Defaults(),
            source,
            source,
            true,
            false,
            { _, _ -> })
        startup.start(); runCurrent()
        assertEquals(
            VehicleFetchStatus.PENDING,
            repo.fields.value.getValue(VehicleProperty.FUEL_LEVEL).status
        )
        assertEquals(5, source.reads.size)
        assertTrue(startup.claimPermissionRequest())
        assertFalse(startup.claimPermissionRequest())
        startup.onEnergyPermissionResult(false); runCurrent()
        assertEquals(
            VehicleFailureReason.PERMISSION,
            repo.fields.value.getValue(VehicleProperty.FUEL_LEVEL).failure?.reason
        )
        startup.onEnergyPermissionResult(true); runCurrent()
        assertFalse(VehicleProperty.FUEL_LEVEL in source.reads)
    }

    @Test
    fun failedLevelIsLoggedEvenWhenPercentProvidesAutomaticVolume() = runTest {
        val repo = InMemoryVehicleInfoRepository()
        val logs = mutableListOf<String>()
        val pleos =
            Source { if (it == VehicleProperty.FUEL_PERCENT) VehicleValue.Number(25.0) else null }
        val aaos = Source {
            when (it) {
                VehicleProperty.FUEL_CAPACITY -> VehicleValue.Number(50_000.0)
                VehicleProperty.FUEL_LEVEL -> throw VehicleReadException(
                    VehicleFailureReason.UNAVAILABLE,
                    "get"
                )

                VehicleProperty.FUEL_TYPES -> VehicleValue.FuelTypes(listOf(10))
                else -> null
            }
        }
        VehicleStartup(
            backgroundScope,
            repo,
            Defaults(),
            pleos,
            aaos,
            true,
            true,
            { msg, _ -> logs += msg }).start()
        runCurrent()
        val conditions = resolveRefuelConditions(RefuelSettings(), repo.fields.value)
        assertEquals(37.5, conditions.liters, 0.0)
        assertEquals(RefuelValueSource.FUEL_PERCENT, conditions.litersSource)
        assertEquals(
            1,
            logs.count { it.contains("property=FUEL_LEVEL ") && it.contains("reason=UNAVAILABLE") })
    }

    @Test
    fun permissionGrantReadsOnlyThePendingField() = runTest {
        val source = Source { VehicleValue.Number(1.0) }
        val startup = VehicleStartup(
            backgroundScope,
            InMemoryVehicleInfoRepository(),
            Defaults(),
            source,
            source,
            true,
            false,
            { _, _ -> })
        startup.start(); runCurrent()
        startup.onEnergyPermissionResult(true); runCurrent()
        assertEquals(6, source.reads.size)
        assertEquals(1, source.reads.count { it == VehicleProperty.FUEL_LEVEL })
    }

    @Test
    fun nonAutomotiveDoesNotTouchEitherSdk() = runTest {
        val repo = InMemoryVehicleInfoRepository()
        val source = Source { error("Must not read") }
        val startup = VehicleStartup(
            backgroundScope,
            repo,
            Defaults(),
            source,
            source,
            false,
            false,
            { _, _ -> })
        startup.start(); runCurrent()
        assertTrue(source.reads.isEmpty())
        assertTrue(repo.fields.value.values.all { it.failure?.reason == VehicleFailureReason.UNSUPPORTED })
        assertFalse(startup.needsEnergyPermission)
    }

    @Test
    fun stageDeadlineIsFiveSeconds() = runTest {
        var failure: VehicleReadException? = null
        backgroundScope.launch {
            try {
                vehicleStage("get") { awaitCancellation() }
            } catch (error: VehicleReadException) {
                failure = error
            }
        }
        runCurrent(); advanceTimeBy(4_999.milliseconds); runCurrent()
        assertNull(failure)
        advanceTimeBy(1.milliseconds); runCurrent()
        assertEquals(VehicleFailureReason.TIMEOUT, failure?.reason)
        assertEquals("get", failure?.stage)
    }
}
