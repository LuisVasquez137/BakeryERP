package com.bakery.erp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.browser.window
import org.w3c.dom.events.Event

@Composable
actual fun rememberIsOnline(): State<Boolean> {
    val online = remember { mutableStateOf(window.navigator.onLine) }
    DisposableEffect(Unit) {
        val onOnline: (Event) -> Unit = { online.value = true }
        val onOffline: (Event) -> Unit = { online.value = false }
        window.addEventListener("online", onOnline)
        window.addEventListener("offline", onOffline)
        onDispose {
            window.removeEventListener("online", onOnline)
            window.removeEventListener("offline", onOffline)
        }
    }
    return online
}
