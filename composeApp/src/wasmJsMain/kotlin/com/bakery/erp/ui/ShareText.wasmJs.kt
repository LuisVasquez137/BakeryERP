package com.bakery.erp.ui

import kotlinx.browser.window

actual fun shareText(text: String, subject: String) {
    // wasmJs: no hay interop directo con navigator.share. Como fallback,
    // mostramos un prompt para copiar manualmente.
    window.alert("Comparte este código: $text")
}
