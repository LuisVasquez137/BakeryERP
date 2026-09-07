package com.bakery.erp.ui

/**
 * Sonido del timer de amasado — el archivo `kitchen_timer.mp3` de la raíz del
 * proyecto (empaquetado como `res/raw` en Android y servido como asset
 * estático en web). Un tono sintético (`ToneGenerator`) no se oía sobre el
 * ruido de las máquinas de la planta; un audio de alarma real sí.
 *
 * `startKneadingAlarmSound()` lo pone en loop hasta el próximo
 * `stopKneadingAlarmSound()` — quien llama decide cuánto dura (ver
 * `KneadingTimerState` en `ProductionLotDetailScreen`, que lo limita a 10s o
 * hasta que el panadero lo detenga a mano).
 *
 *  - Android: `MediaPlayer` sobre el raw resource.
 *  - JS/web: `<audio>` con el mp3 servido desde la raíz del sitio.
 *  - iOS / wasmJs: no-op por ahora (sin caso de uso todavía en esas plataformas).
 */
expect fun startKneadingAlarmSound()
expect fun stopKneadingAlarmSound()
