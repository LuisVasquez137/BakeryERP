package com.bakery.erp.domain.report

import com.bakery.erp.model.ProductionWasteSummary
import com.bakery.erp.model.ExpenseCategory

data class SalesSummary(
    val totalRevenue: Double = 0.0,
    val transactionCount: Int = 0,
    val unitsSold: Double = 0.0,
    val avgTicket: Double = 0.0,
    val byPaymentMethod: Map<String, MoneyAndCount> = emptyMap(),
    /** Canal (`SalesChannel`) -> monto y tickets. Separa pan de restaurante. */
    val byChannel: Map<String, MoneyAndCount> = emptyMap(),
    val byCashier: List<CashierSalesStat> = emptyList(),
    val byBranch: List<BranchSalesStat> = emptyList(),
    val topProducts: List<ProductSalesStat> = emptyList(),
    /**
     * Unidades vendidas de CADA producto (`productId` → unidades). [topProducts] se corta
     * en 20 para la gráfica; el reporte "Pedido vs. recibido" necesita el resto.
     */
    val unitsByProduct: Map<String, Double> = emptyMap(),
    val hourHistogram: Map<Int, Double> = emptyMap(),
    val byDay: List<DailySalesPoint> = emptyList()
)

data class MoneyAndCount(val total: Double = 0.0, val count: Int = 0)

data class CashierSalesStat(
    val cashierId: String,
    val cashierName: String,
    val transactionCount: Int,
    val total: Double,
    val avgTicket: Double
)

data class BranchSalesStat(
    val branchId: String,
    val branchName: String,
    val transactionCount: Int,
    val total: Double
)

data class ProductSalesStat(
    val productId: String,
    val productName: String,
    val quantity: Double,
    val revenue: Double
)

data class DailySalesPoint(
    val dateMillis: Long,
    val dateLabel: String,
    val revenue: Double,
    val transactionCount: Int
)

data class ProductionSummary(
    val lotsCount: Int = 0,
    val completedLotsCount: Int = 0,
    val plannedTotal: Double = 0.0,
    val producedTotal: Double = 0.0,
    val efficiencyPct: Double = 0.0,
    val lateOrderCount: Int = 0,
    /**
     * Unidades que se amasaron y se perdieron en el horno (ver
     * `ProductionLotLine.wasteQuantity`). Dato que no existía: había merma en
     * tránsito (despacho) y merma de tienda, pero lo que se quema nunca se
     * registraba, así que su costo aparecía después como un faltante de insumos
     * sin explicación.
     */
    val wasteTotal: Double = 0.0,
    /** Unidades horneadas de más por encargo, fuera de lo que pidieron las sucursales. */
    val extraTotal: Double = 0.0,
    val wasteByProduct: List<ProductionWasteSummary> = emptyList(),
    val byShift: List<ShiftProductionStat> = emptyList(),
    val byProduct: List<ProductProductionStat> = emptyList(),
    val byDay: List<DailyProductionPoint> = emptyList()
)

data class ShiftProductionStat(
    val shiftId: String,
    val shiftName: String,
    val lotsCount: Int,
    val planned: Double,
    val produced: Double
)

data class ProductProductionStat(
    val productId: String,
    val productName: String,
    val planned: Double,
    val produced: Double,
    val diffPct: Double
)

data class DailyProductionPoint(
    val dateMillis: Long,
    val dateLabel: String,
    val planned: Double,
    val produced: Double
)

data class DispatchSummary(
    val notesCount: Int = 0,
    val deliveredCount: Int = 0,
    val withDiscrepancyCount: Int = 0,
    val cancelledCount: Int = 0,
    val inTransitLossUnits: Double = 0.0,
    val inTransitLossValue: Double = 0.0,
    val byDestinationBranch: List<DispatchBranchStat> = emptyList(),
    val byDiscrepancyType: Map<String, DiscrepancyStat> = emptyMap()
)

data class DispatchBranchStat(
    val branchId: String,
    val branchName: String,
    val notesCount: Int,
    val discrepancyCount: Int,
    val lossUnits: Double
)

data class DiscrepancyStat(val occurrences: Int, val units: Double)

data class ExpenseSummary(
    val total: Double = 0.0,
    val recordsCount: Int = 0,
    val byCategory: Map<ExpenseCategory, Double> = emptyMap(),
    val byBranch: List<BranchExpenseStat> = emptyList(),
    /**
     * Parte de [total] que generó una compra (`Expense.sourcePurchaseId` puesto).
     *
     * El flujo de caja resta las compras pagadas por su lado, así que sin este corte los
     * suministros y los servicios se restarian dos veces: una dentro de la compra y otra
     * como gasto del mes.
     */
    val fromPurchases: Double = 0.0
) {
    /** Gastos capturados por fuera de una compra. [total] menos [fromPurchases]. */
    val standalone: Double get() = (total - fromPurchases).coerceAtLeast(0.0)
}

data class BranchExpenseStat(
    val branchId: String,
    val branchName: String,
    val total: Double
)

data class PayrollSummary(
    val recordsCount: Int = 0,
    val totalGross: Double = 0.0,
    val totalNet: Double = 0.0,
    val totalEmployerCost: Double = 0.0
)

data class StockMovementSummary(
    val receivedUnits: Double = 0.0,
    val soldUnits: Double = 0.0,
    val soldRevenueAtCost: Double = 0.0,
    val mermaUnits: Double = 0.0,
    val mermaValue: Double = 0.0,
    val adjustmentUnits: Double = 0.0,
    val byProduct: List<ProductMovementStat> = emptyList(),
    val byMermaReason: Map<String, MermaReasonStat> = emptyMap()
)

data class ProductMovementStat(
    val productId: String,
    val productName: String,
    val receivedUnits: Double,
    val soldUnits: Double,
    val mermaUnits: Double
)

data class MermaReasonStat(val units: Double, val valueImpact: Double)

data class MermaSummary(
    val totalUnits: Double = 0.0,
    val totalValue: Double = 0.0,
    val byReason: Map<String, MermaReasonStat> = emptyMap()
)

data class CustomerOrdersSummary(
    val ordersCount: Int = 0,
    val byType: Map<String, CustomerOrderTypeStat> = emptyMap(),
    /** Total esperado (suma de [CustomerOrder.totalAmount] en pedidos no cancelados). */
    val totalExpectedRevenue: Double = 0.0,
    /** Suma de anticipos / depósitos ya cobrados. */
    val totalAdvancesCollected: Double = 0.0,
    /** Saldo pendiente: pedidos READY / IN_PRODUCTION / CONFIRMED con balance > 0. */
    val totalPendingBalance: Double = 0.0,
    val topCustomers: List<CustomerStat> = emptyList()
)

data class CustomerOrderTypeStat(
    val count: Int,
    val totalAmount: Double,
    val advancesCollected: Double,
    val pendingBalance: Double
)

data class CustomerStat(
    val customerId: String,
    val customerName: String,
    val ordersCount: Int,
    val totalAmount: Double
)

/**
 * Las compras del mes, vistas como salida de dinero.
 *
 * El reporte financiero no las conocía: los egresos eran gastos + planilla, así que la
 * harina y el azúcar —la mayor salida de efectivo de una panadería— no aparecían en ningún
 * lado. Contablemente es correcto (son inventario hasta que se venden), pero deja sin
 * respuesta la pregunta de "¿en qué se me fue el mes?".
 *
 * Por eso viven en su propia sección y NO dentro de la utilidad operativa: mezclarlas ahí
 * rompería la contabilidad de inventario y contaría doble cuando llegue el costo por
 * producto.
 */
data class PurchaseSummary(
    val purchasesCount: Int = 0,
    val invoiceCount: Int = 0,
    val quickCount: Int = 0,
    /** Total comprado en el período, sin importar si ya se pagó. */
    val total: Double = 0.0,
    /** Lo que entró a inventario y lo que ya se gastó. Ver `PurchaseAmountSplit`. */
    val inventoryTotal: Double = 0.0,
    val expensedTotal: Double = 0.0,
    val unclassifiedTotal: Double = 0.0,
    /**
     * Lo ya abonado de las compras FECHADAS en el período.
     *
     * No es exactamente el efectivo que salió este mes: un abono hecho hoy a una factura de
     * agosto cuenta en agosto, no acá. Se acepta a sabiendas porque lo contrario obliga a
     * abrir la subcolección de abonos de cada compra —una lectura por factura— para mover un
     * número que en una panadería que paga casi todo de contado apenas cambia. La pantalla
     * lo dice en vez de dar a entender que es un flujo de caja exacto.
     */
    val paidTotal: Double = 0.0,
    /** Saldo que queda debiendo de las compras del período. */
    val pendingTotal: Double = 0.0
)
