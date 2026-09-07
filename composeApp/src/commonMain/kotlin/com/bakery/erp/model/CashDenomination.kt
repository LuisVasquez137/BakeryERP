package com.bakery.erp.model

/**
 * Denominaciones de la moneda local (Quetzal guatemalteco).
 * Lista fija — no se prevé cambio frecuente. Si en el futuro se necesita
 * multimoneda u otras denominaciones, mover a Firestore y exponer por país.
 */
data class CashDenomination(
    val code: String,
    val value: Double,
    val label: String,
    val type: CashDenominationType
)

enum class CashDenominationType { BILL, COIN }

object CashDenominations {
    val Q_BILL_200 = CashDenomination("BILL_200", 200.0, "Q200", CashDenominationType.BILL)
    val Q_BILL_100 = CashDenomination("BILL_100", 100.0, "Q100", CashDenominationType.BILL)
    val Q_BILL_50  = CashDenomination("BILL_50",   50.0, "Q50",  CashDenominationType.BILL)
    val Q_BILL_20  = CashDenomination("BILL_20",   20.0, "Q20",  CashDenominationType.BILL)
    val Q_BILL_10  = CashDenomination("BILL_10",   10.0, "Q10",  CashDenominationType.BILL)
    val Q_BILL_5   = CashDenomination("BILL_5",     5.0, "Q5",   CashDenominationType.BILL)
    val Q_BILL_1   = CashDenomination("BILL_1",     1.0, "Q1 (billete)", CashDenominationType.BILL)
    val Q_COIN_1   = CashDenomination("COIN_1",     1.0, "Q1 (moneda)",  CashDenominationType.COIN)
    val Q_COIN_50  = CashDenomination("COIN_050",   0.50, "Q0.50", CashDenominationType.COIN)
    val Q_COIN_25  = CashDenomination("COIN_025",   0.25, "Q0.25", CashDenominationType.COIN)
    val Q_COIN_10  = CashDenomination("COIN_010",   0.10, "Q0.10", CashDenominationType.COIN)
    val Q_COIN_05  = CashDenomination("COIN_005",   0.05, "Q0.05", CashDenominationType.COIN)
    val Q_COIN_01  = CashDenomination("COIN_001",   0.01, "Q0.01", CashDenominationType.COIN)

    val ALL: List<CashDenomination> = listOf(
        Q_BILL_200, Q_BILL_100, Q_BILL_50, Q_BILL_20, Q_BILL_10, Q_BILL_5, Q_BILL_1,
        Q_COIN_1, Q_COIN_50, Q_COIN_25, Q_COIN_10, Q_COIN_05, Q_COIN_01
    )

    val BILLS: List<CashDenomination> = ALL.filter { it.type == CashDenominationType.BILL }
    val COINS: List<CashDenomination> = ALL.filter { it.type == CashDenominationType.COIN }

    private val byCode: Map<String, CashDenomination> = ALL.associateBy { it.code }
    fun byCode(code: String): CashDenomination? = byCode[code]
}

/**
 * Conteo de una denominación específica en un evento (apertura, cierre, dejado-para-mañana,
 * depósito al owner, cuadre sorpresa). Persiste como doc en una subcolección.
 *
 * `subtotal = quantity * denomination.value`, denormalizado para evitar recálculo al leer.
 */
data class CashDenominationCount(
    val denomCode: String = "",
    val quantity: Int = 0,
    val subtotal: Double = 0.0
) {
    companion object {
        fun of(denom: CashDenomination, quantity: Int): CashDenominationCount =
            CashDenominationCount(denom.code, quantity, denom.value * quantity)
    }
}

/** Suma de subtotales — útil para totalizar un mapa code→quantity. */
fun Map<String, Int>.totalDenominationValue(): Double =
    entries.sumOf { (code, qty) ->
        (CashDenominations.byCode(code)?.value ?: 0.0) * qty
    }

/** Convierte mapa code→quantity a lista persistente de [CashDenominationCount]. */
fun Map<String, Int>.toDenominationCounts(): List<CashDenominationCount> =
    mapNotNull { (code, qty) ->
        val denom = CashDenominations.byCode(code) ?: return@mapNotNull null
        if (qty <= 0) null else CashDenominationCount.of(denom, qty)
    }
