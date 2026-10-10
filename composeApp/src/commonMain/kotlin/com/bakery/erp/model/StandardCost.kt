package com.bakery.erp.model

/**
 * Costo estándar congelado de un mes (ver `domain/StandardCost.kt`).
 *
 * Firestore: `standardCostPeriods/{period}` (este encabezado) y `standardCosts/{period}_{productId}`
 * (uno por producto, plano como `productPrices`: el despacho lee UN documento por producto sin
 * consultar nada).
 */
data class StandardCostPeriod(
    /** Mes en que se usa ("YYYY-MM"). */
    val period: String = "",
    /** Mes de donde salieron los gastos y los kilos (el anterior). */
    val sourcePeriod: String = "",
    val plants: List<FrozenPlantRate> = emptyList(),
    val productCount: Int = 0,
    /** Productos propios que quedaron sin costo (se despachan con la lista). */
    val missingCount: Int = 0,
    val frozenAt: Long = 0L,
    val frozenByName: String = ""
)

data class FrozenPlantRate(
    val branchId: String = "",
    val branchName: String = "",
    val expenses: Double = 0.0,
    val payroll: Double = 0.0,
    val doughGrams: Double = 0.0,
    /** 0 = la planta no tuvo masa suficiente y sus productos quedaron sin costo. */
    val ratePerGram: Double = 0.0
)

data class ProductStandardCost(
    val id: String = "",
    val period: String = "",
    val productId: String = "",
    val productName: String = "",
    val plantBranchId: String = "",
    val plantName: String = "",
    val doughGrams: Double = 0.0,
    val materialCost: Double = 0.0,
    val overheadCost: Double = 0.0,
    val unitCost: Double = 0.0,
    val frozenAt: Long = 0L
)

fun standardCostDocId(period: String, productId: String): String = "${period}_$productId"
