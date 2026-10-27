package com.bakery.erp.model

/**
 * Lote de preparación producido en una sucursal. Al crearse descuenta los insumos del
 * inventario de la sucursal (Kardex tipo PRODUCCION) y queda con un saldo en peso
 * ([remainingWeightGrams]) que se consumirá al asociar productos en Fase 2.
 *
 * Independiente de [ProductionLot] (lotes por turno del módulo Producción).
 *
 * Un bizcocho ([Preparation.isCounted]) lleva su saldo en [remainingUnits], no en gramos: nace
 * de la hoja de horneado con lo que salió del horno, y el pastel lo consume por unidad.
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
    val updatedAt: Long = 0L,
    /** Bizcochos que salieron del horno (0 = lote por peso). */
    val producedUnits: Double = 0.0,
    val remainingUnits: Double = 0.0,
    /** Masa por molde con que se hizo ESTA tanda (snapshot: se puede cambiar después). */
    val batterGramsPerUnit: Double = 0.0,
    /** Une los moldes que salieron de la misma horneada (la misma masa batida). */
    val bakeId: String = ""
) {
    val isCounted: Boolean get() = producedUnits > 0.0
}

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
