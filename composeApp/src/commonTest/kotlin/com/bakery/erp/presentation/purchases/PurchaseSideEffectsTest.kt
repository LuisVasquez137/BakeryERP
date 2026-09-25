package com.bakery.erp.presentation.purchases

import com.bakery.erp.model.AssetEventType
import com.bakery.erp.model.ExpenseCategory
import com.bakery.erp.model.ExpensePaidFrom
import com.bakery.erp.model.Purchase
import com.bakery.erp.model.PurchaseLine
import com.bakery.erp.model.PurchaseLineKind
import com.bakery.erp.model.User
import com.bakery.erp.model.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lo que una factura deja fuera del inventario.
 *
 * Se prueba el REPARTO por tipo de línea, no los montos: el modo de fallo real es que un
 * saco de harina termine contado como gasto del mes (duplicando el costo cuando llegue el
 * costeo por producto) o que un mantenimiento no llegue nunca al estado de resultados.
 */
class PurchaseSideEffectsTest {

    private val user = User(
        id = "u1",
        orgId = "c1",
        email = "a@b.c",
        displayName = "Ana",
        role = UserRole.OWNER,
        branchIds = emptyList(),
        isActive = true
    )

    private fun purchase(dateMillis: Long = 1_757_000_000_000L) = Purchase(
        id = "p1",
        branchId = "b1",
        branchName = "Central",
        supplierId = "s1",
        supplierName = "Abarrotería El Sol",
        invoiceNumber = "A-123",
        date = dateMillis
    )

    private fun ingredientLine() = PurchaseLine(
        id = "l1",
        lineKind = PurchaseLineKind.INGREDIENT,
        ingredientId = "i1",
        ingredientName = "Harina",
        quantityReceived = 100.0,
        unitCost = 5.0
    )

    private fun resaleLine() = PurchaseLine(
        id = "l2",
        lineKind = PurchaseLineKind.RESALE_PRODUCT,
        productId = "pr1",
        productName = "Tostadas",
        quantityReceived = 10.0,
        unitCost = 8.0
    )

    private fun supplyLine() = PurchaseLine(
        id = "l3",
        lineKind = PurchaseLineKind.SUPPLY,
        ingredientId = "i2",
        ingredientName = "Cloro",
        quantityReceived = 2.0,
        unitCost = 25.0,
        expenseCategory = ExpenseCategory.SERVICIOS.name
    )

    private fun serviceLine(assetId: String = "") = PurchaseLine(
        id = "l4",
        lineKind = PurchaseLineKind.SERVICE,
        conceptName = "Mantenimiento del horno",
        quantityReceived = 1.0,
        unitCost = 450.0,
        expenseCategory = ExpenseCategory.MANTENIMIENTO.name,
        assetId = assetId,
        assetName = if (assetId.isEmpty()) "" else "Horno rotativo",
        assetEventType = if (assetId.isEmpty()) "" else AssetEventType.MAINTENANCE.name
    )

    @Test
    fun `materia prima y reventa no generan gasto`() {
        val expenses = expensesForPurchase(
            "c1", purchase(), listOf(ingredientLine(), resaleLine())
        )
        assertTrue(expenses.isEmpty(), "El inventario no es gasto al comprarse: $expenses")
    }

    @Test
    fun `suministro y servicio generan un gasto cada uno`() {
        val expenses = expensesForPurchase(
            "c1", purchase(), listOf(ingredientLine(), supplyLine(), serviceLine())
        )
        assertEquals(2, expenses.size)
        assertEquals(setOf("Cloro", "Mantenimiento del horno"), expenses.map { it.name }.toSet())
    }

    @Test
    fun `el gasto conserva la categoria declarada en la linea`() {
        val expenses = expensesForPurchase("c1", purchase(), listOf(serviceLine()))
        assertEquals(ExpenseCategory.MANTENIMIENTO, expenses.single().category)
    }

    @Test
    fun `sin categoria declarada el gasto cae en OTROS y no se adivina`() {
        val sinCategoria = supplyLine().copy(expenseCategory = "")
        val expenses = expensesForPurchase("c1", purchase(), listOf(sinCategoria))
        assertEquals(ExpenseCategory.OTROS, expenses.single().category)
    }

    @Test
    fun `el gasto hereda de que bolsa salio la plata`() {
        val deCaja = purchase().copy(
            paidFrom = ExpensePaidFrom.CASH_DRAWER,
            cashSessionId = "sess1",
            cashRegisterName = "Caja 2"
        )
        val gasto = expensesForPurchase("c1", deCaja, listOf(supplyLine())).single()
        // Sin esto el gasto del mes se vería como si lo hubiera puesto el dueño, y la gaveta
        // aparecería al cuadrar con un faltante que nada explica.
        assertEquals(ExpensePaidFrom.CASH_DRAWER, gasto.paidFrom)
        assertEquals("sess1", gasto.cashSessionId)
        assertEquals("Caja 2", gasto.cashRegisterName)
    }

    @Test
    fun `el comprobante de la compra queda pegado al gasto`() {
        val conFoto = purchase().copy(invoicePhotoUrl = "https://x/recibo.jpg")
        assertEquals(
            "https://x/recibo.jpg",
            expensesForPurchase("c1", conFoto, listOf(supplyLine())).single().photoUrl
        )
    }

    @Test
    fun `una compra sin atribucion declarada no inventa que fue caja general`() {
        val gasto = expensesForPurchase("c1", purchase(), listOf(supplyLine())).single()
        assertEquals("", gasto.paidFrom)
        assertEquals("", gasto.cashSessionId)
    }

    @Test
    fun `una linea sin monto no genera gasto`() {
        val vacia = serviceLine().copy(unitCost = 0.0, quantityReceived = 0.0)
        assertTrue(expensesForPurchase("c1", purchase(), listOf(vacia)).isEmpty())
    }

    @Test
    fun `el gasto queda ligado a la factura para poder revertirlo`() {
        val expense = expensesForPurchase("c1", purchase(), listOf(serviceLine())).single()
        assertEquals("p1", expense.sourcePurchaseId)
        assertEquals("b1", expense.branchId)
    }

    @Test
    fun `solo el servicio con activo declarado escribe en el historial`() {
        val entries = assetEntriesForPurchase(
            purchase(),
            listOf(ingredientLine(), supplyLine(), serviceLine(), serviceLine(assetId = "a1")),
            user
        )
        assertEquals(1, entries.size)
        assertEquals("a1", entries.single().assetId)
        assertEquals(AssetEventType.MAINTENANCE, entries.single().entry.type)
        assertEquals(450.0, entries.single().entry.cost)
        // Sin esto no habría cómo rehacer el historial si la factura se corrige.
        assertEquals("p1", entries.single().entry.sourcePurchaseId)
    }

    @Test
    fun `el periodo del gasto sale de la fecha de la factura, no de hoy`() {
        // Las facturas se capturan en tandas: una de agosto no puede caer en el mes actual.
        val expenses = expensesForPurchase("c1", purchase(1_756_000_000_000L), listOf(serviceLine()))
        assertEquals(purchasePeriodOf(1_756_000_000_000L), expenses.single().period)
        assertEquals(7, expenses.single().period.length, "Formato YYYY-MM")
    }
}
