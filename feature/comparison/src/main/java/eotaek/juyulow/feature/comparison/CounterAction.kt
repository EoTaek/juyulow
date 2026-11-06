package eotaek.juyulow.feature.comparison

sealed interface CounterAction {
    data object Increment : CounterAction
    data object Decrement : CounterAction
    data object Reset : CounterAction
}
