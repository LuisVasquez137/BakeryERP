package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El suministro que no se cuenta (bolsas, bandejas).
 *
 * Dos cosas no pueden salir mal: que una línea suya toque inventario —tiene `ingredientId`
 * lleno, así que pasa por todos los filtros que miran ese campo— y que deje de generar su
 * gasto del mes, que es lo único que registra ese dinero.
 */
class SupplyWithoutStockTest {

    private val bolsas = Ingredient(
        id = "ing-bolsa", name = "Bolsa #5", usage = IngredientUsage.SUPPLY,
        allowedUnits = listOf("unidad"), tracksStock = false
    )
    private val cloro = Ingredient(
        id = "ing-cloro", name = "Cloro", usage = IngredientUsage.SUPPLY,
        allowedUnits = listOf("lt")
    )
    private val harina = Ingredient(
        id = "ing-harina", name = "Harina", usage = IngredientUsage.PRODUCTION,
        allowedUnits = listOf("lb")
    )

    @Test
    fun elTipoDeLineaSaleDelInsumo() {
        assertEquals(PurchaseLineKind.SUPPLY_NO_STOCK, ingredientPurchaseLineKind(bolsas))
        assertEquals(PurchaseLineKind.SUPPLY, ingredientPurchaseLineKind(cloro))
        assertEquals(PurchaseLineKind.INGREDIENT, ingredientPurchaseLineKind(harina))
    }

    @Test
    fun laMateriaPrimaSiempreLlevaStockAunqueAlguienApagueLaBandera() {
        // `tracksStock` es un campo del documento: un insumo de producción mal marcado NO
        // puede dejar de descontar, o la receta se quedaría sin de dónde restar.
        val malMarcada = harina.copy(tracksStock = false)
        assertEquals(PurchaseLineKind.INGREDIENT, ingredientPurchaseLineKind(malMarcada))
        assertTrue(purchaseLineTouchesStock(ingredientPurchaseLineKind(malMarcada)))
    }

    @Test
    fun noTocaInventarioPeroSiEsGasto() {
        assertFalse(purchaseLineTouchesStock(PurchaseLineKind.SUPPLY_NO_STOCK))
        assertTrue(purchaseLineIsExpense(PurchaseLineKind.SUPPLY_NO_STOCK))
        // El cloro sigue haciendo las dos cosas.
        assertTrue(purchaseLineTouchesStock(PurchaseLineKind.SUPPLY))
        assertTrue(purchaseLineIsExpense(PurchaseLineKind.SUPPLY))
    }

    @Test
    fun cuentaComoGastoYNoComoInventarioEnElReporteFinanciero() {
        val lines = listOf(
            PurchaseLine(lineKind = PurchaseLineKind.INGREDIENT, quantityReceived = 1.0, unitCost = 100.0),
            PurchaseLine(lineKind = PurchaseLineKind.SUPPLY_NO_STOCK, quantityReceived = 1.0, unitCost = 40.0)
        )
        val split = lines.amountSplit()
        assertEquals(100.0, split.inventory)
        assertEquals(40.0, split.expensed)
    }

    @Test
    fun sigueSiendoUnItemDelCatalogoParaBuscarYParaElHistorial() {
        val line = PurchaseLine(
            lineKind = PurchaseLineKind.SUPPLY_NO_STOCK,
            ingredientId = "ing-bolsa", ingredientName = "Bolsa #5",
            quantityReceived = 1000.0, unitCost = 0.2
        )
        // A diferencia del servicio de texto libre, tiene id de catálogo: se busca en la
        // lista de compras y se agrupa en la ficha del proveedor sin depender del nombre.
        assertEquals("ing-bolsa", line.itemId)
        assertEquals("Bolsa #5", line.itemName)
        assertEquals("I:ing-bolsa", supplierHistoryKey(line))
        assertEquals(200.0, line.lineTotal)
    }
}
