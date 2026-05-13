package com.bakery.erp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.bakery.erp.di.initKoin
import com.bakery.erp.di.initializePlatform

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initializePlatform()
    initKoin()
    ComposeViewport {
        App()
    }
}
