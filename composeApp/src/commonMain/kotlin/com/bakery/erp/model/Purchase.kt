package com.bakery.erp.model

data class Purchase(
    val id: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val supplierId: String = "",
    val supplierName: String = "",
    val date: Long = 0L,
    val notes: String? = null,
    val lineCount: Int = 0,
    val createdAt: Long = 0L,
    val invoiceNumber: String? = null,
    val receivedById: String = "",
    val receivedByName: String = "",
    val status: String = PurchaseStatus.RECEIVED_PENDING_PAYMENT
)

object PurchaseStatus {
    const val PENDING_RECEIPT = "PENDING_RECEIPT"
    const val RECEIVED_PENDING_PAYMENT = "RECEIVED_PENDING_PAYMENT"
    const val PAID = "PAID"

    fun label(status: String) = when (status) {
        PENDING_RECEIPT -> "Pendiente de recibir"
        RECEIVED_PENDING_PAYMENT -> "Pendiente de pago"
        PAID -> "Pagado"
        else -> "Desconocido"
    }
}

data class PurchaseLine(
    val id: String = "",
    val ingredientId: String = "",
    val ingredientName: String = "",
    val ingredientUnit: String = "",       // Unidad base (allowedUnits[0]) — para stock
    val quantityReceived: Double = 0.0,   // En unidad base — lo que se suma al stock
    val packagesReceived: Double = 0.0,   // Cuántas presentaciones recibió el baker (display)
    val packageDescription: String = "",  // Descripción del formato (ej: "Saco 100 lb") (display)
    val unitCost: Double = 0.0,
    val packageCost: Double = 0.0,        // Costo total del paquete al momento de la compra (ej: Q200/saco)
    val formatId: String = ""             // ID del PurchaseFormat — para actualizar precio si cambia
)
