package com.bakery.erp.data

import com.bakery.erp.domain.PushTokenProvider
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Token FCM del dispositivo Android.
 *
 * Nunca lanza: si el dispositivo no tiene Google Play Services o el usuario negó
 * las notificaciones, devuelve null y la app sigue funcionando con la campana
 * in-app. Perder el push no debe romper ningún flujo de trabajo.
 */
class AndroidPushTokenProvider : PushTokenProvider {

    override val platform: String = "android"

    override suspend fun currentToken(): String? = suspendCancellableCoroutine { cont ->
        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> if (cont.isActive) cont.resume(token) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    override suspend fun deleteToken() {
        runCatching { FirebaseMessaging.getInstance().deleteToken() }
    }
}
