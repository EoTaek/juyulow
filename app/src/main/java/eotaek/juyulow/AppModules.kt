package eotaek.juyulow

import eotaek.juyulow.core.data.ComparisonSessionRepository
import eotaek.juyulow.core.data.InMemoryComparisonSessionRepository
import eotaek.juyulow.core.data.JourneyRepository
import eotaek.juyulow.core.data.MockJourneyRepository
import eotaek.juyulow.core.data.MockStationRepository
import eotaek.juyulow.core.data.PreferencesSettingsRepository
import eotaek.juyulow.core.data.SettingsRepository
import eotaek.juyulow.core.data.SettingsStorage
import eotaek.juyulow.core.data.StationRepository
import eotaek.juyulow.feature.main.CompletionViewModel
import eotaek.juyulow.feature.main.HomeViewModel
import eotaek.juyulow.feature.main.ResultsViewModel
import eotaek.juyulow.feature.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single { SettingsStorage(androidContext().filesDir.resolve("datastore/refuel.preferences_pb")) }
    singleOf<SettingsRepository, SettingsStorage>(::PreferencesSettingsRepository)
    singleOf<StationRepository>(::MockStationRepository)
    singleOf<ComparisonSessionRepository>(::InMemoryComparisonSessionRepository)
    singleOf<JourneyRepository>(::MockJourneyRepository)
    viewModelOf(::AppNavigationViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::ResultsViewModel)
    viewModelOf(::CompletionViewModel)
    viewModelOf(::SettingsViewModel)
}
