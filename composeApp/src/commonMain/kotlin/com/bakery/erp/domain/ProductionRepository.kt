package com.bakery.erp.domain

import com.bakery.erp.domain.report.ProductionSummary
import com.bakery.erp.model.OrderPattern
import com.bakery.erp.model.ProductionLot
import com.bakery.erp.model.ProductionLotLine
import com.bakery.erp.model.ProductionOrder
import com.bakery.erp.model.ProductionOrderLine
import com.bakery.erp.model.ProductionShift
import kotlinx.coroutines.flow.Flow

interface ProductionRepository {

    // ── Turnos ────────────────────────────────────────────────────────────────

    suspend fun getShiftsForBranch(companyId: String, branchId: String): List<ProductionShift>
    suspend fun saveShift(companyId: String, shift: ProductionShift)
    suspend fun deleteShift(companyId: String, shiftId: String)

    // ── Pedidos de producción ─────────────────────────────────────────────────

    /**
     * Flow en vivo de todos los pedidos de la organización. Una sola suscripción
     * con snapshot listener; el cliente filtra por sucursales, fechas y turno.
     * Reemplaza el polling múltiple por sucursal y mantiene la lista actualizada
     * en tiempo real (las nuevas órdenes aparecen sin recargar).
     */
    fun observeOrders(companyId: String): Flow<List<ProductionOrder>>

    /**
     * Flow en vivo de todos los lotes de la organización. Usado para enriquecer
     * los pedidos con el estado del lote ("Lote abierto", "En producción", ...).
     */
    fun observeLots(companyId: String): Flow<List<ProductionLot>>

    /**
     * Flow en vivo de las líneas de un lote específico. Empuja cambios cuando se
     * agregan/modifican/eliminan productos del lote (p. ej. al aprobar un pedido
     * tardío). Usado por la pantalla "Lista de Producción" para que los panaderos
     * vean las actualizaciones sin recargar.
     */
    fun observeLotLines(companyId: String, lotId: String): Flow<List<ProductionLotLine>>

    suspend fun getOrdersForBranch(companyId: String, branchId: String): List<ProductionOrder>
    suspend fun getOrdersForProducingBranch(
        companyId: String,
        producingBranchId: String,
        requestedDate: Long
    ): List<ProductionOrder>
    /**
     * Pedidos donde [sellingBranchId] es origen, con [requestedDate] dentro de
     * `[fromMillis, untilMillis)`. Filtrado server-side por rango de fechas para
     * evitar cargar histórico completo.
     */
    suspend fun getOrdersForSellingBranchInRange(
        companyId: String,
        sellingBranchId: String,
        fromMillis: Long,
        untilMillis: Long
    ): List<ProductionOrder>
    /**
     * Pedidos donde [producingBranchId] es destino, con [requestedDate] dentro de
     * `[fromMillis, untilMillis)`.
     */
    suspend fun getOrdersForProducingBranchInRange(
        companyId: String,
        producingBranchId: String,
        fromMillis: Long,
        untilMillis: Long
    ): List<ProductionOrder>
    /** Todos los pedidos PENDING en cualquier fecha para una planta productora. */
    suspend fun getPendingOrdersForProducingBranch(
        companyId: String,
        producingBranchId: String
    ): List<ProductionOrder>
    /**
     * Pedido PENDING (si existe) que coincida con la cuádrupla `(sellingBranch,
     * producingBranch, fecha calendario, shiftId)`. Usado para detectar duplicados
     * en la creación y sugerir editar el existente.
     */
    suspend fun findPendingOrderForCombination(
        companyId: String,
        sellingBranchId: String,
        producingBranchId: String,
        requestedDate: Long,
        shiftId: String
    ): ProductionOrder?
    suspend fun getOrder(companyId: String, orderId: String): ProductionOrder?
    suspend fun getOrderLines(companyId: String, orderId: String): List<ProductionOrderLine>
    suspend fun saveOrder(
        companyId: String,
        order: ProductionOrder,
        lines: List<ProductionOrderLine>
    )
    /**
     * Actualiza un pedido existente: header + reemplaza completamente la subcolección
     * de líneas (borra las que ya no están y escribe/actualiza las nuevas).
     * Falla si [order.id] está vacío.
     */
    suspend fun updateOrder(
        companyId: String,
        order: ProductionOrder,
        lines: List<ProductionOrderLine>
    )
    suspend fun updateOrderStatus(companyId: String, orderId: String, newStatus: String)

    /**
     * Registro de un ajuste post-reparto (`companies/{c}/orderAdjustments`, plana).
     * Solo se agrega: es la constancia de quién cambió qué con el lote ya armado.
     */
    suspend fun saveOrderAdjustment(
        companyId: String,
        adjustment: com.bakery.erp.model.ProductionOrderAdjustment
    )

    suspend fun getOrderAdjustments(
        companyId: String,
        orderId: String
    ): List<com.bakery.erp.model.ProductionOrderAdjustment>

    /**
     * Borrado real del pedido: el documento, su subcolección `lines/` y la muestra
     * que aportaba a las cantidades sugeridas (igual que
     * [updateOrderStatus] con CANCELLED, pero sin dejar el pedido a la vista).
     *
     * **No es el camino normal para deshacer un pedido** — para eso está cancelarlo,
     * que conserva el rastro de que existió. Esto es para sacar de encima pedidos de
     * prueba, y por eso la UI lo limita al OWNER. El borrado de la cascada (lotes,
     * notas de despacho, avisos) lo coordina
     * [com.bakery.erp.presentation.production.ProductionViewModel.deleteOrderPermanently];
     * este método solo se ocupa del pedido y su patrón.
     */
    suspend fun deleteOrder(companyId: String, orderId: String)

    /**
     * Borrado real del lote y sus líneas. Pensado para el lote que queda vacío al
     * borrarse el último pedido que lo alimentaba: un lote sin fuentes no puede
     * despachar ni recomputarse, y quedaría estorbando en la lista del panadero.
     */
    suspend fun deleteLot(companyId: String, lotId: String)

    /**
     * Saca [orderIds] de la planificación de lotes: dejan de aparecer en el
     * aviso de "pendientes en otras fechas". No cambia su estado ni los borra —
     * siguen PENDING y visibles en su propia fecha.
     */
    suspend fun excludeOrdersFromLotPlanning(companyId: String, orderIds: List<String>)
    /**
     * Anexa las líneas de [orderId] al [lotId] existente, fusionando por `productId`:
     * si el producto ya está en el lote suma cantidades; si no, agrega línea nueva.
     * Marca el pedido como CONFIRMED y lo añade a `lot.sourceOrderIds`.
     * No descuenta stock — eso ocurre al completar el lote.
     */
    suspend fun addOrderToLot(
        companyId: String,
        lotId: String,
        orderId: String,
        newLines: List<ProductionLotLine>
    )

    /**
     * Inverso de [addOrderToLot]: resta las líneas de [orderId] del [lotId] (por
     * `productId`). Si una línea del lote queda en 0 o menos, se elimina. Quita
     * [orderId] de `lot.sourceOrderIds` y refresca `lineCount`. NO cambia el
     * estado del pedido (eso lo hace el caller — típicamente para cancelarlo o
     * regresarlo a PENDING).
     */
    suspend fun removeOrderFromLot(
        companyId: String,
        lotId: String,
        orderId: String,
        orderLines: List<ProductionOrderLine>
    )

    /**
     * Anexa las líneas de un [CustomerOrder] al [lotId] — análogo a [addOrderToLot]
     * pero para pedidos de clientes externos. Fusiona por `productId`. Cada línea
     * entrante debe traer su [com.bakery.erp.model.CustomerLineSpec] adentro de
     * `customerSpecs` para que se preserve quién lo pidió. Agrega el [customerOrderId]
     * a `lot.customerOrderIds`. Filtra fuera líneas con productos `requiresProduction=false`
     * (esos se descuentan directo del stock terminado, no entran al lote).
     */
    suspend fun addCustomerOrderToLot(
        companyId: String,
        lotId: String,
        customerOrderId: String,
        newLines: List<ProductionLotLine>
    )

    /**
     * Inverso de [addCustomerOrderToLot]: resta las cantidades de [customerOrderId]
     * y limpia los `customerSpecs` que pertenezcan a ese pedido. Líneas que queden
     * en 0 se eliminan. Quita el id de `lot.customerOrderIds`.
     */
    suspend fun removeCustomerOrderFromLot(
        companyId: String,
        lotId: String,
        customerOrderId: String
    )

    // ── Patrones de pedido (cantidades sugeridas) ─────────────────────────────

    /**
     * Lee el [OrderPattern] denormalizado de una combinación `(sucursal
     * solicitante, planta, turno, día de la semana)`. **1 lectura**. Devuelve
     * null si todavía no se ha construido.
     */
    suspend fun getOrderPattern(companyId: String, patternId: String): OrderPattern?

    /**
     * Reconstruye el patrón escaneando el historial de pedidos de la sucursal y
     * lo persiste. Es la operación cara (headers + subcolección `lines/` de
     * hasta [OrderPattern.MAX_SAMPLES] pedidos); se ejecuta solo cuando
     * [getOrderPattern] devuelve null. Después de esto el patrón se mantiene
     * al día desde [saveOrder] / [updateOrder] / [updateOrderStatus].
     */
    suspend fun rebuildOrderPatternFromHistory(
        companyId: String,
        sellingBranchId: String,
        producingBranchId: String,
        shiftId: String,
        weekday: Int
    ): OrderPattern

    // ── Lotes de producción ───────────────────────────────────────────────────

    suspend fun getLotsForBranch(companyId: String, branchId: String): List<ProductionLot>
    /**
     * Lotes con [productionDate] dentro de `[fromMillis, untilMillis)` para una planta.
     * Si [producingBranchId] está vacío, devuelve los de toda la organización (útil
     * para reportes consolidados a nivel owner).
     */
    suspend fun getLotsForBranchInRange(
        companyId: String,
        producingBranchId: String,
        fromMillis: Long,
        untilMillis: Long
    ): List<ProductionLot>
    /**
     * Lote activo (no CANCELLED) que coincida con `(producingBranchId, productionDate,
     * shiftId)`. Si no existe devuelve null. Usado para bloquear la creación de
     * lotes duplicados y para resolver el panel de "pedidos tardíos".
     * Se compara `productionDate` por día calendario en TZ del sistema.
     */
    suspend fun getActiveLotForBranchDateShift(
        companyId: String,
        producingBranchId: String,
        productionDate: Long,
        shiftId: String
    ): ProductionLot?
    /**
     * Todos los lotes activos (no CANCELLED) que coincidan con `(producingBranchId,
     * productionDate)`, sin filtrar por turno — el análogo de [getActiveLotForBranchDateShift]
     * para plantas de varios turnos, donde un mismo día puede tener un lote por turno.
     * Se compara `productionDate` por día calendario en TZ del sistema.
     */
    suspend fun getActiveLotsForBranchDate(
        companyId: String,
        producingBranchId: String,
        productionDate: Long
    ): List<ProductionLot>
    suspend fun getLot(companyId: String, lotId: String): ProductionLot?
    suspend fun getLotLines(companyId: String, lotId: String): List<ProductionLotLine>
    suspend fun saveLot(
        companyId: String,
        lot: ProductionLot,
        lines: List<ProductionLotLine>
    )
    suspend fun updateLotStatus(companyId: String, lotId: String, newStatus: String)
    suspend fun saveLotLine(companyId: String, lotId: String, line: ProductionLotLine)
    /** Elimina una línea del lote. Falla si el lote no está OPEN. */
    suspend fun deleteLotLine(companyId: String, lotId: String, lineId: String)
    suspend fun completeLot(
        companyId: String,
        lotId: String,
        producedLines: List<ProductionLotLine>
    )
    suspend fun markStockDeducted(companyId: String, lotId: String)

    /**
     * Agrega lotes (+ líneas) en rango para un set de plantas productoras (null/vacío =
     * toda la organización). También calcula `lateOrderCount` cruzando con pedidos
     * `isLateOrder=true` PENDING en el mismo rango.
     */
    suspend fun aggregateProductionInRange(
        companyId: String,
        producingBranchIds: List<String>?,
        fromMillis: Long,
        untilMillis: Long
    ): ProductionSummary
}
