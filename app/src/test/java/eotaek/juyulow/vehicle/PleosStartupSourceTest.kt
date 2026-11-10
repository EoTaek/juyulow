package eotaek.juyulow.vehicle

import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import eotaek.juyulow.core.model.VehicleFailureReason
import eotaek.juyulow.core.model.VehicleProperty
import eotaek.juyulow.core.model.VehicleValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class PleosStartupSourceTest {
    private class Client : VehicleFuelClient {
        lateinit var listener: VehicleFuelClientListener
        var releases = 0
        var gets = 0
        override fun initialize(listener: VehicleFuelClientListener) {
            this.listener = listener; listener.onInitialized()
        }

        override fun checkCumulativeFuelEconomyCapabilities() = Unit
        override fun checkCurrentFuelEconomyCapabilities() = Unit
        override fun checkFuelPercentCapabilities() = Unit
        override fun getCumulativeFuelEconomy() {
            gets++
        }

        override fun getCurrentFuelEconomy() {
            gets++
        }

        override fun getFuelPercent() {
            gets++
        }

        override fun release() {
            releases++
        }

        override fun registerCumulativeFuelEconomy() = error("No subscriptions")
        override fun registerCurrentFuelEconomy() = error("No subscriptions")
        override fun registerFuelPercent() = error("No subscriptions")
        override fun unregisterCumulativeFuelEconomy() = error("No subscriptions")
        override fun unregisterCurrentFuelEconomy() = error("No subscriptions")
        override fun unregisterFuelPercent() = error("No subscriptions")
    }

    @Test
    fun timedOutCapabilityRetainsClientUntilLateCallbackAndDoesNotIssueGet() = runTest {
        val client = Client()
        val source = PleosStartupSource(backgroundScope, { client }, { _, _ -> })
        var failure: VehicleReadException? = null
        backgroundScope.launch {
            try {
                source.read(VehicleProperty.CUMULATIVE_ECONOMY)
            } catch (error: VehicleReadException) {
                failure = error
            } finally {
                source.close()
            }
        }
        runCurrent(); advanceTimeBy(5_000.milliseconds); runCurrent()
        assertEquals(VehicleFailureReason.TIMEOUT, failure?.reason)
        assertEquals(0, client.releases)
        client.listener.onCapabilities(
            FuelProperty.CUMULATIVE_ECONOMY,
            CapabilityConfig(AccessType.READ)
        )
        runCurrent()
        assertEquals(0, client.gets)
        assertEquals(1, client.releases)
        source.close()
        assertEquals(1, client.releases)
    }

    @Test
    fun readCapabilityThenValuePreservesUnitsAndWaitsForGetBeforeRelease() = runTest {
        val client = Client()
        val source = PleosStartupSource(backgroundScope, { client }, { _, _ -> })
        var result: VehicleValue? = null
        backgroundScope.launch {
            result = source.read(VehicleProperty.CURRENT_ECONOMY); source.close()
        }
        runCurrent()
        client.listener.onCapabilities(
            FuelProperty.CURRENT_ECONOMY,
            CapabilityConfig(AccessType.READ_WRITE)
        )
        runCurrent()
        assertEquals(1, client.gets)
        assertEquals(0, client.releases)
        client.listener.onFuelEconomy(FuelProperty.CURRENT_ECONOMY, 7.25f)
        runCurrent()
        assertEquals(VehicleValue.Number(7.25), result)
        assertEquals(1, client.releases)
    }

    @Test
    fun writeOnlyCapabilityNeverReads() = runTest {
        val client = Client()
        val source = PleosStartupSource(backgroundScope, { client }, { _, _ -> })
        var reason: VehicleFailureReason? = null
        backgroundScope.launch {
            try {
                source.read(VehicleProperty.FUEL_PERCENT)
            } catch (error: VehicleReadException) {
                reason = error.reason
            } finally {
                source.close()
            }
        }
        runCurrent()
        client.listener.onCapabilities(
            FuelProperty.FUEL_PERCENT,
            CapabilityConfig(AccessType.WRITE)
        )
        runCurrent()
        assertEquals(VehicleFailureReason.UNSUPPORTED, reason)
        assertEquals(0, client.gets)
        assertEquals(1, client.releases)
    }

    @Test
    fun getTimeoutIgnoresLateValueButReleasesOnlyAfterCallback() = runTest {
        val client = Client()
        val source = PleosStartupSource(backgroundScope, { client }, { _, _ -> })
        var result: VehicleValue? = null
        var failure: VehicleReadException? = null
        backgroundScope.launch {
            try {
                result = source.read(VehicleProperty.FUEL_PERCENT)
            } catch (error: VehicleReadException) {
                failure = error
            } finally {
                source.close()
            }
        }
        runCurrent()
        client.listener.onCapabilities(FuelProperty.FUEL_PERCENT, CapabilityConfig(AccessType.READ))
        runCurrent(); advanceTimeBy(5_000.milliseconds); runCurrent()
        assertEquals("get", failure?.stage)
        assertEquals(0, client.releases)
        client.listener.onFuelPercent(42)
        runCurrent()
        assertNull(result)
        assertEquals(1, client.releases)
    }

    @Test
    fun documentedSdkErrorMessageMapsPermissionFailure() = runTest {
        val client = Client()
        val source = PleosStartupSource(backgroundScope, { client }, { _, _ -> })
        var failure: VehicleReadException? = null
        backgroundScope.launch {
            try {
                source.read(VehicleProperty.FUEL_PERCENT)
            } catch (error: VehicleReadException) {
                failure = error
            } finally {
                source.close()
            }
        }
        runCurrent()
        client.listener.onError(FuelProperty.FUEL_PERCENT, Exception("ERROR_SECURITY_PERMISSION"))
        runCurrent()
        assertEquals(VehicleFailureReason.PERMISSION, failure?.reason)
        assertEquals("capability", failure?.stage)
        assertEquals(1, client.releases)
    }
}
