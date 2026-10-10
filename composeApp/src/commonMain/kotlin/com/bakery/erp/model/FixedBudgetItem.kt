package com.bakery.erp.model

data class FixedBudgetItem(
    val id: String = "",
    val orgId: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val name: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTROS,
    val amount: Double = 0.0,
    val notes: String? = null,
    val active: Boolean = true,
    /**
     * Ver [CostCenter]. Con [CostCenter.GENERAL] o [CostCenter.DELIVERY] el costo no es de
     * ninguna sucursal y [branchId] va vacío.
     */
    val costCenter: String = CostCenter.BRANCH,
    val updatedAt: Long = 0L
)

/**
 * Id del gasto que [FixedBudgetItem] genera para [period]. Es fijo para que "Anotar los costos
 * fijos del mes" se pueda tocar dos veces —o desde dos teléfonos— sin duplicar la renta.
 */
fun fixedExpenseId(itemId: String, period: String): String = "fijo_${itemId}_$period"

/**
 * El gasto del mes que sale de este costo fijo. Es un gasto normal y editable: la luz nunca
 * sale igual, así que se anota el presupuesto y se corrige al monto del recibo.
 *
 * No toca ninguna caja (`paidFrom` vacío): anotar que la renta es de octubre no dice de dónde
 * salió el dinero. Lo que se pague por factura de proveedor NO va en Costos fijos, o se
 * contaría dos veces.
 */
fun FixedBudgetItem.toMonthlyExpense(period: String, nowMillis: Long): Expense = Expense(
    id = fixedExpenseId(id, period),
    orgId = orgId,
    branchId = branchId,
    branchName = branchName,
    name = name,
    category = category,
    amount = amount,
    period = period,
    notes = listOfNotNull("Costo fijo del mes", notes?.takeIf { it.isNotBlank() }).joinToString(" · "),
    costCenter = costCenter,
    createdAt = nowMillis,
    updatedAt = nowMillis
)
