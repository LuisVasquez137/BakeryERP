package com.bakery.erp.ui

/**
 * En memoria: wasmJs no es un target en uso todavía y no vale la pena cablear
 * `localStorage` acá. La preferencia dura lo que dure la sesión.
 */
actual object LocalPreferences {
    private val values = mutableMapOf<String, String>()

    actual fun getString(key: String, default: String): String = values[key] ?: default

    actual fun putString(key: String, value: String) {
        values[key] = value
    }
}
