package com.bakery.erp.di

import com.bakery.erp.domain.ConnectionStatus
import com.bakery.erp.initializeFirebaseWeb

actual fun initializePlatform() {
    initializeFirebaseWeb()
    // En web la cola de Firestore vive en memoria: recargar la página sin internet perdería
    // las ventas por subir sin que nadie se entere. Hasta activar la caché persistente
    // (IndexedDB), el POS web no cobra sin conexión.
    ConnectionStatus.offlineSalesSupported = false
}

actual fun initKoin() {
    // Koin no se usa en JS — los ViewModels se crean directamente en createAppViewModels()
}
