package com.bakery.erp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
actual fun rememberLocationRequester(): LocationRequester = remember { JsLocationRequester() }

/**
 * El navegador muestra su propio diálogo de permiso ("este sitio quiere saber
 * tu ubicación") dentro de la misma llamada a `getCurrentPosition` — no hay un
 * paso de "pedir permiso" aparte como en Android. Mismo estilo `dynamic` que
 * ya usa `KeepScreenOn.js.kt` para acceder a APIs del navegador sin bindings.
 */
private class JsLocationRequester : LocationRequester {
    override suspend fun request(): LocationResult = suspendCancellableCoroutine { cont ->
        try {
            val nav: dynamic = window.navigator
            val geolocation: dynamic = nav.geolocation
            if (geolocation == null) {
                cont.resume(LocationResult.Unavailable)
                return@suspendCancellableCoroutine
            }
            val onSuccess: (dynamic) -> Unit = { pos ->
                val lat = pos.coords.latitude as Double
                val lng = pos.coords.longitude as Double
                cont.resume(LocationResult.Granted(lat, lng))
            }
            val onError: (dynamic) -> Unit = { err ->
                // code 1 = PERMISSION_DENIED; el resto (timeout, posición no
                // disponible) se trata como Unavailable.
                val code = err?.code as? Int
                cont.resume(if (code == 1) LocationResult.Denied else LocationResult.Unavailable)
            }
            geolocation.getCurrentPosition(onSuccess, onError, js("({ timeout: 15000, maximumAge: 0 })"))
        } catch (_: Throwable) {
            cont.resume(LocationResult.Unavailable)
        }
    }
}
