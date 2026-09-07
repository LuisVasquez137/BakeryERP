package com.bakery.erp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** No-op para wasmJs, igual que [KeepScreenOn]. */
@Composable
actual fun rememberLocationRequester(): LocationRequester = remember {
    object : LocationRequester {
        override suspend fun request(): LocationResult = LocationResult.Unavailable
    }
}
