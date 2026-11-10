package eotaek.juyulow.vehicle

import eotaek.juyulow.core.model.VehicleFailureReason
import eotaek.juyulow.core.model.VehicleProperty
import eotaek.juyulow.core.model.VehicleValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class AaosStartupSourceTest {
    @Test
    fun connectionTimeoutDisconnectsOnceAndDoesNotRead() = runTest {
        var disconnects = 0
        var reads = 0
        val api = object : AaosVehicleApi {
            override suspend fun connect() {
                awaitCancellation()
            }

            override fun read(property: VehicleProperty): VehicleValue? {
                reads++; return null
            }

            override fun canRequestPermission() = false
            override fun disconnect() {
                disconnects++
            }
        }
        val source = AaosStartupSource(backgroundScope, { api }, { _, _ -> })
        var failure: VehicleReadException? = null
        backgroundScope.launch {
            try {
                source.read(VehicleProperty.FUEL_CAPACITY)
            } catch (error: VehicleReadException) {
                failure = error
            } finally {
                source.close()
            }
        }
        runCurrent(); advanceTimeBy(5_000.milliseconds); runCurrent()
        assertEquals(VehicleFailureReason.TIMEOUT, failure?.reason)
        assertEquals("connect", failure?.stage)
        assertEquals(0, reads)
        assertEquals(1, disconnects)
        source.close()
        assertEquals(1, disconnects)
    }

    @Test
    fun restrictionsRequirePositiveConfirmationAndReuseConnection() = runTest {
        var connections = 0
        var restricted = true
        val api = object : AaosVehicleApi {
            override suspend fun connect() {
                connections++
            }

            override fun read(property: VehicleProperty): VehicleValue? = null
            override fun canRequestPermission() = !restricted
            override fun disconnect() = Unit
        }
        val source = AaosStartupSource(backgroundScope, { api }, { _, _ -> })
        val first = async { source.canRequestPermission() }
        runCurrent()
        assertFalse(first.await())
        restricted = false
        val second = async { source.canRequestPermission() }
        runCurrent()
        assertTrue(second.await())
        assertEquals(1, connections)
        source.close()
    }
}
