package com.bakery.erp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.browser.document
import kotlinx.browser.window

/**
 * En web usamos la Screen Wake Lock API. Si el navegador no la soporta o la pestaña
 * pierde visibilidad, el wake lock se libera automáticamente — debemos re-solicitarlo
 * cuando vuelva al foreground.
 */
@Composable
actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        // Usamos `dynamic` para evitar bindings al API Wake Lock (variable entre browsers).
        // El sentinel almacena el WakeLockSentinel devuelto por navigator.wakeLock.request().
        val state = js("({ sentinel: null })")

        val request: () -> Unit = {
            try {
                val nav: dynamic = window.navigator
                val wakeLock: dynamic = nav.wakeLock
                if (wakeLock != null && state.sentinel == null) {
                    js(
                        "wakeLock.request('screen').then(function(s){ state.sentinel = s; }).catch(function(){})"
                    )
                }
            } catch (_: Throwable) { /* navegador sin soporte: ignorar */ }
        }

        val release: () -> Unit = {
            try {
                val s: dynamic = state.sentinel
                if (s != null) {
                    s.release()
                    state.sentinel = null
                }
            } catch (_: Throwable) { /* ignorar */ }
        }

        val visibilityHandler: (org.w3c.dom.events.Event) -> Unit = {
            if (document.asDynamic().visibilityState == "visible") request()
        }

        request()
        document.addEventListener("visibilitychange", visibilityHandler)

        onDispose {
            document.removeEventListener("visibilitychange", visibilityHandler)
            release()
        }
    }
}
