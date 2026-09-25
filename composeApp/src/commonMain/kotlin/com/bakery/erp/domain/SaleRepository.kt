package com.bakery.erp.domain

import com.bakery.erp.domain.report.SalesSummary
import com.bakery.erp.model.CheckoutPayment
import com.bakery.erp.model.Sale
import com.bakery.erp.model.SaleLine
import kotlinx.coroutines.flow.Flow

interface SaleRepository {

    /**
     * Persiste una venta con sus líneas:
     *  1. Para cada línea con productId no-EXPRESS:
     *     - Lee `FinishedProductStock` para snapshot del `avgUnitCost` en `costAtSale`.
     *     - Llama `FinishedStockRepository.applyMovement` (tipo VENTA, referencia SALE).
     *     - Marca `stockDeducted = true`.
     *  2. Para líneas EXPRESS sin productId: `stockDeducted = false`, `costAtSale = 0`.
     *  3. Escribe el doc `Sale` y la subcolección `lines`.
     *  4. Incrementa los totales denormalizados en `CashSession` vía
     *     `CashSessionRepository.incrementSaleTotals`.
     */
    suspend fun createSale(companyId: String, sale: Sale, lines: List<SaleLine>): Sale

    suspend fun getSale(companyId: String, saleId: String): Sale?
    suspend fun getSaleLines(companyId: String, saleId: String): List<SaleLine>

    suspend fun getSalesForSession(companyId: String, sessionId: String): List<Sale>
    fun observeSalesForSession(companyId: String, sessionId: String): Flow<List<Sale>>

    /** Histórico por sucursal en rango de fechas (ms epoch). */
    suspend fun getSalesForBranchInRange(
        companyId: String,
        branchId: String,
        fromMillis: Long,
        untilMillis: Long
    ): List<Sale>

    fun observeSalesForBranchInRange(
        companyId: String,
        branchId: String,
        fromMillis: Long,
        untilMillis: Long
    ): Flow<List<Sale>>

    /**
     * Totales y desgloses (método de pago, cajero, sucursal, producto, día y hora) en un
     * rango de fechas para un set de sucursales. Si [branchIds] es null o vacío, no filtra.
     *
     * **Se sirve de los resúmenes diarios (`salesDaily`), no de las ventas.** Un mes son
     * ~30 documentos por sucursal en vez de una lectura por ticket más una query de líneas
     * por ticket. Cada venta aporta su parte al cobrarse (ver [createSale]).
     *
     * Las ventas anteriores a que existieran los resúmenes **no aparecen** hasta correr
     * [rebuildDailyRollups] sobre ese período.
     */
    suspend fun aggregateSalesInRange(
        companyId: String,
        branchIds: List<String>?,
        fromMillis: Long,
        untilMillis: Long
    ): SalesSummary

    /**
     * Cuentas del restaurante ya cobradas al cliente cuyo efectivo todavía no llegó a
     * ninguna caja de [branchId]. Es la lista que ven **todas** las cajas de la sucursal.
     *
     * Acotada al rango `[fromMillis, untilMillis)` — en la práctica, el día de hoy. Cada día
     * arranca de cero: una cuenta que nadie entregó ayer NO puede seguir apareciendo hoy, o
     * la lista se convierte en un basurero que nadie vuelve a mirar. Lo no entregado sigue
     * en la base como pendiente y se ve en el cierre del día que le toca, no en el de hoy.
     */
    fun observePendingCollections(
        companyId: String,
        branchId: String,
        fromMillis: Long,
        untilMillis: Long
    ): Flow<List<Sale>>

    /**
     * Registra que esta caja recibió el dinero de una cuenta del restaurante: la venta pasa
     * a COMPLETED, queda atribuida a la sesión/caja/cajero y suma a los totales de ESA
     * gaveta, desglosada por método de pago.
     *
     * Todas las cajas de la sucursal ven la misma lista, así que dos pueden tocar la misma
     * cuenta a la vez: la transición se hace en transacción y la segunda falla en vez de
     * duplicar el ingreso en dos gavetas.
     *
     * No toca el resumen diario: el ingreso ya se contó al llegar la venta. Lo que cambia
     * acá es **dónde está el dinero**, no si se vendió.
     */
    suspend fun collectPendingSale(
        companyId: String,
        saleId: String,
        sessionId: String,
        registerId: String,
        registerName: String,
        cashierId: String,
        cashierName: String,
        /**
         * Presente cuando la cuenta se cobró desde el carrito junto con otras cosas: la
         * venta queda con el método y los datos del pago de ESE cobro. Null = el mesero
         * entregó el dinero y solo se registra que entró a esta gaveta.
         */
        checkout: CheckoutPayment? = null
    ): Sale

    /**
     * Reconstruye los resúmenes diarios del rango leyendo las ventas y sus líneas — el
     * camino caro, a propósito: sirve para rellenar el histórico anterior a los resúmenes
     * y para reparar un día que haya quedado torcido.
     *
     * Sobreescribe el documento del día completo en vez de ajustarlo por diferencia: se
     * recalcula desde la fuente de verdad, así que lo que hubiera antes no importa.
     *
     * @return cuántos documentos de resumen se escribieron.
     */
    suspend fun rebuildDailyRollups(
        companyId: String,
        fromMillis: Long,
        untilMillis: Long
    ): Int
}
