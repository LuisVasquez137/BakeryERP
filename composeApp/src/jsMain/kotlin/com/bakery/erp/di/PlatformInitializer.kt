package com.bakery.erp.di

import com.bakery.erp.initializeFirebaseWeb

actual fun initializePlatform() {
    initializeFirebaseWeb()
}

actual fun initKoin() {
    // Koin no se usa en JS — los ViewModels se crean directamente en createAppViewModels()
}
