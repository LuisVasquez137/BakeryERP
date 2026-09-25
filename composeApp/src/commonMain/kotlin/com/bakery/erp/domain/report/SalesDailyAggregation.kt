package com.bakery.erp.domain.report

import com.bakery.erp.model.SalesDaily

/**
 * Arma el [SalesSummary] que consumen el Home y los reportes a partir de los resúmenes
 * diarios, sin tocar una sola venta individual.
 *
 * Lógica pura: recibe los documentos ya filtrados por rango y sucursal, y solo pliega.
 * Lo que antes salía de recorrer decenas de miles de ventas y sus líneas, aquí sale de
 * plegar unas decenas de documentos.
 */
fun salesSummaryFromDaily(dailies: List<SalesDaily>): SalesSummary {
    if (dailies.isEmpty()) return SalesSummary()

    val totalRevenue = dailies.sumOf { it.revenue }
    val transactionCount = dailies.sumOf { it.transactionCount }

    val byPaymentMethod = mutableMapOf<String, MoneyAndCount>()
    dailies.forEach { d ->
        d.paymentTotal.forEach { (method, amount) ->
            val prev = byPaymentMethod[method] ?: MoneyAndCount()
            byPaymentMethod[method] = MoneyAndCount(
                total = prev.total + amount,
                count = prev.count + (d.paymentCount[method] ?: 0)
            )
        }
    }

    val byChannel = mutableMapOf<String, MoneyAndCount>()
    dailies.forEach { d ->
        d.channelRevenue.forEach { (channel, amount) ->
            val prev = byChannel[channel] ?: MoneyAndCount()
            byChannel[channel] = MoneyAndCount(
                total = prev.total + amount,
                count = prev.count + (d.channelCount[channel] ?: 0)
            )
        }
    }

    // Cajero: el nombre se toma del último día que lo trae, por si cambió.
    val cashierTotals = mutableMapOf<String, Double>()
    val cashierCounts = mutableMapOf<String, Int>()
    val cashierNames = mutableMapOf<String, String>()
    dailies.forEach { d ->
        d.cashierTotal.forEach { (id, total) -> cashierTotals[id] = (cashierTotals[id] ?: 0.0) + total }
        d.cashierCount.forEach { (id, count) -> cashierCounts[id] = (cashierCounts[id] ?: 0) + count }
        cashierNames.putAll(d.cashierName)
    }
    val byCashier = cashierTotals.map { (id, total) ->
        val count = cashierCounts[id] ?: 0
        CashierSalesStat(
            cashierId = id,
            cashierName = cashierNames[id] ?: "",
            transactionCount = count,
            total = total,
            avgTicket = if (count > 0) total / count else 0.0
        )
    }.sortedByDescending { it.total }

    val byBranch = dailies.groupBy { it.branchId }
        .map { (branchId, days) ->
            BranchSalesStat(
                branchId = branchId,
                branchName = days.last().branchName,
                transactionCount = days.sumOf { it.transactionCount },
                total = days.sumOf { it.revenue }
            )
        }
        .sortedByDescending { it.total }

    val productQty = mutableMapOf<String, Double>()
    val productRevenue = mutableMapOf<String, Double>()
    val productNames = mutableMapOf<String, String>()
    dailies.forEach { d ->
        d.productQty.forEach { (id, qty) -> productQty[id] = (productQty[id] ?: 0.0) + qty }
        d.productRevenue.forEach { (id, rev) -> productRevenue[id] = (productRevenue[id] ?: 0.0) + rev }
        productNames.putAll(d.productName)
    }
    val topProducts = productRevenue.map { (id, revenue) ->
        ProductSalesStat(
            productId = id,
            productName = productNames[id] ?: "",
            quantity = productQty[id] ?: 0.0,
            revenue = revenue
        )
    }.sortedByDescending { it.revenue }.take(20)

    val hourHistogram = mutableMapOf<Int, Double>()
    dailies.forEach { d ->
        d.hourTotal.forEach { (hour, amount) ->
            val h = hour.toIntOrNull() ?: return@forEach
            hourHistogram[h] = (hourHistogram[h] ?: 0.0) + amount
        }
    }

    // Un punto por día del calendario, sumando las sucursales que vendieron ese día.
    val byDay = dailies.groupBy { it.businessDate }
        .map { (date, days) ->
            DailySalesPoint(
                dateMillis = days.first().dateMillis,
                dateLabel = date,
                revenue = days.sumOf { it.revenue },
                transactionCount = days.sumOf { it.transactionCount }
            )
        }
        .sortedBy { it.dateMillis }

    return SalesSummary(
        totalRevenue = totalRevenue,
        transactionCount = transactionCount,
        unitsSold = dailies.sumOf { it.unitsSold },
        avgTicket = if (transactionCount > 0) totalRevenue / transactionCount else 0.0,
        byPaymentMethod = byPaymentMethod,
        byChannel = byChannel,
        byCashier = byCashier,
        byBranch = byBranch,
        topProducts = topProducts,
        unitsByProduct = productQty.toMap(),
        hourHistogram = hourHistogram,
        byDay = byDay
    )
}
