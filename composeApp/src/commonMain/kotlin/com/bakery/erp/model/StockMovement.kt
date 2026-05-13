package com.bakery.erp.model

data class StockMovement(
    val id: String = "",
    val type: String = "COMPRA",      // COMPRA | AJUSTE | PRODUCCION
    val quantity: Double = 0.0,       // positivo = entrada, negativo = salida
    val balanceBefore: Double = 0.0,
    val balanceAfter: Double = 0.0,
    val branchId: String = "",
    val branchName: String = "",
    val reference: String = "",       // purchaseId u otra referencia
    val date: Long = 0L,
    val notes: String? = null
)
