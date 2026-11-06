package eotaek.juyulow.feature.comparison.di

import eotaek.juyulow.feature.comparison.CounterViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val comparisonModule = module {
    viewModelOf(::CounterViewModel)
}
