package com.bakery.erp.model

/**
 * Aviso dirigido a las personas de una o varias sucursales.
 *
 * Los documentos de `companies/{c}/notifications/` son la **fuente de verdad**:
 * la campana de la app los lee en vivo, y la Cloud Function que manda el push
 * se dispara sobre esta misma colección. Si el push falla o el usuario no tiene
 * permisos de notificación, el aviso igual está en la bandeja — el push es un
 * canal de entrega, no el registro.
 *
 * El destinatario se expresa por **sucursal + permiso**, no por usuario: quién
 * está de turno cambia todos los días, pero "la gente de la planta Calvario que
 * maneja lotes" es estable.
 */
data class AppNotification(
    val id: String = "",
    val type: String = NotificationType.ORDER_RECEIVED,
    val title: String = "",
    val body: String = "",
    /** Sucursales cuyo personal debe ver el aviso. */
    val targetBranchIds: List<String> = emptyList(),
    /**
     * Permiso que además debe tener la persona (vacío = cualquiera de la
     * sucursal). Evita que al repartidor le lleguen avisos de producción.
     */
    val requiredPermission: String = "",
    /** A dónde lleva el tap. Ver [NotificationTarget]. */
    val targetScreen: String = NotificationTarget.NONE,
    /** Id del pedido / lote / despacho al que apunta [targetScreen]. */
    val targetId: String = "",
    /** Quién lo provocó — para no notificar a quien hizo la acción. */
    val createdByUserId: String = "",
    val createdByUserName: String = "",
    /** Usuarios que ya lo leyeron. */
    val readByUserIds: List<String> = emptyList(),
    val createdAt: Long = 0L
)

object NotificationType {
    /** Una sucursal envió un pedido a la planta. */
    const val ORDER_RECEIVED = "ORDER_RECEIVED"
    /** La planta tomó el pedido en un lote. */
    const val ORDER_IN_LOT = "ORDER_IN_LOT"
    /** El lote arrancó: el pedido ya se está horneando. */
    const val ORDER_IN_PRODUCTION = "ORDER_IN_PRODUCTION"
    /** Un producto se anuló por no llegar al mínimo de la amasadora. */
    const val PRODUCT_CANCELLED_MIN_BATCH = "PRODUCT_CANCELLED_MIN_BATCH"

    /**
     * El empaque de un turno cerró con cantidades distintas a las pedidas. Se manda
     * una sola vez por sucursal, al cerrar — no en cada ajuste: mientras la
     * encargada acomoda el turno los números cambian varias veces, y avisar a cada
     * cambio llenaría el teléfono de la sucursal de cifras que ya no valen.
     */
    const val DISPATCH_QUANTITY_CHANGED = "DISPATCH_QUANTITY_CHANGED"

    /**
     * Alguien entró al cierre de una caja fuera de su horario de cuadre. Al dueño (y a quien
     * tenga la bitácora): entrar ya muestra cuánto debería haber, así que es contar dinero sin
     * autorización aunque no llegue a cerrar.
     */
    const val CASH_CLOSE_OUT_OF_SCHEDULE = "CASH_CLOSE_OUT_OF_SCHEDULE"

    /**
     * Se anuló una venta ya cobrada. Al dueño, siempre: anular con el cliente ya ido es la forma
     * de quedarse con el dinero sin que la caja descuadre, y lo que lo frena es que se sepa.
     */
    const val SALE_VOIDED = "SALE_VOIDED"

    /**
     * Depósito al banco desde una caja: salió el efectivo, llegó la boleta (con diferencia si la
     * hay) o se dejó sin boleta. Al dueño: es la forma de saber que el dinero llegó al banco.
     */
    const val BANK_DEPOSIT = "BANK_DEPOSIT"

    /**
     * Accidente en el mostrador que el dueño tiene que ver: lo perdonó alguien, la responsable
     * no confirmó con PIN, o fue de alguien que puede perdonar (no se perdona a sí misma).
     */
    const val COUNTER_ACCIDENT = "COUNTER_ACCIDENT"

    /** Traslado de inventario: va en camino a la sucursal (avisa a quien recibe) o llegó con faltante. */
    const val STOCK_TRANSFER = "STOCK_TRANSFER"

    fun label(type: String) = when (type) {
        ORDER_RECEIVED -> "Pedido recibido"
        ORDER_IN_LOT -> "Pedido tomado"
        ORDER_IN_PRODUCTION -> "En producción"
        PRODUCT_CANCELLED_MIN_BATCH -> "Producto anulado"
        DISPATCH_QUANTITY_CHANGED -> "Cambio en tu envío"
        CASH_CLOSE_OUT_OF_SCHEDULE -> "Caja fuera de horario"
        SALE_VOIDED -> "Venta anulada"
        BANK_DEPOSIT -> "Depósito al banco"
        COUNTER_ACCIDENT -> "Accidente en mostrador"
        STOCK_TRANSFER -> "Traslado"
        else -> "Aviso"
    }
}

/**
 * Pantalla a la que navega el aviso. Se guarda como string y la resuelve la capa
 * de navegación, para no acoplar `model/` con `navigation/` (mismo criterio que
 * [com.bakery.erp.domain.report.AlertTarget]).
 */
object NotificationTarget {
    const val NONE = "NONE"
    const val PRODUCTION_ORDER_DETAIL = "PRODUCTION_ORDER_DETAIL"
    const val PRODUCTION_ORDER_LIST = "PRODUCTION_ORDER_LIST"
    const val PRODUCTION_LOT_DETAIL = "PRODUCTION_LOT_DETAIL"
    const val DISPATCH_NOTE_DETAIL = "DISPATCH_NOTE_DETAIL"
    /** Bitácora de cajas; `targetId` = sucursal. */
    const val CASH_AUDIT = "CASH_AUDIT"
    /** Salidas del mostrador (merma, accidentes, regalos); `targetId` = sucursal. */
    const val COUNTER_REMOVALS = "COUNTER_REMOVALS"
    /** Detalle de un traslado; `targetId` = el traslado. */
    const val STOCK_TRANSFER_DETAIL = "STOCK_TRANSFER_DETAIL"
}

/**
 * ¿Este aviso le toca a [user]?
 *
 * [requiredPermission] se guarda como el `name` del enum [Permission]. Si el
 * string no corresponde a ningún permiso conocido (aviso escrito por una versión
 * más nueva de la app) no se filtra por permiso: es preferible mostrar de más
 * que esconder un aviso operativo.
 */
fun AppNotification.isVisibleTo(user: User): Boolean {
    if (targetBranchIds.isNotEmpty() && targetBranchIds.none { user.canAccessBranch(it) }) return false
    if (requiredPermission.isNotEmpty()) {
        val permission = runCatching { Permission.valueOf(requiredPermission) }.getOrNull()
        if (permission != null && !user.hasPermission(permission)) return false
    }
    return true
}

fun AppNotification.isReadBy(userId: String): Boolean = userId in readByUserIds
