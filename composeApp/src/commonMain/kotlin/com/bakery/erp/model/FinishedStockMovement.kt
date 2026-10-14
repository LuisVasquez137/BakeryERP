package com.bakery.erp.model

data class FinishedStockMovement(
    val id: String = "",
    val productId: String = "",
    val productName: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val type: String = FinishedMovementType.RECEPCION,
    /** Signed: + ingreso (recepción), − salida (merma, venta, transferencia). */
    val quantity: Double = 0.0,
    val unitPriceAtMovement: Double = 0.0,
    val referenceType: String = "",
    val referenceId: String = "",
    val reason: String? = null,
    val createdByUserId: String = "",
    val createdByUserName: String = "",
    val createdAt: Long = 0L
)

object FinishedMovementType {
    const val RECEPCION     = "RECEPCION"
    const val VENTA         = "VENTA"
    const val MERMA         = "MERMA"
    const val AJUSTE        = "AJUSTE"
    const val TRANSFERENCIA = "TRANSFERENCIA"
    /** Vuelve al estante lo de una venta anulada (positivo). Referencia SALE_VOID = la venta. */
    const val DEVOLUCION    = "DEVOLUCION"
    /**
     * Pastel cortado en porciones en la tienda: −1 del entero y +N de la porción, con el
     * mismo `referenceId`. Ver `portioningMovements`.
     */
    const val PORCIONADO    = "PORCIONADO"
    /** Se cayó o se arruinó en el mostrador; se le cargó a costo a una colaboradora. Ver `CounterRemoval`. */
    const val ACCIDENTE     = "ACCIDENTE"
    /** Regalado desde la caja, dentro de la cuota diaria de la sucursal. Ver `CounterRemoval`. */
    const val CORTESIA      = "CORTESIA"

    fun label(type: String) = when (type) {
        RECEPCION     -> "Recepción"
        VENTA         -> "Venta"
        MERMA         -> "Merma"
        AJUSTE        -> "Ajuste"
        TRANSFERENCIA -> "Traslado"
        DEVOLUCION    -> "Devolución (venta anulada)"
        PORCIONADO    -> "Porcionado"
        ACCIDENTE     -> "Accidente (cobrado)"
        CORTESIA      -> "Regalo"
        else          -> "Desconocido"
    }
}

object FinishedMovementReference {
    const val DISPATCH    = "DISPATCH"
    const val SALE        = "SALE"
    const val WASTAGE     = "WASTAGE"
    const val ADJUSTMENT  = "ADJUSTMENT"
    const val TRANSFER    = "TRANSFER"
    const val PURCHASE    = "PURCHASE"   // compra de producto de reventa a proveedor
    const val COUNTER_REMOVAL = "COUNTER_REMOVAL" // salida del mostrador: referenceId = el CounterRemoval
    const val PORTIONING  = "PORTIONING" // pastel cortado: referenceId une la salida y la entrada
    const val SALE_VOID   = "SALE_VOID"  // venta anulada: referenceId = la venta
}
