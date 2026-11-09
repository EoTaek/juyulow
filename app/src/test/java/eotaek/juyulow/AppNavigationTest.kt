package eotaek.juyulow

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationTest {
    @Test
    fun settingsDismissalReadsLatestSaveStateAndAllowsRetryOrSuccessToClose() {
        val navigation = AppNavigationViewModel()
        var saving = false
        navigation.settings()
        navigation.setSettingsDismissGuard { !saving }
        saving = true
        // Both system Back and dialog outside dismissal use this same callback.
        repeat(2) { navigation.back() }
        assertEquals(listOf(Home, Settings), navigation.backStack.toList())
        saving = false
        navigation.back()
        assertEquals(listOf(Home), navigation.backStack.toList())
        navigation.setSettingsDismissGuard(null)
        navigation.settings()
        navigation.back()
        assertEquals(listOf(Home), navigation.backStack.toList())
    }

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
