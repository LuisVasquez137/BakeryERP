package com.bakery.erp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/**
 * `true` mientras el equipo tiene salida real a internet. Cada plataforma decide cómo saberlo:
 *  - Android: red por defecto con `NET_CAPABILITY_VALIDATED` — un wifi conectado pero sin
 *    internet (el router sin servicio, el portal del hotel) cuenta como SIN conexión, que es
 *    justo el caso que confunde en la panadería.
 *  - JS/web: `navigator.onLine` y los eventos `online`/`offline`.
 *  - iOS y wasmJs: siempre `true` por ahora (pendiente NWPathMonitor en iOS).
 */
@Composable
expect fun rememberIsOnline(): State<Boolean>
