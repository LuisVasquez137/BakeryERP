package com.bakery.erp.domain.report

data class HomeAlert(
    val id: String,
    val severity: AlertSeverity,
    val target: AlertTarget,
    val title: String,
    val message: String,
    val actionLabel: String,
    val createdAt: Long
)

enum class AlertSeverity { INFO, WARNING, CRITICAL }

enum class AlertTarget {
    LOW_STOCK_INGREDIENTS,
    UPCOMING_MAINTENANCE,
    IN_TRANSIT_DISPATCH,
    STALE_PRODUCTION_LOT,
    LATE_PRODUCTION_ORDER,
    OVERDUE_CASH_SESSION,
    DISPATCH_PRICE_MISSING,
    OVERDUE_PURCHASE,
    EXPIRING_INGREDIENT
}
