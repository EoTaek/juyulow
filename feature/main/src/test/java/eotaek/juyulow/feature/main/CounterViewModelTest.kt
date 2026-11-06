package eotaek.juyulow.feature.main

import eotaek.juyulow.feature.main.di.mainModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.koin.dsl.koinApplication

class CounterViewModelTest {
    @Test
    fun counterStartsAtZero() {
        assertEquals(0, CounterViewModel().uiState.value.count)
    }

    @Test
    fun actionsUpdateTheCurrentCount() {
        val viewModel = CounterViewModel()

        repeat(3) { viewModel.onAction(CounterAction.Increment) }
        viewModel.onAction(CounterAction.Decrement)

        assertEquals(2, viewModel.uiState.value.count)
        viewModel.onAction(CounterAction.Reset)
        assertEquals(0, viewModel.uiState.value.count)
        viewModel.onAction(CounterAction.Increment)
        assertEquals(1, viewModel.uiState.value.count)
    }

    @Test
    fun decrementCannotMakeTheCountNegative() {
        val viewModel = CounterViewModel()

        repeat(2) { viewModel.onAction(CounterAction.Decrement) }

        assertEquals(0, viewModel.uiState.value.count)
    }

    @Test
    fun koinResolvesIndependentViewModels() {
        val application = koinApplication { modules(mainModule) }
        try {
            val first = application.koin.get<CounterViewModel>()
            val second = application.koin.get<CounterViewModel>()
            first.onAction(CounterAction.Increment)

            assertNotSame(first, second)
            assertEquals(1, first.uiState.value.count)
            assertEquals(0, second.uiState.value.count)
        } finally {
            application.close()
        }
    }
}
