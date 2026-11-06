package eotaek.juyulow.feature.main.di

import eotaek.juyulow.feature.main.CounterViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val mainModule = module {
    viewModelOf(::CounterViewModel)
}
