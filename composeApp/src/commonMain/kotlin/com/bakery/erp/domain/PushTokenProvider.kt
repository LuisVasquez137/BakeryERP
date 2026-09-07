package com.bakery.erp.domain

/**
 * Obtiene el token de push del dispositivo.
 *
 * Se abstrae por plataforma porque cada una lo consigue distinto: Android por
 * FCM, web por Service Worker + VAPID, iOS por APNs. La implementación por
 * defecto no devuelve nada — así la app corre igual en plataformas donde el push
 * todavía no está configurado, y el usuario solo pierde el aviso en la barra de
 * notificaciones, no el aviso en sí (que vive en Firestore).
 */
interface PushTokenProvider {
    /** Identificador de plataforma que se guarda junto al token: "android" | "web" | "ios". */
    val platform: String

    /**
     * Token de este dispositivo, o null si el push no está disponible (permiso
     * denegado, sin Google Play Services, plataforma sin configurar).
     */
    suspend fun currentToken(): String?

    /** Borra el token local al cerrar sesión. */
    suspend fun deleteToken()
}

/** Implementación neutra para plataformas sin push configurado. */
class NoOpPushTokenProvider(override val platform: String = "none") : PushTokenProvider {
    override suspend fun currentToken(): String? = null
    override suspend fun deleteToken() = Unit
}
