package com.example.wakacje1

import android.app.Application
import com.example.wakacje1.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class WakacjeApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            // Logowanie błędów Koina w logcat (pomaga w debugowaniu)
            androidLogger()
            // Przekazanie kontekstu aplikacji (potrzebne np. dla Room/SharedPrefs)
            androidContext(this@WakacjeApplication)
            // Załadowanie Twojego modułu z zależnościami
            modules(appModule)
        }
    }
}