package com.bakery.erp.model

/**
 * Datos del pago de UN cobro en caja, que puede abarcar varias ventas (el pan del mostrador y
 * la cuenta del restaurante que trajo el cliente). Se copian iguales a cada venta del cobro.
 */
data class CheckoutPayment(
    val checkoutId: String,
    val method: String,
    val checkoutTotal: Double,
    val tenderedAmount: Double,
    val changeAmount: Double,
    val billValues: List<Double> = emptyList(),
    val billCounts: List<Double> = emptyList(),
    /**
     * A quién facturar las cuentas del restaurante que entran en este cobro. Vacío = no se
     * factura. Viaja acá para que la cuenta quede SOLICITADA en la misma escritura que la
     * recibe en caja, igual que la venta de pan.
     */
    val invoiceReceptorId: String = "",
    val invoiceReceptorName: String = "",
    val invoiceWithoutDetail: Boolean = false,
    /**
     * Parte de ESTA cuenta que paga un anticipo del cliente. Se arma por cuenta (ver
     * `planDepositApplication`): el anticipo se reparte entre las ventas del cobro.
     */
    val depositId: String = "",
    val depositFolio: String = "",
    val depositAmount: Double = 0.0
)

/**
 * El último cobro de la caja, tal como se ve en "Último cobro": cuánto se cobró, con qué
 * billetes pagó el cliente y cuánto vuelto se dio. Existe para los reclamos del mostrador
 * ("no me dio bien el vuelto") y para cuando la cajera no se acuerda de qué cobró.
 */
data class CheckoutRecord(
    val checkoutId: String,
    val at: Long,
    val method: String,
    val total: Double,
    val tendered: Double,
    val change: Double,
    /** Billetes (valor, cantidad), de mayor a menor. Vacío si se tecleó el monto. */
    val bills: List<Pair<Double, Int>> = emptyList(),
    /** "3 × Pan de Q0.50", "Restaurante #47 · Mesa 4"… vacío si no se conoce el detalle. */
    val items: List<String> = emptyList(),
    val cashierName: String = ""
)

/**
 * Arma los cobros a partir de las ventas de una sesión: agrupa por [Sale.checkoutId] (las
 * viejas, sin él, son cada una su propio cobro) y ordena del más reciente al más viejo.
 * Las cuentas del restaurante que se recibieron del mesero sin pasar por el carrito no son
 * un cobro de esta caja —no hubo billetes ni vuelto acá— y se dejan fuera.
 */
fun checkoutsFromSales(sales: List<Sale>): List<CheckoutRecord> =
    sales.filter { it.status == SaleStatus.COMPLETED }
        .filter { it.source == SaleSource.POS || it.checkoutId.isNotEmpty() }
        .groupBy { it.checkoutId.ifEmpty { "sale_" + it.id } }
        .map { (key, group) ->
            val first = group.maxBy { it.createdAt }
            CheckoutRecord(
                checkoutId = key,
                at = group.maxOf { maxOf(it.createdAt, it.collectedAt) },
                method = first.paymentMethod,
                total = first.effectiveCheckoutTotal,
                tendered = first.tenderedAmount,
                change = first.changeAmount,
                bills = first.tenderedBills,
                cashierName = first.cashierName
            )
        }
        .sortedByDescending { it.at }
