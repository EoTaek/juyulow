package eotaek.juyulow

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationTest {
    @Test
    fun backAndHomeNeverLeaveStaleCompletionOrRemoveRoot() {
        val navigation = AppNavigationViewModel()
        navigation.back()
        assertEquals(listOf(Home), navigation.backStack.toList())
        navigation.settings()
        navigation.back()
        navigation.results("batch")
        navigation.complete("selected")
        navigation.back()
        assertEquals(Results("batch"), navigation.backStack.last())
        navigation.complete("another")
        navigation.home()
        assertEquals(listOf(Home), navigation.backStack.toList())
        assertEquals(listOf(Home), AppNavigationViewModel().backStack.toList())
    }
}
