package com.bakery.erp.di

import org.koin.core.context.startKoin

actual fun initKoin() {
    startKoin {
        modules(appModule)
    }
}
