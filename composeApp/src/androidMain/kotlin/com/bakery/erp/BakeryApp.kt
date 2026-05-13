package com.bakery.erp

import android.app.Application
import com.bakery.erp.di.androidModule
import com.bakery.erp.di.appModule
import com.bakery.erp.di.initializePlatform
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BakeryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initializePlatform()
        startKoin {
            androidContext(this@BakeryApp)
            // androidModule va DESPUÉS para sobreescribir el GoogleAuthHandler stub
            modules(appModule, androidModule)
        }
    }
}
