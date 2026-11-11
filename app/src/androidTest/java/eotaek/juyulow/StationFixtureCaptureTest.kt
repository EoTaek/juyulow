package eotaek.juyulow

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit developer probe: enter a POI query in the foreground navigation search field. */
class StationFixtureCaptureTest {
    @Test
    fun enterSearchQuery() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("stationSearchProbe") == "true")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags =
                flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        automation.waitForIdle(500, 5_000)
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.className == "android.widget.EditText") return node
            for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
            return null
        }

        val field = automation.windows.firstNotNullOfOrNull { find(it.root) }
            ?: find(automation.rootInActiveWindow)
        checkNotNull(field) { "Open navigation search first" }
        field.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val query = args.getString("queryBase64")?.let {
            String(android.util.Base64.decode(it, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } ?: requireNotNull(args.getString("query"))
        assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                query
            )
        }))
        repeat(20) {
            if (field.refresh() && field.text.toString() == query) return@repeat
            android.os.SystemClock.sleep(100)
        }
        assertTrue("Query was not applied: ${field.text}", field.text.toString() == query)
        assertTrue(
            "IME search unavailable",
            field.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        )
    }
}
