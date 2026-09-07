package com.bakery.erp.ui

import platform.Foundation.NSUserDefaults

/** Respaldado por `NSUserDefaults`. */
actual object LocalPreferences {
    actual fun getString(key: String, default: String): String =
        NSUserDefaults.standardUserDefaults.stringForKey(key) ?: default

    actual fun putString(key: String, value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, key)
    }
}
