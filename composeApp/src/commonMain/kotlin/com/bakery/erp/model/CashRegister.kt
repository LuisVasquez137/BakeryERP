package com.bakery.erp.model

/** Caja física dentro de una sucursal. Una sucursal puede tener varias. */
data class CashRegister(
    val id: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val name: String = "",
    val active: Boolean = true,
    /**
     * Archivada (soft-delete). Se oculta de la administración y del POS pero se conserva
     * para que las ventas/sesiones históricas que la referencian sigan mostrando su nombre.
     * Distinto de [active] (inactiva pero aún utilizable / re-activable).
     */
    val deleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
