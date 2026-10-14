package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El pastel que no se vendió entero se corta en la tienda: sale del entero, entra a la
 * porción, y el costo pasa del uno a la otra sin cambiar el valor del inventario.
 */
class PortioningTest {

    private val porcion = Product(
        id = "psn", name = "Porción de selva negra",
        portionOfProductId = "sn", portionOfProductName = "Selva negra", portionsPerWhole = 8
    )

    private fun request(wholes: Int = 1, perWhole: Int = 8, portion: Product = porcion) = PortioningRequest(
        branchId = "b1", branchName = "Centro", portion = portion,
        wholes = wholes, portionsPerWhole = perWhole, userId = "u1", userName = "Ana"
    )

    private fun stock(qty: Double, cost: Double = 60.0) = FinishedProductStock(
        id = "b1_sn", branchId = "b1", productId = "sn", productName = "Selva negra",
        currentStock = qty, avgUnitCost = cost
    )

    @Test
    fun salidaDelEnteroYEntradaDePorcionesConElCostoRepartido() {
        val moves = portioningMovements(request(wholes = 2, perWhole = 8), stock(3.0), "ref1")
        assertEquals(2, moves.size)

        val out = moves[0]
        assertEquals("sn", out.productId)
        assertEquals(-2.0, out.quantity)
        assertEquals(60.0, out.unitPriceAtMovement)

        val inn = moves[1]
        assertEquals("psn", inn.productId)
        assertEquals(16.0, inn.quantity)
        assertEquals(7.5, inn.unitPriceAtMovement)

        // Mismo valor antes y después de cortar.
        assertEquals(-out.quantity * out.unitPriceAtMovement, inn.quantity * inn.unitPriceAtMovement)
        assertTrue(moves.all {
            it.type == FinishedMovementType.PORCIONADO && it.referenceId == "ref1" &&
                it.referenceType == FinishedMovementReference.PORTIONING && it.branchId == "b1"
        })
    }

    @Test
    fun seRespetanLasPorcionesQueDiceLaCajeraAunqueElProductoDigaOtra() {
        val moves = portioningMovements(request(wholes = 1, perWhole = 10), stock(1.0), "r")
        assertEquals(10.0, moves[1].quantity)
        assertEquals(6.0, moves[1].unitPriceAtMovement)
    }

    @Test
    fun noDejaCortarMasEnterosDeLosQueHay() {
        assertNotNull(portioningError(request(wholes = 2), stock(1.0)))
        assertNotNull(portioningError(request(wholes = 1), null))
        assertNull(portioningError(request(wholes = 1), stock(1.0)))
    }

    @Test
    fun cantidadesInvalidasYProductoQueNoEsPorcion() {
        assertNotNull(portioningError(request(wholes = 0), stock(5.0)))
        assertNotNull(portioningError(request(perWhole = 0), stock(5.0)))
        assertNotNull(portioningError(request(portion = Product(id = "x", name = "Pirujo")), stock(5.0)))
    }

    @Test
    fun alCobrarSeOfreceCortarSoloSiFaltanPorcionesYHayEnteros() {
        assertTrue(shouldOfferPortioning(porcion, quantityInCart = 1.0, portionStock = 0.0, wholeStock = 2.0))
        // Todavía alcanzan las porciones cortadas.
        assertFalse(shouldOfferPortioning(porcion, 2.0, portionStock = 3.0, wholeStock = 2.0))
        // No hay entero que cortar: el aviso de sobreventa de siempre se encarga.
        assertFalse(shouldOfferPortioning(porcion, 1.0, portionStock = 0.0, wholeStock = 0.0))
        // Un producto que no es porción nunca pregunta.
        assertFalse(shouldOfferPortioning(Product(id = "x"), 1.0, 0.0, 5.0))
    }
}
