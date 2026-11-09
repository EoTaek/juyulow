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
import eotaek.juyulow.feature.main.completion.CompletionViewModel
import eotaek.juyulow.feature.main.home.HomeViewModel
import eotaek.juyulow.feature.main.results.ResultsViewModel
import eotaek.juyulow.feature.settings.SettingsViewModel
import eotaek.juyulow.location.FusedLocationClient
import eotaek.juyulow.location.PleosFusedLocationClient
import eotaek.juyulow.navigation.NaviClient
import eotaek.juyulow.navigation.PleosNaviClient
import eotaek.juyulow.vehicle.PleosVehicleFuelClient
import eotaek.juyulow.vehicle.VehicleFuelClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    factory<NaviClient> { PleosNaviClient(androidContext()) }
    factory<FusedLocationClient> { PleosFusedLocationClient(androidContext()) }
    factory<VehicleFuelClient> { PleosVehicleFuelClient(androidContext()) }
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
