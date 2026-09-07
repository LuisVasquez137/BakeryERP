package com.bakery.erp.model

enum class AssetStatus {
    ACTIVE, IN_REPAIR, DISPOSED, SOLD;

    val label: String get() = when (this) {
        ACTIVE -> "Activo"
        IN_REPAIR -> "En reparación"
        DISPOSED -> "Dado de baja"
        SOLD -> "Vendido"
    }
}

data class FixedAsset(
    val id: String = "",
    val assetCode: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val imageUrl: String? = null,

    // Ubicación
    val branchId: String = "",
    val branchName: String = "",
    val location: String = "",
    val responsible: String? = null,

    // Adquisición
    val purchaseDate: Long = 0L,
    val purchaseCost: Double = 0.0,
    val currency: String = "GTQ",
    val supplierName: String? = null,
    val invoiceNumber: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    val warrantyUntil: Long? = null,

    // Estado
    val status: AssetStatus = AssetStatus.ACTIVE,

    // Depreciación lineal
    val usefulLifeMonths: Int? = null,
    val residualValue: Double = 0.0,

    // Mantenimiento preventivo
    val nextMaintenanceDate: Long? = null,
    val maintenanceIntervalDays: Int? = null,

    // Denormalizado (recalculado al guardar evento)
    val totalMaintenanceCost: Double = 0.0,
    val totalRepairCost: Double = 0.0,
    val totalUpgradeCost: Double = 0.0,
    val historyCount: Int = 0,

    val notes: String? = null,
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

val ASSET_CATEGORIES = listOf(
    "Maquinaria de producción",
    "Mobiliario y equipo",
    "Equipo de cómputo",
    "Vehículos",
    "Herramientas",
    "Inmuebles y mejoras",
    "Otros"
)
