package com.bakery.erp.domain

import com.bakery.erp.model.AppNotification
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {

    /**
     * Flow en vivo de los avisos recientes de la organización. El filtrado por
     * sucursal y permiso ocurre en cliente (ver
     * [com.bakery.erp.model.isVisibleTo]) porque un mismo aviso puede apuntar a
     * varias sucursales y Firestore no permite ese cruce con array + permiso en
     * una sola consulta.
     */
    fun observeNotifications(companyId: String, limit: Int = 50): Flow<List<AppNotification>>

    /** Escribe el aviso. Es también lo que dispara el push (ver `functions/`). */
    suspend fun push(companyId: String, notification: AppNotification)

    /**
     * Borra los avisos que apuntan a alguno de [targetIds] (`targetId` del aviso).
     *
     * Se usa cuando se borra de raíz aquello a lo que apuntaban: un aviso cuyo
     * pedido ya no existe sigue sonando en la campana y al tocarlo no lleva a
     * ningún lado.
     */
    suspend fun deleteForTargets(companyId: String, targetIds: List<String>)

    suspend fun markAsRead(companyId: String, notificationId: String, userId: String)

    suspend fun markAllAsRead(companyId: String, notificationIds: List<String>, userId: String)

    // ── Tokens de dispositivo (FCM) ───────────────────────────────────────────

    /**
     * Registra el token FCM de este dispositivo bajo el usuario. La Cloud
     * Function los lee para saber a qué dispositivos mandar el push.
     *
     * Se guarda por token (no por usuario) porque una persona puede tener la
     * tablet de la planta y su teléfono, y ambos deben sonar.
     */
    suspend fun registerDeviceToken(
        companyId: String,
        userId: String,
        token: String,
        platform: String
    )

    /** Borra el token al cerrar sesión, para que no le lleguen avisos ajenos. */
    suspend fun unregisterDeviceToken(companyId: String, token: String)
}
