package eotaek.juyulow

import ai.pleos.playground.location.fused.model.LocationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SdkLogFieldsTest {
    @Test
    fun coordinatesAndNullableValuesRemainExact() {
        assertNull(sdkFields(null))
        val fields = sdkFields(LocationData(37.5001, 127.0001, 30.0, 45f, 0.8f, 123L)) as Map<*, *>
        assertEquals(37.5001, fields["latitude"])
        assertEquals(127.0001, fields["longitude"])
        assertEquals(123L, fields["timestampMillis"])
    }

    @Test
    fun exceptionsNeverExposeArbitraryMessagesOrCauses() {
        val fields = sdkFields(IllegalStateException("secret-key", Exception("secret-url")))
        assertFalse(fields.toString().contains("secret"))
        assertTrue(
            sdkFields(Exception("ERROR_SECURITY_PERMISSION")).toString()
                .contains("ERROR_SECURITY_PERMISSION")
        )
    }

    @Test
    fun unknownObjectsNeverUseTheirToString() {
        val unknown = object {
            override fun toString(): String = error("must not stringify")
        }
        assertNotNull(sdkFields(unknown))
    }
}
