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
    /**
     * Horarios de cuadre de ESTA caja ("14:00", "21:00"). Son por caja y no por sucursal: en
     * una misma sucursal cada caja cuadra a su hora. Un cierre —o solo entrar a la pantalla de
     * cierre— fuera de estos horarios queda marcado en la bitácora y le avisa al dueño. Vacío =
     * sin restricción.
     */
    val closeSchedules: List<String> = emptyList(),
    /** Minutos de tolerancia antes y después de cada horario. */
    val closeToleranceMinutes: Int = DEFAULT_CLOSE_TOLERANCE_MINUTES,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
