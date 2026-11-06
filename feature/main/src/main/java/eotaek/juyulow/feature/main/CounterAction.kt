package eotaek.juyulow.feature.main

sealed interface CounterAction {
    data object Increment : CounterAction
    data object Decrement : CounterAction
    data object Reset : CounterAction
}
