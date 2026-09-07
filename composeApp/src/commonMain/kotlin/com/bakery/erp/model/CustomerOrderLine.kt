package com.bakery.erp.model

/**
 * Línea de un [CustomerOrder]. Snapshot de precio (unitPrice, priceSource) igual
 * que [DispatchNoteLine] para auditoría histórica.
 */
data class CustomerOrderLine(
    val id: String = "",
    val productId: String = "",
    val productName: String = "",
    val productUnit: String = "",
    val requestedQuantity: Double = 0.0,
    val unitPrice: Double = 0.0,
    /** PRODUCT_OVERRIDE | LIST_FACTOR | NONE — mismo enum string que DispatchNoteLine. */
    val priceSource: String = "",
    /** Especificación libre del cliente: "más grande de lo normal", "sin azúcar", etc. */
    val customSpec: String = "",
    /**
     * Derivado de [Product.isInHouseProduced] al crear la línea.
     * `false` = producto de reventa pura — no se incluye en el lote de producción
     * y se descuenta directo de [FinishedProductStock] al confirmar el pedido.
     */
    val requiresProduction: Boolean = true,
    /**
     * ID del [ProductionOrder] que consolidó esta línea. Vacío = la línea todavía
     * está disponible para ser asignada por la encargada de sucursal a un PO.
     * Cuando todas las líneas de un [CustomerOrder] están asignadas, el status del
     * pedido pasa a CONFIRMED; si algunas siguen vacías, queda PARTIALLY_CONFIRMED.
     */
    val linkedProductionOrderId: String = "",
)
