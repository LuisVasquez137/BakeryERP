package com.bakery.erp.domain.report

import com.bakery.erp.model.Purchase
import com.bakery.erp.model.PurchaseKind
import com.bakery.erp.model.PurchaseLine
import com.bakery.erp.model.PurchaseLineKind
import com.bakery.erp.model.PurchaseStatus
import com.bakery.erp.model.amountSplit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Las compras como salida de dinero en el reporte financiero.
 *
 * El modo de fallo que importa acá no es un total mal sumado: es el **doble conteo**. Los
 * suministros y los servicios de una compra ya generaron un `Expense`, así que si el reporte
 * suma la compra entera a los egresos, el cloro y el mantenimiento del horno se restan dos
 * veces y la utilidad sale peor de lo que es.
 *
 * El otro modo de fallo es el histórico: las compras registradas antes del desglose no traen
 * los campos, y clasificarlas mal las movería de lado en un reporte que el dueño usa para
 * decidir.
 */
class PurchaseAggregationTest {

    private val ENE = 1_735_689_600_000L          // 2025-01-01 aprox, en UTC
    private val FEB = ENE + 31L * 86_400_000L

    private fun linea(kind: String, total: Double) = PurchaseLine(
        id = "l_$kind$total",
        lineKind = kind,
        quantityReceived = 1.0,
        unitCost = total
    )

    private fun compra(
        id: String = "p1",
        date: Long = ENE + 86_400_000L,
        lines: List<PurchaseLine> = listOf(linea(PurchaseLineKind.INGREDIENT, 100.0)),
        status: String = PurchaseStatus.PAID,
        paid: Double? = null,
        kind: String = PurchaseKind.INVOICE
    ): Purchase {
        val split = lines.amountSplit()
        val total = split.total
        return Purchase(
            id = id,
            kind = kind,
            date = date,
            status = status,
            totalAmount = total,
            paidAmount = paid ?: total,
            inventoryAmount = split.inventory,
            expensedAmount = split.expensed,
            lineKinds = lines.map { it.lineKind }.distinct()
        )
    }

    // ── Desglose inventario / gasto ──────────────────────────────────────────

    @Test
    fun `una factura mixta reparte el monto entre inventario y gasto`() {
        // La factura de la abarrotería: huevos (inventario), cloro (gasto), flete (gasto).
        val lines = listOf(
            linea(PurchaseLineKind.INGREDIENT, 300.0),
            linea(PurchaseLineKind.SUPPLY, 50.0),
            linea(PurchaseLineKind.SERVICE, 75.0)
        )
        val summary = aggregatePurchases(listOf(compra(lines = lines)), ENE, FEB)

        assertEquals(425.0, summary.total, 0.001)
        assertEquals(300.0, summary.inventoryTotal, 0.001)
        assertEquals(125.0, summary.expensedTotal, 0.001)
        assertEquals(0.0, summary.unclassifiedTotal, 0.001)
    }

    @Test
    fun `la reventa cuenta como inventario, no como gasto`() {
        // Va a FinishedProductStock: es un activo hasta que se vende, igual que la harina.
        val lines = listOf(linea(PurchaseLineKind.RESALE_PRODUCT, 200.0))
        val summary = aggregatePurchases(listOf(compra(lines = lines)), ENE, FEB)

        assertEquals(200.0, summary.inventoryTotal, 0.001)
        assertEquals(0.0, summary.expensedTotal, 0.001)
    }

    // ── Histórico sin los campos denormalizados ──────────────────────────────

    @Test
    fun `una compra vieja sin lineKinds cuenta como materia prima`() {
        // Antes de los cuatro tipos de línea el módulo solo sabía registrar materia prima,
        // así que deducirlo del encabezado clasifica bien el histórico sin migrar nada.
        val vieja = Purchase(
            id = "old",
            date = ENE + 86_400_000L,
            status = PurchaseStatus.PAID,
            totalAmount = 900.0,
            paidAmount = 900.0
        )
        val summary = aggregatePurchases(listOf(vieja), ENE, FEB)

        assertEquals(900.0, summary.inventoryTotal, 0.001)
        assertEquals(0.0, summary.unclassifiedTotal, 0.001)
    }

    @Test
    fun `una compra mixta sin desglose guardado queda sin clasificar, no repartida a ojo`() {
        val mixtaSinCampos = Purchase(
            id = "mix",
            date = ENE + 86_400_000L,
            status = PurchaseStatus.PAID,
            totalAmount = 400.0,
            paidAmount = 400.0,
            lineKinds = listOf(PurchaseLineKind.INGREDIENT, PurchaseLineKind.SUPPLY)
        )
        val summary = aggregatePurchases(listOf(mixtaSinCampos), ENE, FEB)

        assertEquals(0.0, summary.inventoryTotal, 0.001)
        assertEquals(0.0, summary.expensedTotal, 0.001)
        assertEquals(400.0, summary.unclassifiedTotal, 0.001)
        // El total no se pierde: sigue siendo dinero que salió.
        assertEquals(400.0, summary.total, 0.001)
    }

    @Test
    fun `una compra de solo suministros sin desglose guardado va toda a gasto`() {
        val soloSupply = Purchase(
            id = "sup",
            date = ENE + 86_400_000L,
            status = PurchaseStatus.PAID,
            totalAmount = 60.0,
            paidAmount = 60.0,
            lineKinds = listOf(PurchaseLineKind.SUPPLY)
        )
        val summary = aggregatePurchases(listOf(soloSupply), ENE, FEB)

        assertEquals(60.0, summary.expensedTotal, 0.001)
        assertEquals(0.0, summary.unclassifiedTotal, 0.001)
    }

    // ── Qué entra y qué no ───────────────────────────────────────────────────

    @Test
    fun `una compra por recibir no cuenta como salida del mes`() {
        // No entró a la bodega ni generó gasto todavía: contarla mostraría dinero que no se
        // movió. Es la misma regla que sigue el inventario.
        val porRecibir = compra(id = "pr", status = PurchaseStatus.PENDING_RECEIPT, paid = 0.0)
        val summary = aggregatePurchases(listOf(porRecibir), ENE, FEB)

        assertEquals(0, summary.purchasesCount)
        assertEquals(0.0, summary.total, 0.001)
    }

    @Test
    fun `las compras de otro mes quedan fuera del rango`() {
        val enero = compra(id = "a", date = ENE + 86_400_000L)
        val febrero = compra(id = "b", date = FEB + 86_400_000L)
        val summary = aggregatePurchases(listOf(enero, febrero), ENE, FEB)

        assertEquals(1, summary.purchasesCount)
        assertEquals(100.0, summary.total, 0.001)
    }

    @Test
    fun `separa lo pagado de lo que queda debiendo`() {
        // Una factura a crédito comprometió dinero pero todavía no lo sacó de la caja.
        val aCredito = compra(
            id = "cred",
            lines = listOf(linea(PurchaseLineKind.INGREDIENT, 1000.0)),
            status = PurchaseStatus.RECEIVED_PENDING_PAYMENT,
            paid = 400.0
        )
        val summary = aggregatePurchases(listOf(aCredito), ENE, FEB)

        assertEquals(1000.0, summary.total, 0.001)
        assertEquals(400.0, summary.paidTotal, 0.001)
        assertEquals(600.0, summary.pendingTotal, 0.001)
    }

    @Test
    fun `cuenta por separado facturas y compras rapidas`() {
        val summary = aggregatePurchases(
            listOf(
                compra(id = "f1", kind = PurchaseKind.INVOICE),
                compra(id = "q1", kind = PurchaseKind.QUICK),
                // Guardada con el nombre viejo: sigue siendo una compra rápida.
                compra(id = "q2", kind = PurchaseKind.LEGACY_MARKET_RUN)
            ),
            ENE, FEB
        )

        assertEquals(1, summary.invoiceCount)
        assertEquals(2, summary.quickCount)
    }

    @Test
    fun `sin compras devuelve el resumen vacio`() {
        assertEquals(PurchaseSummary(), aggregatePurchases(emptyList(), ENE, FEB))
    }
}
