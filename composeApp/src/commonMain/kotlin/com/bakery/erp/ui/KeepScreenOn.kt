package com.bakery.erp.ui

import androidx.compose.runtime.Composable

/**
 * Mantiene la pantalla encendida mientras la composable esté en pantalla.
 * Cada plataforma decide cómo hacerlo:
 *  - Android: `FLAG_KEEP_SCREEN_ON` en el Window.
 *  - JS/web: Screen Wake Lock API (mientras la pestaña esté visible).
 *  - iOS: `UIApplication.idleTimerDisabled = true`.
 *  - wasmJs: no-op por ahora.
 *
 * Pensado para la pantalla "Lista de Producción" donde el panadero la mantiene
 * encendida todo el turno.
 */
@Composable
expect fun KeepScreenOn()
