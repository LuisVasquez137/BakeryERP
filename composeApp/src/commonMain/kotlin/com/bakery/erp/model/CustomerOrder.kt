package com.bakery.erp.model

/**
 * Pedido externo unificado. Cubre 3 canales discriminados por [type]:
 *  - RESELLER: tienda revendedora (B2B) — usa lista de precios mayorista.
 *  - RETAIL_SPECIAL: encargo simple de cliente final (pirujos especiales, etc.).
 *  - CAKE: encargo de pastel (datos específicos en [cakeDetails]).
 *
 * El ciclo de vida es idéntico para los 3 tipos. Se consolidan al [ProductionLot]
 * existente (planta, fecha de entrega, turno) junto con los `ProductionOrder` de
 * sucursales, vía [ProductionLot.customerOrderIds].
 */
data class CustomerOrder(
    val id: String = "",
    val type: String = CustomerOrderType.RETAIL_SPECIAL,
    val status: String = CustomerOrderStatus.PENDING,

    // ---- Cliente (snapshot inmutable; si hay customerId, se denormaliza al guardar) ----
    /** Ref a [ExternalCustomer]; vacío para walk-in. */
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val customerPhoneAlt: String = "",
    /** "¿Quién recoge o recibe el pedido?" — puede diferir del cliente. */
    val recipientName: String = "",

    // ---- Entrega ----
    val deliveryType: String = CustomerOrderDeliveryType.PICKUP,
    /** Sucursal de retiro (PICKUP) o sucursal origen del reparto (DELIVERY). */
    val deliveryBranchId: String = "",
    val deliveryBranchName: String = "",
    val deliveryAddress: String = "",
    val deliveryAddressReference: String = "",
    val deliveryDate: Long = 0L,
    /** Hora preferida — texto libre tipo "10:30 AM" o "14:00". */
    val deliveryTime: String = "",

    // ---- Routing a planta productora ----
    val producingBranchId: String = "",
    val producingBranchName: String = "",
    val shiftId: String = "",
    val shiftName: String = "",

    /** Mismo concepto que ProductionOrder.isLateOrder. */
    val isLateOrder: Boolean = false,

    // ---- Pastel (solo si type == CAKE) ----
    val cakeDetails: CakeOrderDetails? = null,

    // ---- Precios (snapshot) ----
    val priceListId: String = "",
    val priceListName: String = "",
    val subtotalAmount: Double = 0.0,
    /** Hoja de arroz opcional (pasteles). */
    val riceSheetAmount: Double = 0.0,
    /** Alquiler de fuente (pasteles). */
    val fountainRentAmount: Double = 0.0,
    val extrasAmount: Double = 0.0,
    val totalAmount: Double = 0.0,
    /** Depósito en garantía por la base/fuente (pasteles, reembolsable). */
    val depositBaseAmount: Double = 0.0,
    /** Suma de pagos ADVANCE + DEPOSIT_BASE registrados. Denormalizado. */
    val advancePaidAmount: Double = 0.0,
    /** [totalAmount] − [advancePaidAmount]. Denormalizado. */
    val balanceAmount: Double = 0.0,

    val notes: String = "",
    val createdByUserId: String = "",
    val createdByUserName: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lineCount: Int = 0,
    val lines: List<CustomerOrderLine> = emptyList(),
)

object CustomerOrderType {
    const val RESELLER = "RESELLER"
    const val RETAIL_SPECIAL = "RETAIL_SPECIAL"
    const val CAKE = "CAKE"

    fun label(type: String) = when (type) {
        RESELLER -> "Revendedor"
        RETAIL_SPECIAL -> "Encargo especial"
        CAKE -> "Pastel"
        else -> "Desconocido"
    }
}

object CustomerOrderStatus {
    /** Borrador (revendedor antes de enviar). */
    const val DRAFT = "DRAFT"
    /** Enviado, pendiente de confirmación por planta/asesor. */
    const val PENDING = "PENDING"
    /**
     * Solo algunas líneas del pedido fueron consolidadas en algún PO. El resto
     * sigue pendiente de asignar — la encargada de sucursal vendedora todavía
     * tiene que armar otro PO (probablemente a otra planta) para cubrirlas.
     */
    const val PARTIALLY_CONFIRMED = "PARTIALLY_CONFIRMED"
    /** Aceptado: ya entró al lote de producción (si requiere) o reservó stock. */
    const val CONFIRMED = "CONFIRMED"
    /** Lote en ejecución. */
    const val IN_PRODUCTION = "IN_PRODUCTION"
    /** Producido / separado en stock terminado, listo para entrega. */
    const val READY = "READY"
    /** Entregado al cliente. */
    const val DELIVERED = "DELIVERED"
    const val CANCELLED = "CANCELLED"

    fun label(status: String) = when (status) {
        DRAFT -> "Borrador"
        PENDING -> "Pendiente"
        PARTIALLY_CONFIRMED -> "Confirmado parcial"
        CONFIRMED -> "Confirmado"
        IN_PRODUCTION -> "En producción"
        READY -> "Listo"
        DELIVERED -> "Entregado"
        CANCELLED -> "Cancelado"
        else -> "Desconocido"
    }
}

object CustomerOrderDeliveryType {
    const val PICKUP = "PICKUP"
    const val DELIVERY = "DELIVERY"

    fun label(type: String) = when (type) {
        PICKUP -> "Sucursal"
        DELIVERY -> "A domicilio"
        else -> "Desconocido"
    }
}
