package com.bakery.erp.domain

import com.bakery.erp.domain.report.PurchaseSummary
import com.bakery.erp.model.Purchase
import com.bakery.erp.model.PurchaseLine
import com.bakery.erp.model.PurchasePayment
import kotlinx.coroutines.flow.Flow

interface PurchaseRepository {
    suspend fun getPurchases(companyId: String): List<Purchase>

    /** Lista de compras en vivo: otro equipo registra una y aparece sin reiniciar. */
    fun observePurchases(companyId: String): Flow<List<Purchase>>

    /**
     * Compras del rango, agregadas para el reporte financiero. Ver [aggregatePurchases]:
     * el criterio vive ahí como lógica pura y esto solo trae los datos.
     */
    suspend fun aggregatePurchasesInRange(
        companyId: String,
        fromMillis: Long,
        untilExclusiveMillis: Long
    ): PurchaseSummary
    suspend fun getPurchase(companyId: String, purchaseId: String): Purchase?
    suspend fun savePurchase(companyId: String, purchase: Purchase, lines: List<PurchaseLine>)
    suspend fun getLines(companyId: String, purchaseId: String): List<PurchaseLine>

    /**
     * Reemplaza encabezado y líneas de una compra ya registrada. NO toca inventario: la
     * corrección de stock la orquesta el ViewModel, que es quien conoce los repositorios de
     * insumos y de producto terminado.
     */
    suspend fun updatePurchase(companyId: String, purchase: Purchase, lines: List<PurchaseLine>)
    suspend fun updatePurchaseStatus(companyId: String, purchaseId: String, newStatus: String)

    /**
     * Borra encabezado, líneas y abonos. NO toca inventario: revertir el ingreso lo orquesta
     * el ViewModel antes de llamar acá, porque es quien conoce los repositorios de stock.
     */
    suspend fun deletePurchase(companyId: String, purchaseId: String)

    /**
     * Marca la compra como recibida. Solo escribe el encabezado — el ingreso al inventario
     * y el Kardex los orquesta el ViewModel, que es quien conoce los repositorios de stock.
     */
    suspend fun markReceived(companyId: String, purchaseId: String, receivedAt: Long)

    /** Cambia la fecha de vencimiento de una factura ya registrada. */
    suspend fun updateDueDate(companyId: String, purchaseId: String, dueDate: Long)

    // ── Cuentas por pagar ────────────────────────────────────────────────────

    suspend fun getPayments(companyId: String, purchaseId: String): List<PurchasePayment>

    /**
     * Registra un abono y devuelve la compra ya con `paidAmount`/`status` recalculados.
     * El recálculo vive acá, y no en el ViewModel, para que el saldo denormalizado no pueda
     * quedar desalineado con la subcolección según por dónde se haya entrado.
     */
    suspend fun addPayment(companyId: String, purchaseId: String, payment: PurchasePayment): Purchase?

    /** Borra un abono mal registrado y devuelve la compra con el saldo recalculado. */
    suspend fun deletePayment(companyId: String, purchaseId: String, paymentId: String): Purchase?
}
