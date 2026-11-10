package eotaek.juyulow

import android.app.Application
import eotaek.juyulow.vehicle.VehicleRuntime
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class JuyulowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidContext(this@JuyulowApplication)
            modules(appModule)
        }
        koin.koin.get<VehicleRuntime>().startup.start()
    }
}
