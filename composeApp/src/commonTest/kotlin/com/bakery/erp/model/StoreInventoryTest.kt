package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El modo de fallo que motivó esto: un producto de reventa sin movimientos no aparecía en
 * ninguna sucursal, y no había forma de ver si la factura había ingresado o no.
 */
class StoreInventoryTest {

    private val pan = Product(id = "pan", name = "Pirujo", isInHouseProduced = true)
    private val coca = Product(id = "coca", name = "Coca-Cola", isInHouseProduced = false, purchaseCost = 5.0)
    private val inactiva = Product(id = "old", name = "Pepsi", isInHouseProduced = false, active = false)
    private val huevo = Product(
        id = "hd", name = "Huevos por docena", isInHouseProduced = false,
        linkedIngredientId = "egg", linkedIngredientName = "Huevo", ingredientUnitsPerSale = 12.0
    )

    @Test
    fun reventaSinMovimientosSaleEnCero() {
        val rows = buildStoreInventoryRows(emptyList(), listOf(pan, coca, inactiva), emptyMap())
        assertEquals(listOf("coca"), rows.map { it.productId })
        assertEquals(0.0, rows.single().currentStock)
        assertFalse(rows.single().hasStockRecord)
    }

    @Test
    fun conStockSeUsaElDocumentoSinDuplicar() {
        val stock = FinishedProductStock(productId = "coca", productName = "Coca-Cola", currentStock = 48.0, avgUnitCost = 5.0)
        val rows = buildStoreInventoryRows(listOf(stock), listOf(coca), emptyMap())
        assertEquals(1, rows.size)
        assertEquals(48.0, rows.single().currentStock)
        assertTrue(rows.single().isResale)
    }

    @Test
    fun elPanPropioSigueSaliendoSoloConDocumento() {
        val stock = FinishedProductStock(productId = "pan", productName = "Pirujo", currentStock = 100.0)
        val rows = buildStoreInventoryRows(listOf(stock), listOf(pan), emptyMap())
        assertFalse(rows.single().isResale)
    }

    @Test
    fun productoDeInsumoMuestraElStockDelInsumoEnPiezas() {
        val viejoDoc = FinishedProductStock(productId = "hd", currentStock = 7.0)
        val rows = buildStoreInventoryRows(listOf(viejoDoc), listOf(huevo), mapOf("egg" to 360.0))
        assertEquals(1, rows.size)
        assertEquals(30.0, rows.single().currentStock)
        assertTrue(rows.single().isIngredientBacked)
    }
}
