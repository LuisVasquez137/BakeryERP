package com.bakery.erp

import android.app.Application
import com.bakery.erp.data.CurrentActivityHolder
import com.bakery.erp.data.createNotificationChannels
import com.bakery.erp.di.androidModule
import com.bakery.erp.di.appModule
import com.bakery.erp.di.initializePlatform
import com.bakery.erp.ui.LocalPreferences
import com.bakery.erp.ui.initKneadingAlarmSound
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BakeryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LocalPreferences.init(this)
        initKneadingAlarmSound(this)
        initializePlatform()
        // Antes que nada: si el canal no existe cuando llega un push, Android lo
        // descarta sin avisar.
        createNotificationChannels(this)
        CurrentActivityHolder.registerWith(this)
        startKoin {
            androidContext(this@BakeryApp)
            // androidModule va DESPUÉS para sobreescribir el GoogleAuthHandler stub
            modules(appModule, androidModule)
        }
    }
}
