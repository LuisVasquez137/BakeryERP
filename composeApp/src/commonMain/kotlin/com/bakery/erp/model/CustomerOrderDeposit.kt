package com.bakery.erp.model

/**
 * Un pedido de cliente recibe anticipo mientras siga vivo y le falte por pagar: ni entregado,
 * ni cancelado, ni borrador de revendedor (ése no se cobra en caja).
 */
fun CustomerOrder.acceptsDeposit(): Boolean =
    status !in setOf(CustomerOrderStatus.DELIVERED, CustomerOrderStatus.CANCELLED, CustomerOrderStatus.DRAFT) &&
        type != CustomerOrderType.RESELLER &&
        balanceAmount > 0.004

/** Cómo se ve el pedido en el anticipo y en el comprobante: tipo, entrega y cliente. */
fun CustomerOrder.depositLabel(deliveryDateLabel: String): String =
    listOf(CustomerOrderType.label(type), deliveryDateLabel, customerName)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

/** Qué encargó, en una línea, para prellenar el anticipo. */
fun CustomerOrder.depositDescription(lines: List<CustomerOrderLine> = this.lines): String =
    lines.joinToString(", ") { l -> "${l.requestedQuantity.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }} ${l.productName}" }
        .ifBlank { CustomerOrderType.label(type) }

/**
 * Los anticipos que se escribieron a mano en el pedido antes de que pasaran por caja no tienen
 * un pago detrás. El primer pago real recalcula lo abonado sumando pagos, y sin esto los
 * borraría: se convierten en un pago "anotado a mano" con lo que decía el pedido.
 */
fun legacyAdvanceToMigrate(order: CustomerOrder, existingPayments: List<CustomerOrderPayment>): Double =
    if (existingPayments.isEmpty() && order.advancePaidAmount > 0.004) order.advancePaidAmount else 0.0
