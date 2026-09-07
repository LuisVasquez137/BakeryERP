package com.bakery.erp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** iOS no está activo todavía en este proyecto — stub hasta implementar
 *  CLLocationManager. Ver [rememberLocationRequester]. */
@Composable
actual fun rememberLocationRequester(): LocationRequester = remember {
    object : LocationRequester {
        override suspend fun request(): LocationResult = LocationResult.Unavailable
    }
}
