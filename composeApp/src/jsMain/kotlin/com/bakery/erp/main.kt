@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bakery.erp

import androidx.compose.ui.window.ComposeViewport
import com.bakery.erp.di.initKoin
import com.bakery.erp.di.initializePlatform
import kotlinx.browser.document

fun main() {
    initializePlatform()
    initKoin()
    val body = document.body ?: return
    ComposeViewport(body) {
        App()
    }
}
