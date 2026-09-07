@file:JsModule("firebase/messaging")
@file:JsNonModule

package com.bakery.erp.data.messaging

import kotlin.js.Promise

/**
 * Declaraciones del SDK modular `firebase/messaging`.
 *
 * GitLive no envuelve Messaging, así que se llama al SDK de JS directo. El
 * módulo ya viene como dependencia npm del propio GitLive, no hay que agregarlo
 * al build.
 */
external fun getMessaging(app: dynamic = definedExternally): dynamic

/**
 * Token de este navegador. [options] lleva `vapidKey` y
 * `serviceWorkerRegistration`. Rechaza si el usuario negó las notificaciones.
 */
external fun getToken(messaging: dynamic, options: dynamic): Promise<String>

external fun deleteToken(messaging: dynamic): Promise<Boolean>

/** false en navegadores sin Push API (Safari viejo, modo incógnito de algunos). */
external fun isSupported(): Promise<Boolean>
