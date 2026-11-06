package eotaek.juyulow

import android.app.Application
import eotaek.juyulow.feature.comparison.di.comparisonModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class JuyulowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@JuyulowApplication)
            modules(comparisonModule)
        }
    }
}
