package com.bakery.erp.model

data class Supplier(
    val id: String = "",
    val company: String = "",         // Nombre de la empresa
    val salesperson: String = "",     // Nombre del vendedor/contacto
    val phone: String = "",           // Número de teléfono
    val address: String? = null,      // Dirección (opcional)
    val notes: String? = null,
    /**
     * Condiciones de crédito por defecto, en días. 0 = contado (la factura nace vencida el
     * mismo día, o sea sin vencimiento que vigilar). Es un DEFAULT del proveedor, no una
     * regla: cada compra guarda su propio snapshot en `Purchase.paymentTermsDays` porque
     * un proveedor puede dar 30 días hoy y 15 el mes entrante, y las facturas viejas no
     * deben cambiar de fecha de vencimiento por eso.
     */
    val paymentTermsDays: Int = 0,
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
