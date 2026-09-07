package com.bakery.erp.ui

import android.content.Context
import android.content.SharedPreferences

/**
 * Respaldado por [SharedPreferences]. El Context lo inyecta `BakeryApp.onCreate`
 * vía [init]; si por alguna razón no se inicializó, los accesos devuelven el
 * default en vez de reventar — perder una preferencia de UI no debe tumbar nada.
 */
actual object LocalPreferences {

    private const val FILE_NAME = "bakeos_ui_prefs"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    }

    actual fun getString(key: String, default: String): String =
        runCatching { prefs?.getString(key, default) ?: default }.getOrDefault(default)

    actual fun putString(key: String, value: String) {
        runCatching { prefs?.edit()?.putString(key, value)?.apply() }
    }
}
