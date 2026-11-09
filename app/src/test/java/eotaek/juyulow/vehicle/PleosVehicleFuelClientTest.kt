package eotaek.juyulow.vehicle

import ai.pleos.playground.vehicle.api.Fuel
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.listener.FuelEconomyListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.lang.reflect.Proxy

class PleosVehicleFuelClientTest {
    private val api = FakeApi()
    private val client = PleosVehicleFuelClient(api)
    private val events = mutableListOf<Any?>()
    private val listener = object : VehicleFuelClientListener {
        override fun onInitialized() {
            events += "initialized"
        }

        override fun onInitializationFailed(cause: Exception) {
            events += cause
        }

        override fun onCapabilities(property: FuelProperty, capability: CapabilityConfig) {
            events.add(property to capability)
        }

        override fun onFuelEconomy(property: FuelProperty, litersPer100Km: Float?) {
            events.add(property to litersPer100Km)
        }

        override fun onFuelPercent(percent: Int?) {
            events += percent
        }

        override fun onError(property: FuelProperty, cause: Exception) {
            events.add(property to cause)
        }
    }

    @Test
    fun `lifecycle gates requests and acquires Fuel only after initialization`() {
        assertThrows(IllegalStateException::class.java) { client.getFuelPercent() }
        client.initialize(listener)
        assertEquals(listOf("initialize", "getFuel"), api.calls)
        assertEquals(listOf("initialized"), events)
        assertThrows(IllegalStateException::class.java) { client.initialize(listener) }
        client.release()
        client.release()
        assertEquals("release", api.calls.last())
        assertThrows(IllegalStateException::class.java) { client.getCurrentFuelEconomy() }
    }

    @Test
    fun `initialization and domain acquisition failures retain release obligation`() {
        for (method in listOf("initialize", "getFuel")) {
            val fake = FakeApi()
            val session = PleosVehicleFuelClient(fake)
            val failure = IllegalStateException(method)
            fake.failures[method] = failure
            val failures = mutableListOf<Exception>()
            session.initialize(object : VehicleFuelClientListener {
                override fun onInitializationFailed(cause: Exception) {
                    failures += cause
                }
            })
            assertEquals(listOf(failure), failures)
            assertThrows(IllegalStateException::class.java) { session.checkFuelPercentCapabilities() }
            session.release()
            assertEquals("release", fake.calls.last())
        }
    }

    @Test
    fun `capabilities preserve READ NONE and error independently for each property`() {
        client.initialize(listener)
        client.checkCumulativeFuelEconomyCapabilities()
        client.checkCurrentFuelEconomyCapabilities()
        client.checkFuelPercentCapabilities()
        val read = CapabilityConfig(AccessType.READ)
        val none = CapabilityConfig(AccessType.NONE)
        api.complete("checkCumulativeFuelEconomyCapabilities", read)
        api.complete("checkCurrentFuelEconomyCapabilities", none)
        val failure = SecurityException("permission")
        api.fail("checkFuelPercentCapabilities", failure)
        assertEquals(
            listOf(
                "initialized", FuelProperty.CUMULATIVE_ECONOMY to read,
                FuelProperty.CURRENT_ECONOMY to none, FuelProperty.FUEL_PERCENT to failure
            ), events
        )
        assertFalse(api.calls.any { it.startsWith("getC") })
    }

    @Test
    fun `reads preserve original units fractional zero and nullable values`() {
        client.initialize(listener)
        client.getCumulativeFuelEconomy()
        client.getCurrentFuelEconomy()
        client.getFuelPercent()
        api.complete("getCumulativeFuelEconomy", 8.25f)
        api.complete("getCurrentFuelEconomy", 0f)
        api.complete("getFuelPercent", null)
        api.complete("getCurrentFuelEconomy", null)
        assertEquals(
            listOf(
                "initialized", FuelProperty.CUMULATIVE_ECONOMY to 8.25f,
                FuelProperty.CURRENT_ECONOMY to 0f, null, FuelProperty.CURRENT_ECONOMY to null
            ), events
        )
    }

    @Test
    fun `subscriptions are distinct and same SDK instances are removed before release`() {
        client.initialize(listener)
        client.registerCumulativeFuelEconomy()
        client.registerCurrentFuelEconomy()
        client.registerFuelPercent()
        assertThrows(IllegalStateException::class.java) { client.registerCurrentFuelEconomy() }
        val cumulative =
            api.arguments.getValue("registerCumulativeFuelEconomy")[0] as FuelEconomyListener
        val current = api.arguments.getValue("registerCurrentFuelEconomy")[0] as FuelEconomyListener
        val percent = api.arguments.getValue("registerFuelPercent")[0] as FuelPercentListener
        assertNotSame(cumulative, current)
        cumulative.onFuelEconomyUpdated(7.5f)
        current.onFuelEconomyUpdated(null)
        percent.onFuelPercentUpdated(42)
        val failure = IllegalStateException("unavailable")
        current.onFailed(failure)
        assertEquals(
            listOf(
                "initialized", FuelProperty.CUMULATIVE_ECONOMY to 7.5f,
                FuelProperty.CURRENT_ECONOMY to null, 42, FuelProperty.CURRENT_ECONOMY to failure
            ), events
        )
        client.unregisterCurrentFuelEconomy()
        current.onFuelEconomyUpdated(99f)
        client.release()
        assertSame(cumulative, api.arguments.getValue("unregisterCumulativeFuelEconomy")[0])
        assertSame(current, api.arguments.getValue("unregisterCurrentFuelEconomy")[0])
        assertSame(percent, api.arguments.getValue("unregisterFuelPercent")[0])
        cumulative.onFuelEconomyUpdated(99f)
        assertEquals(5, events.size)
        assertEquals("release", api.calls.last())
    }

    @Test
    fun `cleanup continues through all subscriptions and SDK even after failures`() {
        client.initialize(listener)
        client.registerCumulativeFuelEconomy()
        client.registerCurrentFuelEconomy()
        client.registerFuelPercent()
        val first = SecurityException("cumulative")
        val second = SecurityException("current")
        api.failures["unregisterCumulativeFuelEconomy"] = first
        api.failures["unregisterCurrentFuelEconomy"] = second
        val thrown = assertThrows(SecurityException::class.java) { client.release() }
        assertSame(first, thrown)
        assertSame(second, thrown.suppressed.single())
        assertEquals(
            listOf(
                "unregisterCumulativeFuelEconomy", "unregisterCurrentFuelEconomy",
                "unregisterFuelPercent", "release"
            ), api.calls.takeLast(4)
        )
        client.release()
    }

    @Test
    fun `failed subscription suppresses updates but retains cleanup obligation`() {
        client.initialize(listener)
        api.failures["registerFuelPercent"] = SecurityException("denied")
        assertThrows(SecurityException::class.java) { client.registerFuelPercent() }
        val updates = api.arguments.getValue("registerFuelPercent")[0] as FuelPercentListener
        updates.onFuelPercentUpdated(50)
        assertEquals(listOf("initialized"), events)
        assertThrows(IllegalStateException::class.java) { client.registerFuelPercent() }
        client.release()
        assertSame(updates, api.arguments.getValue("unregisterFuelPercent")[0])
    }

    @Test
    fun `late query callbacks are ignored after release and synchronous exceptions reach caller`() {
        client.initialize(listener)
        client.getCurrentFuelEconomy()
        val failure = SecurityException("denied")
        api.failures["getFuelPercent"] = failure
        assertSame(failure, assertThrows(SecurityException::class.java) { client.getFuelPercent() })
        client.release()
        api.complete("getCurrentFuelEconomy", 12f)
        api.fail("getCurrentFuelEconomy", failure)
        assertEquals(listOf("initialized"), events)
    }

    /** Fake the SDK interface without Android/IPC. Unused SDK methods are rejected. */
    private class FakeApi : VehicleFuelApi {
        val calls = mutableListOf<String>()
        val arguments = mutableMapOf<String, Array<out Any?>>()
        val failures = mutableMapOf<String, Exception>()
        private val supportedMethods = setOf(
            "checkCumulativeFuelEconomyCapabilities",
            "checkCurrentFuelEconomyCapabilities",
            "checkFuelPercentCapabilities",
            "getCumulativeFuelEconomy",
            "getCurrentFuelEconomy",
            "getFuelPercent",
            "registerCumulativeFuelEconomy",
            "registerCurrentFuelEconomy",
            "registerFuelPercent",
            "unregisterCumulativeFuelEconomy",
            "unregisterCurrentFuelEconomy",
            "unregisterFuelPercent"
        )
        private val fuel = Proxy.newProxyInstance(
            Fuel::class.java.classLoader,
            arrayOf(Fuel::class.java)
        ) { _, method, args ->
            check(method.name in supportedMethods) { "Unexpected SDK method: ${method.name}" }
            arguments[method.name] = args ?: emptyArray()
            record(method.name)
            null
        } as Fuel

        override fun initialize() = record("initialize")
        override fun getFuel(): Fuel {
            record("getFuel"); return fuel
        }

        override fun release() = record("release")
        private fun record(method: String) {
            calls += method
            failures[method]?.let { throw it }
        }

        @Suppress("UNCHECKED_CAST")
        fun complete(method: String, value: Any?) {
            (arguments.getValue(method)[0] as (Any?) -> Unit)(value)
        }

        @Suppress("UNCHECKED_CAST")
        fun fail(method: String, cause: Exception) {
            (arguments.getValue(method)[1] as (Exception) -> Unit)(cause)
        }
    }
}
