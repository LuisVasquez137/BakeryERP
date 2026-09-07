package com.bakery.erp.ui

import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.await
import kotlin.js.Promise
import kotlin.js.json

@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
actual fun shareText(text: String, subject: String) {
    val nav = window.navigator.asDynamic()
    val shareData = json(
        "title" to subject,
        "text" to text
    )
    if (nav.share != null) {
        GlobalScope.launch {
            runCatching {
                (nav.share(shareData) as Promise<dynamic>).await()
            }
        }
    } else {
        // Fallback: copiar al portapapeles.
        val clip = nav.clipboard
        if (clip != null) {
            clip.writeText(text)
            window.alert("Código copiado al portapapeles: $text")
        } else {
            window.prompt("Copia el código:", text)
        }
    }
}
