package com.bakery.erp

import androidx.compose.ui.window.ComposeUIViewController
import com.bakery.erp.di.initKoin
import com.bakery.erp.di.initializePlatform
import com.bakery.erp.di.iosModule
import org.koin.core.context.loadKoinModules

private var koinStarted = false

fun MainViewController() = ComposeUIViewController {
    if (!koinStarted) {
        koinStarted = true
        initializePlatform()
        initKoin()
        // Después de appModule para que sobreescriba los NoOp compartidos con Android.
        loadKoinModules(iosModule)
    }
    App()
}
