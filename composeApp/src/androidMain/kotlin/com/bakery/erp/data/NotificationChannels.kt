package com.bakery.erp.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Canal de notificaciones de pedidos.
 *
 * Debe existir ANTES de que llegue el primer push: en Android 8+ una
 * notificación dirigida a un canal inexistente se descarta en silencio. El id
 * tiene que coincidir con el `channelId` que manda `functions/index.js` y con el
 * `default_notification_channel_id` del manifest.
 */
const val ORDERS_NOTIFICATION_CHANNEL_ID = "bakeos_pedidos"

fun createNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val channel = NotificationChannel(
        ORDERS_NOTIFICATION_CHANNEL_ID,
        "Pedidos y producción",
        // IMPORTANCE_HIGH para que aparezca como aviso emergente: en la planta la
        // tablet está en la mesa de trabajo y un pedido que solo suma un icono en
        // la barra no lo ve nadie.
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Pedidos recibidos, lotes iniciados y productos anulados."
        enableVibration(true)
    }
    manager.createNotificationChannel(channel)
}
