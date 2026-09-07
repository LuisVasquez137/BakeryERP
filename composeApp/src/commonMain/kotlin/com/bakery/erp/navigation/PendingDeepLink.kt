package com.bakery.erp.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Destino pendiente que dejó el tap sobre una notificación del sistema.
 *
 * Cuando la app está cerrada, el push lo pinta Android y al tocarlo se lanza la
 * activity con los datos en los extras del intent — no hay NavController vivo en
 * ese momento. Se deposita acá y [AppNavigation] lo consume en cuanto el grafo
 * está montado y hay sesión iniciada.
 *
 * Es un singleton de proceso porque el productor (la activity) y el consumidor
 * (el grafo de navegación) no comparten scope ni ciclo de vida.
 */
object PendingDeepLink {

    private val _target = MutableStateFlow<DeepLinkTarget?>(null)
    val target: StateFlow<DeepLinkTarget?> = _target.asStateFlow()

    fun set(targetScreen: String, targetId: String) {
        if (targetScreen.isBlank()) return
        _target.value = DeepLinkTarget(targetScreen, targetId)
    }

    /** Se llama después de navegar, para que no se repita al rotar o volver. */
    fun consume() { _target.value = null }
}

data class DeepLinkTarget(val targetScreen: String, val targetId: String)
