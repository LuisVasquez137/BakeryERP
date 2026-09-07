package com.bakery.erp.ui

import kotlinx.browser.window

/**
 * Respaldado por `localStorage`. Se envuelve en runCatching porque en modo
 * incógnito o con cookies bloqueadas el navegador lanza al escribir.
 */
actual object LocalPreferences {
    actual fun getString(key: String, default: String): String =
        runCatching { window.localStorage.getItem(key) }.getOrNull() ?: default

    actual fun putString(key: String, value: String) {
        runCatching { window.localStorage.setItem(key, value) }
    }
}
