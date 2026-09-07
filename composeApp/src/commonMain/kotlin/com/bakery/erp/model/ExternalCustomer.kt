package com.bakery.erp.model

/**
 * Cliente externo a la organización: tiendas revendedoras (B2B) o clientes
 * recurrentes que vale la pena tener en agenda (encargos repetidos).
 *
 * Los clientes walk-in NO se modelan aquí — viven sólo en el snapshot de
 * `CustomerOrder.customerName/customerPhone`.
 */
data class ExternalCustomer(
    val id: String = "",
    val type: String = ExternalCustomerType.RESELLER,
    val name: String = "",
    val businessName: String = "",
    val phone: String = "",
    val phoneAlt: String = "",
    val email: String = "",
    val address: String = "",
    val taxId: String = "",
    /** Lista de precios asignada (WHOLESALE custom para revendedor, o vacío = default del tipo). */
    val priceListId: String = "",
    val priceListName: String = "",
    /** Si el cliente tiene login propio (rol RESELLER), ID del User vinculado. */
    val linkedUserId: String = "",
    val active: Boolean = true,

    // ── Preferencias del revendedor (configuradas en el registro, se reusan diario) ─
    /** PICKUP = pasa a recoger; DELIVERY = la panadería entrega a su negocio. */
    val deliveryMode: String = ResellerDeliveryMode.PICKUP,
    /** Sucursal donde el revendedor pasa a recoger (PICKUP). */
    val preferredBranchId: String = "",
    val preferredBranchName: String = "",
    /** Hora habitual de retiro en formato "HH:mm" (24h). */
    val preferredPickupTime: String = "",
    /** Ubicación capturada UNA VEZ para entregas a domicilio (no es tracking en vivo). */
    val deliveryLatitude: Double? = null,
    val deliveryLongitude: Double? = null,
    val deliveryAddressReference: String = "",

    // ── Asignación por la planta (después de aprobar) ───────────────────────────
    /** Planta productora que atiende a este revendedor por defecto. */
    val assignedProducingBranchId: String = "",
    val assignedProducingBranchName: String = "",
    /** Turno asignado (día/noche) en el que se consolidan sus pedidos. */
    val assignedShiftId: String = "",
    val assignedShiftName: String = "",

    /** Estado del proceso de alta: PENDING_APPROVAL → ACTIVE | REJECTED. */
    val registrationStatus: String = ResellerRegistrationStatus.ACTIVE,

    val notes: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

object ResellerDeliveryMode {
    const val PICKUP = "PICKUP"
    const val DELIVERY = "DELIVERY"

    fun label(mode: String) = when (mode) {
        PICKUP -> "Retira en sucursal"
        DELIVERY -> "Entrega a domicilio"
        else -> "Desconocido"
    }
}

object ResellerRegistrationStatus {
    const val PENDING_APPROVAL = "PENDING_APPROVAL"
    const val ACTIVE = "ACTIVE"
    const val REJECTED = "REJECTED"

    fun label(status: String) = when (status) {
        PENDING_APPROVAL -> "Pendiente de aprobación"
        ACTIVE -> "Activo"
        REJECTED -> "Rechazado"
        else -> "Desconocido"
    }
}

object ExternalCustomerType {
    const val RESELLER = "RESELLER"
    const val RETAIL_RECURRING = "RETAIL_RECURRING"

    fun label(type: String) = when (type) {
        RESELLER -> "Revendedor"
        RETAIL_RECURRING -> "Cliente recurrente"
        else -> "Desconocido"
    }
}
