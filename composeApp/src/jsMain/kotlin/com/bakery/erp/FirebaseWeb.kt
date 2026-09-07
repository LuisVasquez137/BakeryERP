package com.bakery.erp

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.initialize

// Reemplaza estos valores con los de tu proyecto Firebase Console
// (los obtienes en: Configuración del proyecto → Tus apps → app web → Configuración del SDK)
private val firebaseOptions = FirebaseOptions(
    apiKey = "AIzaSyCC1nIxA5kWBGcrogka3APFhqbs6j2oNhM",
    authDomain = "bakery-erp-saas.firebaseapp.com",
    projectId = "bakery-erp-saas",
    storageBucket = "bakery-erp-saas.firebasestorage.app",
    gcmSenderId = "412638080390",
    applicationId = "1:412638080390:web:796d1dcec9d4bfac305007"
)

/**
 * Clave pública de Web Push (VAPID) del proyecto.
 *
 * Se saca de: Firebase Console → Configuración del proyecto → Cloud Messaging →
 * "Certificados push web" → Generar par de claves. Es la clave **pública**; no
 * es un secreto y puede vivir en el repo, igual que el `apiKey` de arriba.
 *
 * Vacía = el push web queda apagado y la app usa solo la campana in-app. No
 * rompe nada: `JsPushTokenProvider` devuelve null y no registra token.
 *
 * Si la cambias acá, `firebase-messaging-sw.js` NO necesita cambios — el service
 * worker solo lleva la config del proyecto, no la VAPID.
 */
const val WEB_PUSH_VAPID_KEY = ""

fun initializeFirebaseWeb() {
    Firebase.initialize(options = firebaseOptions)
}
