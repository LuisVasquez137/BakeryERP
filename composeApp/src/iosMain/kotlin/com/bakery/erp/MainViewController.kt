package com.bakery.erp

import androidx.compose.ui.window.ComposeUIViewController
import com.bakery.erp.di.initKoin
import com.bakery.erp.di.initializePlatform

private var koinStarted = false

fun MainViewController() = ComposeUIViewController {
    if (!koinStarted) {
        koinStarted = true
        initializePlatform()
        initKoin()
    }
    App()
}
