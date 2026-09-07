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

    fun label(type: String) = when (type) {
        RECEPCION     -> "Recepción"
        VENTA         -> "Venta"
        MERMA         -> "Merma"
        AJUSTE        -> "Ajuste"
        TRANSFERENCIA -> "Transferencia"
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
}
