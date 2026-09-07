package com.bakery.erp.model

/**
 * Lote de preparación producido en una sucursal. Al crearse descuenta los insumos del
 * inventario de la sucursal (Kardex tipo PRODUCCION) y queda con un saldo en peso
 * ([remainingWeightGrams]) que se consumirá al asociar productos en Fase 2.
 *
 * Independiente de [ProductionLot] (lotes por turno del módulo Producción).
 */
data class PreparationBatch(
    val id: String = "",
    val preparationId: String = "",
    val preparationName: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val batchCount: Int = 1,                  // N lotes producidos
    val producedWeightGrams: Double = 0.0,    // batchWeightGrams * batchCount (calculado al crear)
    val remainingWeightGrams: Double = 0.0,   // saldo (= producedWeightGrams al crear)
    val status: String = PreparationBatchStatus.ACTIVE,
    val producedByUserId: String = "",
    val producedByUserName: String = "",
    val producedAt: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

object PreparationBatchStatus {
    const val ACTIVE = "ACTIVE"
    const val DEPLETED = "DEPLETED"
    const val CANCELLED = "CANCELLED"

    fun label(status: String) = when (status) {
        ACTIVE    -> "Activo"
        DEPLETED  -> "Agotado"
        CANCELLED -> "Cancelado"
        else      -> "Desconocido"
    }
}
