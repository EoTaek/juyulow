package eotaek.juyulow

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import eotaek.juyulow.core.data.JourneyRepository
import eotaek.juyulow.core.design.theme.JUYULOWTheme
import eotaek.juyulow.feature.main.completion.CompletionRoute
import eotaek.juyulow.feature.main.home.HomeRoute
import eotaek.juyulow.feature.main.results.ResultsRoute
import eotaek.juyulow.feature.main.settings.SettingsRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
data object Home : NavKey

@Serializable
data class Results(val sessionId: String) : NavKey

@Serializable
data class Completion(val selectionId: String) : NavKey

@Serializable
data object Settings : NavKey

/** Deliberately no SavedStateHandle: only settings survive process recreation. */
class AppNavigationViewModel(private val journeys: JourneyRepository? = null) : ViewModel() {
    init {
        if (journeys != null) viewModelScope.launch {
            try {
                journeys.initialize()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { /* Repository state supplies the connection error to the home UI. */
            }
        }
    }

    override fun onCleared() {
        if (journeys != null) CoroutineScope(Dispatchers.Main.immediate + NonCancellable).launch { journeys.release() }
    }

    fun onForeground() {
        if (journeys?.navigationState?.value?.status == eotaek.juyulow.core.model.NaviStatus.READY) viewModelScope.launch {
            try {
                journeys.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }
    val backStack = NavBackStack<NavKey>(Home)
    private var settingsDismissGuard: (() -> Boolean)? = null
    private var routeDismissGuard: (() -> Boolean)? = null
    fun setRouteDismissGuard(guard: (() -> Boolean)?) {
        routeDismissGuard = guard
    }

    fun setSettingsDismissGuard(guard: (() -> Boolean)?) {
        settingsDismissGuard = guard
    }

    fun back() {
        if (backStack.last() == Settings && settingsDismissGuard?.invoke() == false) return
        if (backStack.last() is Results && routeDismissGuard?.invoke() == false) return
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun results(id: String) {
        if (backStack.last() == Home) backStack.add(Results(id))
    }

    fun complete(id: String) {
        if (backStack.last() is Results) backStack.add(Completion(id))
    }

    fun settings() {
        if (backStack.last() == Home) backStack.add(Settings)
    }

    fun home() {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
}

@Composable
fun JuyulowApp(navigation: AppNavigationViewModel = koinViewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { navigation.onForeground() }
    Surface(Modifier.fillMaxSize(), color = JUYULOWTheme.colors.backgroundPrimary) {
        NavDisplay(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            backStack = navigation.backStack,
            onBack = navigation::back,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            sceneStrategies = listOf(DialogSceneStrategy()),
            entryProvider = entryProvider {
                entry<Home> {
                    HomeRoute(koinViewModel(), navigation::results, navigation::settings)
                }
                entry<Results> { key ->
                    ResultsRoute(
                        koinViewModel(parameters = { parametersOf(key.sessionId) }),
                        navigation::back,
                        navigation::complete,
                        navigation::setRouteDismissGuard,
                    )
                }
                entry<Completion> { key ->
                    CompletionRoute(
                        koinViewModel(parameters = { parametersOf(key.selectionId) }),
                        navigation::back,
                        navigation::home
                    )
                }
                entry<Settings>(
                    metadata = DialogSceneStrategy.dialog(
                        DialogProperties(
                            usePlatformDefaultWidth = false
                        )
                    )
                ) {
                    SettingsRoute(
                        koinViewModel(),
                        navigation::back,
                        navigation::setSettingsDismissGuard,
                    )
                }
            },
        )
    }
}
