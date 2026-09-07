package com.bakery.erp.data

import com.bakery.erp.domain.PushTokenProvider
import com.bakery.erp.data.messaging.deleteToken
import com.bakery.erp.data.messaging.getMessaging
import com.bakery.erp.data.messaging.getToken
import com.bakery.erp.data.messaging.isSupported
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlin.js.Promise
import kotlin.js.json

/**
 * Token de push del navegador.
 *
 * Cadena de cosas que tienen que salir bien para que haya token: el navegador
 * soporta Push API → hay clave VAPID configurada → el Service Worker se registra
 * → el usuario concede el permiso. Cualquiera que falle devuelve null y la app
 * sigue igual con la campana in-app; ninguna es un error que valga la pena
 * mostrarle al usuario.
 *
 * @param vapidKey clave pública de Web Push del proyecto. Vacía = sin push web.
 */
class JsPushTokenProvider(private val vapidKey: String) : PushTokenProvider {

    override val platform: String = "web"

    override suspend fun currentToken(): String? {
        if (vapidKey.isBlank()) return null
        return runCatching {
            if (!isSupported().await()) return null

            val serviceWorker = window.navigator.asDynamic().serviceWorker
                ?: return null
            // Alcance en la raíz: el SW debe poder atender avisos de toda la app.
            val registration = (serviceWorker.register("/firebase-messaging-sw.js") as Promise<dynamic>)
                .await()

            val permission = (window.asDynamic().Notification.requestPermission() as Promise<String>)
                .await()
            if (permission != "granted") return null

            getToken(
                getMessaging(),
                json(
                    "vapidKey" to vapidKey,
                    "serviceWorkerRegistration" to registration
                )
            ).await().takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    override suspend fun deleteToken() {
        runCatching { deleteToken(getMessaging()).await() }
    }
}
