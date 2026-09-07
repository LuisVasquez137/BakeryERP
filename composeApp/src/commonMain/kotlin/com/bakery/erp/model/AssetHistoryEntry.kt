package com.bakery.erp.model

enum class AssetEventType {
    PURCHASE,
    MAINTENANCE,
    REPAIR,
    UPGRADE,
    INSPECTION,
    TRANSFER,
    DISPOSAL,
    SALE,
    NOTE;

    val label: String get() = when (this) {
        PURCHASE -> "Compra"
        MAINTENANCE -> "Mantenimiento"
        REPAIR -> "Reparación"
        UPGRADE -> "Mejora"
        INSPECTION -> "Inspección"
        TRANSFER -> "Transferencia"
        DISPOSAL -> "Baja"
        SALE -> "Venta"
        NOTE -> "Nota"
    }

    val hasCost: Boolean get() = when (this) {
        INSPECTION, TRANSFER, NOTE -> false
        else -> true
    }
}

data class AssetHistoryEntry(
    val id: String = "",
    val assetId: String = "",
    val type: AssetEventType = AssetEventType.NOTE,
    val date: Long = 0L,
    val description: String = "",
    val cost: Double = 0.0,
    val providerName: String? = null,
    val invoiceNumber: String? = null,
    /**
     * Factura de compra que generó el evento, cuando el mantenimiento se capturó desde
     * Compras en vez de a mano. Permite rehacer el historial si esa factura se corrige o
     * se borra, en vez de dejar un mantenimiento fantasma moviendo la próxima fecha del
     * horno. Vacío = evento registrado directamente en el activo.
     */
    val sourcePurchaseId: String = "",
    val nextMaintenanceDate: Long? = null,
    val fromBranchId: String? = null,
    val fromBranchName: String? = null,
    val toBranchId: String? = null,
    val toBranchName: String? = null,
    val createdBy: String = "",
    val createdByName: String = "",
    val createdAt: Long = 0L
)
