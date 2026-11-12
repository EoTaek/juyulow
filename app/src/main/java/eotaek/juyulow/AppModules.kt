package eotaek.juyulow

import eotaek.juyulow.core.data.ComparisonSessionRepository
import eotaek.juyulow.core.data.InMemoryComparisonSessionRepository
import eotaek.juyulow.core.data.InMemoryVehicleInfoRepository
import eotaek.juyulow.core.data.JourneyRepository
import eotaek.juyulow.core.data.LiveJourneyRepository
import eotaek.juyulow.core.data.NavigationRepository
import eotaek.juyulow.core.data.PreferencesSettingsRepository
import eotaek.juyulow.core.data.PreferencesVehicleDefaultsRepository
import eotaek.juyulow.core.data.SettingsRepository
import eotaek.juyulow.core.data.SettingsStorage
import eotaek.juyulow.core.data.StationRepository
import eotaek.juyulow.core.data.VehicleDefaultsRepository
import eotaek.juyulow.core.data.VehicleInfoRepository
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
import eotaek.juyulow.vehicle.VehicleRuntime
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single { eotaek.juyulow.core.data.OpinetClient.create(BuildConfig.OPINET_API_KEY) }
    single { eotaek.juyulow.core.data.OpinetCandidates(get()) }
    single { eotaek.juyulow.navigation.NaviTripLedger() }
    factory<NavigationRepository> { eotaek.juyulow.navigation.PleosNavigationRepository(get(), get()) }
    factory<NaviClient> { PleosNaviClient(androidContext()) }
    factory<FusedLocationClient> { PleosFusedLocationClient(androidContext()) }
    factory<VehicleFuelClient> { PleosVehicleFuelClient(androidContext()) }
    single { SettingsStorage(androidContext().filesDir.resolve("datastore/refuel.preferences_pb")) }
    singleOf<SettingsRepository, SettingsStorage>(::PreferencesSettingsRepository)
    single<VehicleDefaultsRepository> { PreferencesVehicleDefaultsRepository(get(), get()) }
    single { InMemoryVehicleInfoRepository() }
    single<VehicleInfoRepository> { get<InMemoryVehicleInfoRepository>() }
    single { VehicleRuntime(androidContext(), get(), get()) }
    single {
        LiveJourneyRepository(
            { get<NavigationRepository>() },
            candidateSource = { origin, conditions ->
                get<eotaek.juyulow.core.data.OpinetCandidates>().search(
                    origin,
                    conditions
                )
            },
            canInteract = { eotaek.juyulow.navigation.canInteractWithNavigation(androidContext()) })
    }
    single<StationRepository> { get<LiveJourneyRepository>() }
    singleOf<ComparisonSessionRepository>(::InMemoryComparisonSessionRepository)
    single<JourneyRepository> { get<LiveJourneyRepository>() }
    viewModelOf(::AppNavigationViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::ResultsViewModel)
    viewModelOf(::CompletionViewModel)
    viewModelOf(::SettingsViewModel)
}
