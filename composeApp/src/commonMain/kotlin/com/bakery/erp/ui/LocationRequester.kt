package com.bakery.erp.ui

import androidx.compose.runtime.Composable

/** Resultado de pedir la ubicación actual del dispositivo. */
sealed interface LocationResult {
    data class Granted(val latitude: Double, val longitude: Double) : LocationResult
    /** El usuario rechazó el permiso (o lo tenía denegado permanentemente). */
    data object Denied : LocationResult
    /**
     * Permiso concedido pero no se pudo obtener una posición (GPS apagado,
     * timeout, navegador sin soporte). Se trata igual que [Denied] en la UI:
     * no hay forma útil de distinguir "inténtalo de nuevo" sin reintentar, y
     * reintentar solo arriesga colgar el formulario.
     */
    data object Unavailable : LocationResult
}

/** Pide (si hace falta) el permiso de ubicación del SO y obtiene la posición
 *  actual. Una instancia por composición — atada al ciclo de vida de quien la pide. */
interface LocationRequester {
    suspend fun request(): LocationResult
}

/**
 * Captura la ubicación del dispositivo, una sola vez por sucursal — ver
 * [com.bakery.erp.model.Branch.latitude]. Cada plataforma decide cómo pedir
 * permiso y leer la posición:
 *  - Android: `ActivityResultContracts.RequestPermission()` + `LocationManager`.
 *  - JS/web: `navigator.geolocation.getCurrentPosition` (el navegador muestra
 *    su propio diálogo de permiso dentro de esa misma llamada).
 *  - iOS: stub que siempre devuelve `Unavailable` (iOS no está activo todavía).
 *  - wasmJs: no-op por ahora, igual que [KeepScreenOn].
 *
 * Se pide como Composable (no como servicio inyectado por Koin) porque en
 * Android el registro del `ActivityResultLauncher` tiene que ocurrir en
 * composición — mismo motivo por el que [KeepScreenOn] es un
 * `@Composable expect fun` en vez de una interfaz de dominio.
 */
@Composable
expect fun rememberLocationRequester(): LocationRequester
