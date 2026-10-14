package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reglas de los traslados entre sucursales: qué se puede mandar, cómo viajan los lotes y el faltante. */
class StockTransferTest {

    private val pan = StockTransferLine(kind = StockTransferItemKind.PRODUCT, itemId = "p1", itemName = "Pirujo", quantity = 100.0, unitCost = 0.3)
    private fun transfer(vararg lines: StockTransferLine) = StockTransfer(
        originBranchId = "a", originBranchName = "Bombero",
        destinationBranchId = "b", destinationBranchName = "Pacajá",
        lines = lines.toList()
    )

    @Test
    fun noSeMandaANingunLadoNiASiMisma() {
        assertNull(stockTransferSendError(transfer(pan)))
        assertNotNull(stockTransferSendError(transfer(pan).copy(destinationBranchId = "")))
        assertNotNull(stockTransferSendError(transfer(pan).copy(destinationBranchId = "a")))
        assertNotNull(stockTransferSendError(transfer()))
        assertNotNull(stockTransferSendError(transfer(pan, pan)))
    }

    @Test
    fun soloSeTrasladaLoQueLlevaInventario() {
        assertTrue(Ingredient(usage = IngredientUsage.PRODUCTION).isTransferable())
        assertTrue(Ingredient(usage = IngredientUsage.SUPPLY, tracksStock = true).isTransferable())
        // El cloro que solo se gasta no tiene existencias que mover.
        assertFalse(Ingredient(usage = IngredientUsage.SUPPLY, tracksStock = false).isTransferable())
        assertFalse(Ingredient(active = false).isTransferable())
    }

    @Test
    fun soloSeRecibeLoQueVaEnCamino() {
        val t = transfer(pan)
        assertNull(stockTransferReceiveError(t, listOf(90.0)))
        assertNotNull(stockTransferReceiveError(t, emptyList()))
        assertNotNull(stockTransferReceiveError(t, listOf(-1.0)))
        assertNotNull(stockTransferReceiveError(t.copy(status = StockTransferStatus.RECEIVED), listOf(90.0)))
        assertNotNull(stockTransferReceiveError(t.copy(status = StockTransferStatus.CANCELLED), listOf(90.0)))
    }

    @Test
    fun elFaltanteSeCalculaAlRecibir() {
        val t = transfer(pan.copy(receivedQuantity = 90.0)).copy(status = StockTransferStatus.RECEIVED)
        assertEquals(10.0, t.lines.single().shortage)
        assertEquals(1, t.shortages.size)
        assertEquals(27.0, t.receivedValue, 1e-9)
        assertEquals(3.0, t.shortageValue, 1e-9)
        // De más no es faltante.
        val extra = transfer(pan.copy(receivedQuantity = 105.0)).copy(status = StockTransferStatus.RECEIVED)
        assertTrue(extra.shortages.isEmpty())
        assertEquals(5.0, extra.lines.single().surplus)
    }

    @Test
    fun losLotesSalenPorFefoYLlegaPrimeroElQueVenceAntes() {
        val lots = listOf(
            StockLot(id = "tarde", expiryDate = 3_000L, remainingQuantity = 20.0, unitCost = 1.0),
            StockLot(id = "pronto", expiryDate = 1_000L, remainingQuantity = 10.0, unitCost = 2.0)
        )
        val out = transferLotsFrom(lots, 25.0)
        assertEquals(listOf(1_000L, 3_000L), out.map { it.expiryDate })
        assertEquals(listOf(10.0, 15.0), out.map { it.quantity })
        assertEquals(2.0, out.first().unitCost)

        // Llegaron 12: el lote que vence antes completo, del otro solo 2.
        val arrived = lotsForReceived(out, 12.0)
        assertEquals(listOf(10.0, 2.0), arrived.map { it.quantity })
        assertTrue(lotsForReceived(out, 0.0).isEmpty())
    }

    @Test
    fun elCostoDelInsumoVaPorUnidadDelStock() {
        // Saco de 100 lb a Q250 sobre una harina que se guarda en lb: Q2.50 la libra.
        val saco = PurchaseFormat(id = "s", description = "Saco 100 lb", unit = "lb", unitsPerPackage = 100.0, packageCost = 250.0, isDefault = true)
        val harina = Ingredient(allowedUnits = listOf("lb"), purchaseFormats = listOf(saco))
        assertEquals(2.5, harina.costPerStockUnit(), 1e-9)
        // Guardada en onzas, la misma bolsa vale por onza.
        val enOnzas = harina.copy(allowedUnits = listOf("oz"))
        assertEquals(250.0 / 1600.0, enOnzas.costPerStockUnit(), 1e-6)
        assertEquals(0.0, Ingredient(allowedUnits = listOf("lb")).costPerStockUnit())
    }

    @Test
    fun soloLoRecibidoMueveElReporte() {
        val enCamino = transfer(pan)
        assertTrue(stockTransferEffects(listOf(enCamino)).isEmpty())
        val recibido = transfer(pan.copy(receivedQuantity = 90.0)).copy(status = StockTransferStatus.RECEIVED)
        val effects = stockTransferEffects(listOf(recibido))
        assertEquals(27.0, effects.first { it.branchId == "b" }.value, 1e-9)
        assertEquals(-27.0, effects.first { it.branchId == "a" }.value, 1e-9)
        assertEquals(0.0, effects.sumOf { it.value }, 1e-9)
    }

    @Test
    fun laEtiquetaUsaLoQueSeEscribio() {
        assertEquals("2 Saco 100 lb", pan.copy(displayQuantity = "2 Saco 100 lb").quantityLabel())
        assertEquals("100 u", pan.copy(unit = "u").quantityLabel())
    }
}
