package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pedido de la sucursal (pan molde, pan tostado): se pide para un día y un viaje, quien lo tiene
 * lo envía con lo que de verdad manda y viaja en el camión del reparto.
 */
class StockTransferRequestTest {

    private val day = 1_000_000L
    private val molde = StockTransferLine(itemId = "molde", itemName = "Pan molde", requestedQuantity = 10.0)
    private val tostado = StockTransferLine(itemId = "tostado", itemName = "Pan tostado", requestedQuantity = 6.0)

    private fun request(origin: String = "bom", vararg lines: StockTransferLine = arrayOf(molde)) = StockTransfer(
        id = "t1",
        originBranchId = origin, originBranchName = origin,
        destinationBranchId = "dem", destinationBranchName = "Democracia",
        status = StockTransferStatus.REQUESTED,
        lines = lines.toList(),
        trip = DISPATCH_TRIP_FIRST, forDate = day, requestedAt = day + 10
    )

    private val bombero = Branch(id = "bom", name = "Bombero", deliveryRouteOrder = listOf("cal", "dem", "pac"))
    private val calvario = Branch(id = "cal", name = "Calvario")
    private val democracia = Branch(id = "dem", name = "Democracia")
    private val branches = listOf(bombero, calvario, democracia)

    @Test
    fun unPedidoNecesitaDiaYViaje() {
        assertNull(stockTransferRequestError(request()))
        assertNotNull(stockTransferRequestError(request().copy(trip = 0)))
        assertNotNull(stockTransferRequestError(request().copy(forDate = 0L)))
        assertNotNull(stockTransferRequestError(request().copy(originBranchId = "dem")))
        assertNotNull(stockTransferRequestError(request(lines = arrayOf(molde.copy(requestedQuantity = 0.0)))))
    }

    @Test
    fun alEnviarSeQuedanLasLineasPedidasAunqueVayanEnCero() {
        val sending = request("cal", molde, tostado).sendingRequest(listOf(8.0, 0.0))
        assertEquals(listOf(8.0, 0.0), sending.lines.map { it.quantity })
        assertEquals(listOf(10.0, 6.0), sending.lines.map { it.requestedQuantity })
        assertNull(stockTransferSendError(sending))
        val sent = sending.copy(status = StockTransferStatus.IN_TRANSIT)
        assertEquals(listOf("molde", "tostado"), sent.shortOfRequest.map { it.itemId })
    }

    @Test
    fun noSeEnviaUnPedidoTodoEnCero() {
        assertNotNull(stockTransferSendError(request().sendingRequest(listOf(0.0))))
    }

    @Test
    fun loQueSaleDeLaPlantaVaEnSuCamion() {
        assertEquals("bom", carrierPlantIdOf(request("bom"), branches))
        assertFalse(request("bom").isPickupFor("bom"))
    }

    @Test
    fun loDeCalvarioLoRecogeElCamionQueLoTieneEnSuRuta() {
        val t = request("cal")
        assertEquals("bom", carrierPlantIdOf(t, branches))
        assertTrue(t.isPickupFor("bom"))
    }

    @Test
    fun soloEntranLosDelDiaYViaje() {
        val all = listOf(
            request("cal"),
            request("bom").copy(id = "t2", trip = DISPATCH_TRIP_SECOND),
            request("bom").copy(id = "t3", forDate = day + 86_400_000L),
            request("bom").copy(id = "t4", status = StockTransferStatus.CANCELLED),
            // Traslado directo, sin pedido: no va en el reparto.
            request("bom").copy(id = "t5", trip = 0, forDate = 0L, status = StockTransferStatus.IN_TRANSIT)
        )
        val first = transfersForTrip(all, branches, "bom", DISPATCH_TRIP_FIRST, day, day + 86_400_000L)
        assertEquals(listOf("t1"), first.map { it.id })
    }
}
