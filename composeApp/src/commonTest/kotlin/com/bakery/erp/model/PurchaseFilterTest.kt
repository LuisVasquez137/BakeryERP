package com.bakery.erp.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lo que se verifica acá es el filtrado y la proyección de estado — no el formato.
 * Son los dos lugares donde un error no se ve: una factura que no aparece en la lista
 * parece no existir, y una vencida que no se pinta como vencida no la cobra nadie.
 */
class PurchaseFilterTest {

    private val tz = TimeZone.UTC

    private fun day(year: Int, month: Int, dayOfMonth: Int): Long =
        LocalDate(year, month, dayOfMonth).atStartOfDayIn(tz).toEpochMilliseconds()

    /** 15 de junio de 2026, mediodía. */
    private val now = day(2026, 6, 15) + 12 * 3_600_000L

    private fun purchase(
        id: String,
        date: Long,
        total: Double = 100.0,
        paid: Double = 0.0,
        due: Long = 0L,
        status: String = PurchaseStatus.RECEIVED_PENDING_PAYMENT,
        supplier: String = "Molinos",
        supplierId: String = "s1",
        branchId: String = "b1",
        items: List<String> = emptyList()
    ) = Purchase(
        id = id,
        branchId = branchId,
        branchName = "Planta",
        supplierId = supplierId,
        supplierName = supplier,
        date = date,
        totalAmount = total,
        paidAmount = paid,
        dueDate = due,
        status = status,
        itemNames = items
    )

    // ── Rangos ───────────────────────────────────────────────────────────────

    @Test
    fun `este mes incluye hoy y excluye el mes pasado`() {
        val range = purchaseDateRange(PurchasePeriod.THIS_MONTH, now, tz)
        assertTrue(day(2026, 6, 1) in range)
        assertTrue(now in range)
        assertTrue(day(2026, 5, 31) !in range)
    }

    @Test
    fun `mes pasado no toca ni mayo 1 anterior ni junio`() {
        val range = purchaseDateRange(PurchasePeriod.LAST_MONTH, now, tz)
        assertTrue(day(2026, 5, 1) in range)
        assertTrue(day(2026, 5, 31) in range)
        assertTrue(day(2026, 6, 1) !in range)
        assertTrue(day(2026, 4, 30) !in range)
    }

    @Test
    fun `ultimos 3 meses arranca el primero de abril`() {
        val range = purchaseDateRange(PurchasePeriod.LAST_3_MONTHS, now, tz)
        assertTrue(day(2026, 4, 1) in range)
        assertTrue(day(2026, 3, 31) !in range)
    }

    @Test
    fun `enero retrocede de anio al pedir el mes pasado`() {
        val enero = day(2026, 1, 10)
        val range = purchaseDateRange(PurchasePeriod.LAST_MONTH, enero, tz)
        assertTrue(day(2025, 12, 1) in range)
        assertTrue(day(2025, 12, 31) in range)
        assertTrue(day(2026, 1, 1) !in range)
    }

    @Test
    fun `rango personalizado incluye el ultimo dia completo`() {
        val range = purchaseDateRange(
            PurchasePeriod.CUSTOM, now, tz,
            customFrom = day(2026, 6, 1),
            customUntil = day(2026, 6, 10)
        )
        // Una compra registrada a las 6 pm del último día sigue siendo de ese día.
        assertTrue(day(2026, 6, 10) + 18 * 3_600_000L in range)
        assertTrue(day(2026, 6, 11) !in range)
    }

    @Test
    fun `todo el historico no descarta nada`() {
        val range = purchaseDateRange(PurchasePeriod.ALL, now, tz)
        assertTrue(day(2019, 1, 1) in range)
        assertTrue(now in range)
    }

    // ── Estado proyectado ────────────────────────────────────────────────────

    @Test
    fun `una factura con saldo y vencimiento pasado es vencida`() {
        val p = purchase("1", day(2026, 5, 1), due = day(2026, 6, 1))
        assertEquals(PurchaseDisplayStatus.OVERDUE, p.displayStatus(now))
    }

    @Test
    fun `una factura saldada nunca aparece vencida aunque paso su fecha`() {
        val p = purchase("1", day(2026, 5, 1), total = 100.0, paid = 100.0, due = day(2026, 6, 1))
        assertEquals(PurchaseDisplayStatus.PAID, p.displayStatus(now))
    }

    @Test
    fun `un centavo de diferencia cuenta como saldada`() {
        val p = purchase("1", day(2026, 6, 10), total = 100.0, paid = 99.995)
        assertEquals(PurchaseDisplayStatus.PAID, p.displayStatus(now))
    }

    @Test
    fun `un abono parcial sin vencer se muestra abonada`() {
        val p = purchase("1", day(2026, 6, 10), total = 100.0, paid = 40.0, due = day(2026, 7, 10))
        assertEquals(PurchaseDisplayStatus.PARTIALLY_PAID, p.displayStatus(now))
        assertEquals(60.0, p.balanceDue)
    }

    @Test
    fun `pendiente de recibir manda sobre el vencimiento`() {
        // Pagar por adelantado algo que no ha llegado no lo mete a la bodega, y una compra
        // que todavía no entró no debe contaminar el rojo de "vencidas".
        val p = purchase(
            "1", day(2026, 5, 1), due = day(2026, 6, 1),
            status = PurchaseStatus.PENDING_RECEIPT
        )
        assertEquals(PurchaseDisplayStatus.PENDING_RECEIPT, p.displayStatus(now))
    }

    @Test
    fun `sin fecha de vencimiento no hay dias por contar`() {
        assertEquals(null, purchase("1", now).daysUntilDue(now))
    }

    @Test
    fun `los dias de mora se cuentan hacia atras`() {
        val p = purchase("1", day(2026, 5, 1), due = day(2026, 6, 10))
        assertEquals(-5, p.daysUntilDue(now))
    }

    // ── Búsqueda y filtros ───────────────────────────────────────────────────

    @Test
    fun `buscar un insumo encuentra la factura que lo lleva dentro`() {
        val conAceite = purchase("1", day(2026, 6, 2), items = listOf("Aceite vegetal", "Harina dura"))
        val sinAceite = purchase("2", day(2026, 6, 3), items = listOf("Azúcar"))
        val result = listOf(conAceite, sinAceite)
            .applyPurchaseFilters(PurchaseFilters(query = "aceite"), now, tz)
        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun `la busqueda ignora mayusculas en proveedor y factura`() {
        val p = purchase("1", day(2026, 6, 2), supplier = "Molinos Modernos")
            .copy(invoiceNumber = "A-4471")
        val base = listOf(p)
        assertEquals(1, base.applyPurchaseFilters(PurchaseFilters(query = "MOLINOS"), now, tz).size)
        assertEquals(1, base.applyPurchaseFilters(PurchaseFilters(query = "a-44"), now, tz).size)
    }

    @Test
    fun `el filtro de estado usa el estado proyectado, no el guardado`() {
        val vencida = purchase("1", day(2026, 6, 1), due = day(2026, 6, 5))
        val alDia = purchase("2", day(2026, 6, 2), due = day(2026, 7, 5))
        val result = listOf(vencida, alDia).applyPurchaseFilters(
            PurchaseFilters(statuses = setOf(PurchaseDisplayStatus.OVERDUE)), now, tz
        )
        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun `varios filtros se combinan con Y, no con O`() {
        val a = purchase("1", day(2026, 6, 2), supplierId = "s1", branchId = "b1")
        val b = purchase("2", day(2026, 6, 3), supplierId = "s2", branchId = "b1")
        val c = purchase("3", day(2026, 6, 4), supplierId = "s1", branchId = "b2")
        val result = listOf(a, b, c).applyPurchaseFilters(
            PurchaseFilters(supplierIds = setOf("s1"), branchIds = setOf("b1")), now, tz
        )
        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun `el periodo por defecto deja fuera lo del mes pasado`() {
        val vieja = purchase("1", day(2026, 5, 20))
        val nueva = purchase("2", day(2026, 6, 2))
        val enMes = listOf(vieja, nueva).applyPurchaseFilters(PurchaseFilters(), now, tz)
        assertEquals(listOf("2"), enMes.map { it.id })

        val todo = listOf(vieja, nueva)
            .applyPurchaseFilters(PurchaseFilters(period = PurchasePeriod.ALL), now, tz)
        assertEquals(setOf("1", "2"), todo.map { it.id }.toSet())
    }

    @Test
    fun `ordenar por monto no altera el conjunto filtrado`() {
        val a = purchase("1", day(2026, 6, 2), total = 50.0)
        val b = purchase("2", day(2026, 6, 3), total = 500.0)
        val result = listOf(a, b).applyPurchaseFilters(
            PurchaseFilters(sort = PurchaseSort.AMOUNT_DESC), now, tz
        )
        assertEquals(listOf("2", "1"), result.map { it.id })
    }

    // ── KPIs ─────────────────────────────────────────────────────────────────

    @Test
    fun `los kpis separan vencido de por pagar sin contarlo dos veces`() {
        val vencida = purchase("1", day(2026, 6, 1), total = 200.0, due = day(2026, 6, 5))
        val alDia = purchase("2", day(2026, 6, 2), total = 300.0, paid = 100.0, due = day(2026, 7, 5))
        val pagada = purchase("3", day(2026, 6, 3), total = 150.0, paid = 150.0, status = PurchaseStatus.PAID)
        val porRecibir = purchase("4", day(2026, 6, 4), total = 80.0, status = PurchaseStatus.PENDING_RECEIPT)

        val kpis = listOf(vencida, alDia, pagada, porRecibir).purchaseKpis(now)
        assertEquals(4, kpis.invoiceCount)
        assertEquals(730.0, kpis.total)
        assertEquals(250.0, kpis.paid)
        // 200 de la vencida + 200 de saldo de la que está al día. La pagada no suma, y la que
        // aún no se recibe tampoco: todavía no es una deuda exigible.
        assertEquals(400.0, kpis.outstanding)
        assertEquals(200.0, kpis.overdue)
        assertEquals(1, kpis.overdueCount)
        assertEquals(1, kpis.pendingReceiptCount)
    }

    // ── Total de líneas ──────────────────────────────────────────────────────

    @Test
    fun `el total mezcla lineas con formato y sin formato`() {
        val conFormato = PurchaseLine(
            ingredientId = "i1", packagesReceived = 3.0, packageCost = 200.0,
            quantityReceived = 300.0, unitCost = 2.0
        )
        val sinFormato = PurchaseLine(
            ingredientId = "i2", quantityReceived = 10.0, unitCost = 5.5
        )
        // Antes el total se descartaba entero si UNA línea no traía costo por presentación.
        assertEquals(600.0 + 55.0, listOf(conFormato, sinFormato).purchaseTotal())
    }

    // ── IVA (precios capturados CON impuesto incluido) ───────────────────────

    @Test
    fun `el iva se despeja hacia atras, no se suma encima`() {
        val b = taxBreakdownFromGross(112.0, 12.0)
        assertEquals(112.0, b.total)
        assertEquals(100.0, b.base, 0.001)
        assertEquals(12.0, b.tax, 0.001)
    }

    @Test
    fun `base mas iva siempre reconstruye el total capturado`() {
        // Es la propiedad que hace que la factura cuadre con el papel. Si se rompe, el
        // desglose deja de ser un desglose y pasa a ser otro número.
        listOf(0.01, 7.35, 318.0, 44616.5).forEach { total ->
            val b = taxBreakdownFromGross(total, 12.0)
            assertEquals(total, b.base + b.tax, 0.0001)
        }
    }

    @Test
    fun `sin tasa el total queda intacto y sin impuesto`() {
        val b = taxBreakdownFromGross(500.0, 0.0)
        assertEquals(500.0, b.base)
        assertEquals(0.0, b.tax)
    }

    @Test
    fun `un total en cero no divide entre nada`() {
        val b = taxBreakdownFromGross(0.0, 12.0)
        assertEquals(0.0, b.total)
        assertEquals(0.0, b.tax)
    }

    // ── Cuadre contra el papel ───────────────────────────────────────────────

    @Test
    fun `la diferencia con la factura declarada tiene signo`() {
        val capturadoDeMas = purchase("1", now, total = 120.0).copy(declaredTotal = 100.0)
        assertEquals(20.0, capturadoDeMas.captureDifference())
        val capturadoDeMenos = purchase("2", now, total = 80.0).copy(declaredTotal = 100.0)
        assertEquals(-20.0, capturadoDeMenos.captureDifference())
        // Sin total declarado no hay nada que comparar — no es lo mismo que "cuadra".
        assertEquals(null, purchase("3", now, total = 80.0).captureDifference())
    }

    // ── Caducidad ────────────────────────────────────────────────────────────

    @Test
    fun `el vencimiento de la compra es el mas proximo de sus lineas`() {
        val lines = listOf(
            PurchaseLine(ingredientId = "i1", ingredientName = "Manteca", expiryDate = day(2026, 12, 1)),
            PurchaseLine(ingredientId = "i2", ingredientName = "Levadura", expiryDate = day(2026, 10, 5)),
            PurchaseLine(ingredientId = "i3", ingredientName = "Harina")
        )
        assertEquals(day(2026, 10, 5), lines.nearestExpiry())
        assertEquals("Levadura", lines.nearestExpiryLine()?.ingredientName)
    }

    @Test
    fun `sin lineas perecederas no hay vencimiento`() {
        val lines = listOf(PurchaseLine(ingredientId = "i1", ingredientName = "Sal"))
        assertEquals(0L, lines.nearestExpiry())
        assertEquals(null, lines.nearestExpiryLine())
    }

    @Test
    fun `una linea de reventa aporta cantidad por costo unitario`() {
        val reventa = PurchaseLine(
            lineKind = PurchaseLineKind.RESALE_PRODUCT,
            productId = "p1", productName = "Coca 500ml",
            quantityReceived = 24.0, unitCost = 5.0
        )
        assertEquals(120.0, reventa.lineTotal)
        assertEquals("Coca 500ml", reventa.itemName)
        assertEquals("p1", reventa.itemId)
    }
}
