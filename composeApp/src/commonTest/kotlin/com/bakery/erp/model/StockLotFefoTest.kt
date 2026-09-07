package com.bakery.erp.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El reparto FEFO es el único lugar del módulo donde una equivocación NO se vería: el total
 * del inventario sale igual y lo único que queda mal es *cuál* lote se gastó — que es
 * precisamente el dato del que depende el aviso de caducidad.
 */
class StockLotFefoTest {

    private val tz = TimeZone.UTC

    private fun day(year: Int, month: Int, dayOfMonth: Int): Long =
        LocalDate(year, month, dayOfMonth).atStartOfDayIn(tz).toEpochMilliseconds()

    private fun lot(
        id: String,
        remaining: Double,
        expiry: Long = 0L,
        received: Long = 0L,
        status: String = StockLotStatus.ACTIVE
    ) = StockLot(
        id = id,
        ingredientId = "levadura",
        ingredientName = "Levadura Fleishman",
        branchId = "b1",
        branchIngredientKey = stockLotKey("b1", "levadura"),
        expiryDate = expiry,
        receivedAt = received,
        initialQuantity = remaining,
        remainingQuantity = remaining,
        status = status
    )

    @Test
    fun `consume primero el que vence antes, no el que llego antes`() {
        // La diferencia entre FEFO y FIFO: el lote B entró después pero caduca primero.
        val a = lot("A", 10.0, expiry = day(2026, 12, 1), received = day(2026, 9, 1))
        val b = lot("B", 10.0, expiry = day(2026, 10, 1), received = day(2026, 9, 20))
        val result = allocateFefo(listOf(a, b), 6.0)
        assertEquals(listOf("B"), result.allocations.map { it.lotId })
        assertEquals(6.0, result.allocations.first().quantity)
        assertEquals(0.0, result.shortfall)
    }

    @Test
    fun `un consumo grande encadena varios lotes en orden`() {
        val a = lot("A", 10.0, expiry = day(2026, 10, 1))
        val b = lot("B", 10.0, expiry = day(2026, 11, 1))
        val c = lot("C", 10.0, expiry = day(2026, 12, 1))
        val result = allocateFefo(listOf(c, a, b), 25.0)
        assertEquals(listOf("A", "B", "C"), result.allocations.map { it.lotId })
        assertEquals(listOf(10.0, 10.0, 5.0), result.allocations.map { it.quantity })
        assertEquals(0.0, result.shortfall)
    }

    @Test
    fun `los lotes sin fecha se consumen al final`() {
        // Si hay uno que vence la semana entrante y otro sin fecha, gastar el fechado es lo
        // que evita la merma.
        val sinFecha = lot("SIN", 10.0, expiry = 0L, received = day(2026, 1, 1))
        val conFecha = lot("CON", 10.0, expiry = day(2026, 10, 1), received = day(2026, 9, 1))
        val result = allocateFefo(listOf(sinFecha, conFecha), 12.0)
        assertEquals(listOf("CON", "SIN"), result.allocations.map { it.lotId })
    }

    @Test
    fun `sin ninguna fecha la regla degrada a FIFO por recepcion`() {
        val viejo = lot("VIEJO", 10.0, received = day(2026, 1, 1))
        val nuevo = lot("NUEVO", 10.0, received = day(2026, 6, 1))
        val result = allocateFefo(listOf(nuevo, viejo), 5.0)
        assertEquals(listOf("VIEJO"), result.allocations.map { it.lotId })
    }

    @Test
    fun `un lote agotado no vuelve a entrar al reparto`() {
        val agotado = lot("AGOTADO", 0.0, expiry = day(2026, 10, 1), status = StockLotStatus.DEPLETED)
        val vivo = lot("VIVO", 10.0, expiry = day(2026, 11, 1))
        val result = allocateFefo(listOf(agotado, vivo), 4.0)
        assertEquals(listOf("VIVO"), result.allocations.map { it.lotId })
    }

    @Test
    fun `restos de gramos no cuentan como lote vivo`() {
        // 3 g sobrantes en un saco son un saco vacío, no un saco disponible.
        val resto = lot("RESTO", 0.003, expiry = day(2026, 10, 1))
        val vivo = lot("VIVO", 10.0, expiry = day(2026, 11, 1))
        val result = allocateFefo(listOf(resto, vivo), 2.0)
        assertEquals(listOf("VIVO"), result.allocations.map { it.lotId })
    }

    @Test
    fun `si los lotes no alcanzan se reporta el faltante y no se inventa stock`() {
        // Pasa cuando el libro se despega de la bodega, o cuando un insumo empezó a llevar
        // lotes teniendo existencias previas. El consumo real ya ocurrió: se registra lo que
        // había y el faltante queda a la vista en vez de reventar la producción.
        val a = lot("A", 4.0, expiry = day(2026, 10, 1))
        val result = allocateFefo(listOf(a), 10.0)
        assertEquals(4.0, result.allocations.sumOf { it.quantity })
        assertEquals(6.0, result.shortfall)
    }

    @Test
    fun `sin lotes todo es faltante y no truena`() {
        val result = allocateFefo(emptyList(), 7.0)
        assertTrue(result.allocations.isEmpty())
        assertEquals(7.0, result.shortfall)
    }

    @Test
    fun `consumir cero no toca ningun lote`() {
        val a = lot("A", 10.0, expiry = day(2026, 10, 1))
        val result = allocateFefo(listOf(a), 0.0)
        assertTrue(result.allocations.isEmpty())
        assertEquals(0.0, result.shortfall)
    }

    @Test
    fun `lo repartido nunca excede lo pedido ni el saldo de cada lote`() {
        val lots = listOf(
            lot("A", 3.5, expiry = day(2026, 10, 1)),
            lot("B", 2.25, expiry = day(2026, 10, 15)),
            lot("C", 9.0, expiry = day(2026, 11, 1))
        )
        val result = allocateFefo(lots, 6.0)
        assertEquals(6.0, result.allocations.sumOf { it.quantity }, 0.0001)
        val byId = lots.associateBy { it.id }
        result.allocations.forEach { alloc ->
            assertTrue(alloc.quantity <= byId.getValue(alloc.lotId).remainingQuantity + 0.0001)
        }
    }

    // ── Aviso de caducidad ───────────────────────────────────────────────────

    @Test
    fun `solo avisan los lotes con saldo, fecha y dentro del plazo`() {
        val corte = day(2026, 10, 15)
        val lots = listOf(
            lot("VENCE", 5.0, expiry = day(2026, 10, 1)),
            lot("LEJOS", 5.0, expiry = day(2027, 1, 1)),
            lot("SIN_FECHA", 5.0),
            // El caso que la versión anterior no sabía descartar: comprado y ya consumido.
            lot("CONSUMIDO", 0.0, expiry = day(2026, 10, 2), status = StockLotStatus.DEPLETED)
        )
        assertEquals(listOf("VENCE"), lots.expiringBefore(corte).map { it.id })
    }

    @Test
    fun `los avisos salen del mas urgente al menos`() {
        val lots = listOf(
            lot("B", 5.0, expiry = day(2026, 10, 10)),
            lot("A", 5.0, expiry = day(2026, 10, 1)),
            lot("C", 5.0, expiry = day(2026, 10, 14))
        )
        assertEquals(listOf("A", "B", "C"), lots.expiringBefore(day(2026, 10, 20)).map { it.id })
    }
}
