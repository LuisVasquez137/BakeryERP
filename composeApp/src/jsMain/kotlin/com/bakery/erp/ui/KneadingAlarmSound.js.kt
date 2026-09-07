package com.bakery.erp.ui

import kotlinx.browser.document
import org.w3c.dom.HTMLAudioElement

// El mp3 se sirve desde la raíz del sitio — jsMain/resources se copia tal
// cual al output de webpack (mismo mecanismo que firebase-messaging-sw.js).
private var audioEl: HTMLAudioElement? = null

private fun audio(): HTMLAudioElement {
    val existing = audioEl
    if (existing != null) return existing
    val created = document.createElement("audio") as HTMLAudioElement
    created.src = "/kitchen_timer.mp3"
    created.loop = true
    audioEl = created
    return created
}

actual fun startKneadingAlarmSound() {
    try {
        val el = audio()
        el.currentTime = 0.0
        el.play()
    } catch (_: Throwable) { /* navegador sin soporte, o requiere gesto del usuario: ignorar */ }
}

actual fun stopKneadingAlarmSound() {
    try {
        audioEl?.pause()
    } catch (_: Throwable) { /* ignorar */ }
}
