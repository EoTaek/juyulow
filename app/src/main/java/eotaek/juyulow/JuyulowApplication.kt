package eotaek.juyulow

import android.app.Application
import eotaek.juyulow.feature.main.di.mainModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class JuyulowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@JuyulowApplication)
            modules(mainModule)
        }
    }
}
