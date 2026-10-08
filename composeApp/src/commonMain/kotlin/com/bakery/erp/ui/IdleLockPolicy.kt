package com.bakery.erp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Cuánto aguanta un equipo de sucursal sin que nadie lo toque antes de volver a pedir PIN
 * (AppNavigation revisa cada 30 s).
 *
 * Hay pantallas que se MIRAN sin tocarlas: el panadero tiene la receta del lote enfrente con
 * las manos en la masa. Ahí no se bloquea nunca; la persona se identifica con su PIN al
 * iniciar el lote. En la caja, en cambio, 15 minutos sin tocar ya es que la cajera se fue.
 */
object IdleLockPolicy {
    const val DEFAULT_MS = 15 * 60 * 1000L

    // Composición = un solo hilo: un contador simple basta.
    private var watchedScreens = 0

    /** El plazo que manda ahora; null = no bloquear (hay una pantalla "de mirar" abierta). */
    val currentLimitMs: Long? get() = if (watchedScreens > 0) null else DEFAULT_MS

    internal fun enter() { watchedScreens++ }
    internal fun leave() { if (watchedScreens > 0) watchedScreens-- }
}

/**
 * Marca la pantalla como "de mirar sin tocar": mientras esté abierta el equipo no se bloquea
 * por inactividad. Va junto a [KeepScreenOn].
 */
@Composable
fun WatchedScreenIdleLock() {
    DisposableEffect(Unit) {
        IdleLockPolicy.enter()
        onDispose { IdleLockPolicy.leave() }
    }
}
