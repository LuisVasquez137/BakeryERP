package com.bakery.erp.ui

import androidx.compose.runtime.Composable

/** No-op para wasmJs (bindings de Wake Lock API requieren extra setup). */
@Composable
actual fun KeepScreenOn() {
    // intentionally empty
}
