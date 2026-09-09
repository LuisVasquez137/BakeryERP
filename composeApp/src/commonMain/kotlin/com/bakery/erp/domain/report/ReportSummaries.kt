package com.bakery.erp.domain.report

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
    val byBranch: List<BranchExpenseStat> = emptyList()
)

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
