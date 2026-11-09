package eotaek.juyulow.location

import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PleosFusedLocationClientTest {
    private val api = FakeApi()
    private val client = PleosFusedLocationClient(api)
    private val events = mutableListOf<Any?>()
    private val listener = object : FusedLocationClientListener {
        override fun onInitialized() {
            events += "initialized"
        }

        override fun onInitializationFailed(cause: Exception) {
            events += cause
        }

        override fun onLastKnownLocation(location: LocationData?) {
            events += location
        }

        override fun onChanged(location: LocationData?) {
            events += location
        }

        override fun onError(cause: Exception) {
            events += cause
        }
    }

    @Test
    fun `support check does not initialize and unsupported environment rejects requests`() {
        api.supported = false
        assertFalse(client.isSupported())
        client.initialize(listener)
        assertTrue(events.single() is UnsupportedOperationException)
        assertFalse(api.calls.contains("initialize"))
        assertThrows(IllegalStateException::class.java) { client.getLastKnownLocation() }
        client.release()
        assertFalse(api.calls.contains("release"))
    }

    @Test
    fun `initialization is explicit and terminal after failure or release`() {
        assertThrows(IllegalStateException::class.java) { client.getLastKnownLocation() }
        val failure = IllegalStateException("initialization")
        api.initializeFailure = failure
        client.initialize(listener)
        assertSame(failure, events.single())
        assertThrows(IllegalStateException::class.java) { client.initialize(listener) }
        client.release()
        client.release()
        assertEquals(listOf("support", "initialize", "release"), api.calls)
        assertThrows(IllegalStateException::class.java) { client.isSupported() }
    }

    @Test
    fun `last known and update callbacks preserve location null and error`() {
        client.initialize(listener)
        client.getLastKnownLocation()
        val location = LocationData(37.4, 127.1, 100.0, 45f, 0.9f, 123L)
        api.last.onChanged(location)
        api.last.onChanged(null)
        client.registerFusedLocationCallback()
        api.updates.onChanged(location)
        val failure = SecurityException("privacy")
        api.updates.onError(failure)
        assertEquals(listOf("initialized", location, null, location, failure), events)
        client.showLocationAccessBlockedPopup()
        assertEquals("popup", api.calls.last())
    }

    @Test
    fun `subscription is unique and uses same instance for cleanup`() {
        client.initialize(listener)
        client.registerFusedLocationCallback()
        assertThrows(IllegalStateException::class.java) { client.registerFusedLocationCallback() }
        client.unregisterFusedLocationCallback()
        assertSame(api.updates, api.removed)
        api.updates.onChanged(null)
        assertEquals(listOf("initialized"), events)
        client.registerFusedLocationCallback()
        client.release()
        client.release()
        api.updates.onError(IllegalStateException("late"))
        assertEquals(listOf("initialized"), events)
        assertEquals(listOf("unregister", "release"), api.calls.takeLast(2))
    }

    @Test
    fun `release attempts SDK cleanup after permission revocation and suppresses late query`() {
        client.initialize(listener)
        client.getLastKnownLocation()
        client.registerFusedLocationCallback()
        val failure = SecurityException("revoked")
        api.unregisterFailure = failure
        assertSame(failure, assertThrows(SecurityException::class.java) { client.release() })
        assertEquals("release", api.calls.last())
        api.last.onChanged(null)
        api.updates.onChanged(null)
        assertEquals(listOf("initialized"), events)
        assertThrows(IllegalStateException::class.java) { client.registerFusedLocationCallback() }
    }

    @Test
    fun `partially failed registration is not retried and is cleaned on release`() {
        client.initialize(listener)
        val failure = SecurityException("denied")
        api.registerFailure = failure
        assertSame(
            failure,
            assertThrows(SecurityException::class.java) { client.registerFusedLocationCallback() })
        api.updates.onChanged(null)
        assertEquals(listOf("initialized"), events)
        assertThrows(IllegalStateException::class.java) { client.registerFusedLocationCallback() }
        client.release()
        assertSame(api.updates, api.removed)
    }

    @Test
    fun `unregister failure retains cleanup obligation but stops updates`() {
        client.initialize(listener)
        client.registerFusedLocationCallback()
        api.unregisterFailure = SecurityException("revoked")
        assertThrows(SecurityException::class.java) { client.unregisterFusedLocationCallback() }
        api.updates.onChanged(null)
        assertEquals(listOf("initialized"), events)
        api.unregisterFailure = null
        client.release()
        assertEquals(2, api.calls.count { it == "unregister" })
    }

    private class FakeApi : FusedLocationApi {
        val calls = mutableListOf<String>()
        var supported = true
        var initializeFailure: Exception? = null
        var registerFailure: Exception? = null
        var unregisterFailure: Exception? = null
        lateinit var last: FusedLocationListener
        lateinit var updates: FusedLocationListener
        var removed: FusedLocationListener? = null
        override fun isSupported(): Boolean {
            calls += "support"; return supported
        }

        override fun initialize() {
            calls += "initialize"; initializeFailure?.let { throw it }
        }

        override fun getLastKnownLocation(callback: FusedLocationListener) {
            calls += "last"; last = callback
        }

        override fun registerFusedLocationCallback(listener: FusedLocationListener) {
            calls += "register"
            updates = listener
            registerFailure?.let { throw it }
        }

        override fun unregisterFusedLocationCallback(listener: FusedLocationListener) {
            calls += "unregister"
            removed = listener
            unregisterFailure?.let { throw it }
        }

        override fun showLocationAccessBlockedPopup() {
            calls += "popup"
        }

        override fun release() {
            calls += "release"
        }
    }
}
