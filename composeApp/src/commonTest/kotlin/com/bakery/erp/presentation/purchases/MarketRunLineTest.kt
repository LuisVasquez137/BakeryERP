package com.bakery.erp.presentation.purchases

import com.bakery.erp.model.Branch
import com.bakery.erp.model.ExpenseCategory
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.IngredientUsage
import com.bakery.erp.model.Purchase
import com.bakery.erp.model.PurchaseKind
import com.bakery.erp.model.PurchaseLineKind
import com.bakery.erp.model.branchesTouched
import com.bakery.erp.model.effectiveBranchId
import com.bakery.erp.model.lineTotal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La vuelta del mercado: una salida, muchas cosas, varias sucursales.
 *
 * Lo que se prueba es la traducción de "papas, Q25, para el restaurante" a una línea con su
 * efecto correcto. Los dos modos de fallo reales son que el monto tecleado no sobreviva la
 * conversión —quien anota escribe el TOTAL, no el precio unitario— y que una fila sin insumo
 * termine intentando mover inventario que nadie declaró.
 */
class MarketRunLineTest {

    private val restaurante = Branch(id = "b2", name = "Restaurante")

    private fun ingrediente(usage: IngredientUsage = IngredientUsage.PRODUCTION) = Ingredient(
        id = "i1",
        name = "Papas",
        allowedUnits = listOf("lb"),
        usage = usage
    )

    private fun fila(
        description: String = "Papas del mercado",
        ingredient: Ingredient? = null,
        quantity: Double = 1.0,
        amount: Double = 25.0,
        branch: Branch? = null
    ) = MarketRunDraftLine(
        id = "l1",
        description = description,
        ingredient = ingredient,
        branch = branch,
        placeName = "mercado",
        quantity = quantity,
        amount = amount
    )

    @Test
    fun `una fila sin insumo es gasto y no toca inventario`() {
        val line = fila(description = "Mesita para el teléfono").toPurchaseLine(
            ExpenseCategory.OTROS.name
        )
        assertEquals(PurchaseLineKind.SERVICE, line.lineKind)
        assertEquals("Mesita para el teléfono", line.conceptName)
        assertTrue(line.ingredientId.isEmpty())
    }

    @Test
    fun `una fila pegada a materia prima entra al inventario`() {
        val line = fila(ingredient = ingrediente()).toPurchaseLine(ExpenseCategory.OTROS.name)
        assertEquals(PurchaseLineKind.INGREDIENT, line.lineKind)
        assertEquals("i1", line.ingredientId)
    }

    @Test
    fun `una fila pegada a un suministro entra al inventario y ademas se gasta`() {
        val line = fila(ingredient = ingrediente(IngredientUsage.SUPPLY))
            .toPurchaseLine(ExpenseCategory.OTROS.name)
        // SUPPLY es el único tipo que hace las dos cosas — ver PurchaseLineKind.
        assertEquals(PurchaseLineKind.SUPPLY, line.lineKind)
    }

    @Test
    fun `el monto tecleado es el total de la fila, no el precio unitario`() {
        // Quien vuelve del mercado dice "las papas me costaron Q25", no "Q5 la libra".
        val line = fila(quantity = 5.0, amount = 25.0).toPurchaseLine(ExpenseCategory.OTROS.name)
        assertEquals(25.0, line.lineTotal)
        assertEquals(5.0, line.unitCost)
    }

    @Test
    fun `una fila sin cantidad sigue valiendo lo que costo`() {
        // El campo de cantidad es opcional en el diálogo: nadie pesa lo que trae del mercado.
        val line = fila(quantity = 0.0, amount = 40.0).toPurchaseLine(ExpenseCategory.OTROS.name)
        assertEquals(40.0, line.lineTotal)
        assertEquals(1.0, line.quantityReceived)
    }

    @Test
    fun `la sucursal de la linea manda sobre la del encabezado`() {
        val purchase = Purchase(
            id = "p1", kind = PurchaseKind.MARKET_RUN,
            branchId = "b1", branchName = "Panadería"
        )
        val propia = fila(branch = restaurante).toPurchaseLine(ExpenseCategory.OTROS.name)
        val heredada = fila(branch = null).toPurchaseLine(ExpenseCategory.OTROS.name)

        assertEquals("b2", propia.effectiveBranchId(purchase))
        assertEquals("b1", heredada.effectiveBranchId(purchase))
    }

    @Test
    fun `una vuelta a dos sucursales reporta las dos`() {
        // Es lo que decide a cuántas sucursales hay que reconciliarles los lotes: con una
        // sola, la fruta de la otra quedaría sin registrar en el libro de caducidades.
        val purchase = Purchase(
            id = "p1", kind = PurchaseKind.MARKET_RUN,
            branchId = "b1", branchName = "Panadería"
        )
        val lines = listOf(
            fila(branch = restaurante).toPurchaseLine(ExpenseCategory.OTROS.name),
            fila(branch = null).toPurchaseLine(ExpenseCategory.OTROS.name)
        )
        assertEquals(
            setOf("b1", "b2"),
            lines.branchesTouched(purchase).map { it.first }.toSet()
        )
    }

    @Test
    fun `el gasto de la linea cae en la sucursal de la linea`() {
        // Los trapos del restaurante no son gasto de la panadería: el reporte financiero
        // desglosa por sucursal y mezclarlos haría inútil ese desglose.
        val purchase = Purchase(
            id = "p1", kind = PurchaseKind.MARKET_RUN,
            branchId = "b1", branchName = "Panadería"
        )
        val line = fila(description = "Trapos", branch = restaurante)
            .toPurchaseLine(ExpenseCategory.OTROS.name)
        val expense = expensesForPurchase("c1", purchase, listOf(line)).single()
        assertEquals("b2", expense.branchId)
        assertEquals("Restaurante", expense.branchName)
    }
}
