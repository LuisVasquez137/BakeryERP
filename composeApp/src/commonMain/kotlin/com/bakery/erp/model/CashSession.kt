package com.bakery.erp.model

/**
 * Una sesión de caja: apertura+cierre de una [CashRegister] por un cajero específico.
 * Mientras [status] sea OPEN solo ese cajero puede operar la caja.
 *
 * Los totales [totalCashSales] / [totalCardSales] / [totalTransferSales] / [totalSales] / [saleCount]
 * son denormalizados — se incrementan al confirmar cada venta para evitar query N+1 al cerrar.
 */
data class CashSession(
    val id: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val registerId: String = "",
    val registerName: String = "",
    val cashierId: String = "",
    val cashierName: String = "",
    val openedAt: Long = 0L,
    val closedAt: Long? = null,
    /** Fondo de cambio inicial. */
    val openingBalance: Double = 0.0,
    val totalCashSales: Double = 0.0,
    val totalCardSales: Double = 0.0,
    val totalTransferSales: Double = 0.0,
    /** Vendido a crédito en el turno. Informativo: no es dinero que esté en la gaveta. */
    val totalCreditSales: Double = 0.0,
    val totalSales: Double = 0.0,
    val totalMovementsIn: Double = 0.0,
    val totalMovementsOut: Double = 0.0,
    /** Calculado al cerrar: openingBalance + totalCashSales + movsIn - movsOut. */
    val expectedCash: Double = 0.0,
    val countedCash: Double = 0.0,
    /** countedCash - expectedCash. Negativo = faltante, positivo = sobrante. */
    val difference: Double = 0.0,
    val status: String = CashSessionStatus.OPEN,
    val saleCount: Int = 0,
    val notes: String? = null,
    /** Si la sesión fue incluida en un cierre del día (`DailyCashClose`), su id. Bloquea ediciones. */
    val dailyCloseId: String? = null,
    /**
     * `true` si la cerró el sistema porque quedó abierta de un día para otro, no una persona.
     *
     * El cuadre de una sesión así dice cero diferencia **porque se asumió pago exacto**, no
     * porque alguien haya contado. Sin este campo, el reporte mostraría una caja perfectamente
     * cuadrada y nadie sabría que nunca se contó — que es peor que un faltante declarado.
     */
    val autoClosed: Boolean = false,
    /**
     * Colaborador que abrió la caja. La tablet del mostrador tiene un solo usuario para todas,
     * así que el usuario no dice quién estaba: el colaborador sí.
     */
    val openedByEmployeeId: String = "",
    val openedByEmployeeName: String = "",
    /** Colaborador que recibe la gaveta al cerrar (el del siguiente turno). Vacío = fin del día. */
    val receivedByEmployeeId: String = "",
    val receivedByEmployeeName: String = "",
    /** Usuario de la app que cerró. Puede no ser el que abrió (un supervisor, otro turno). */
    val closedByUserId: String = "",
    val closedByUserName: String = "",

    // ── Cadena de turnos (ver ShiftHandover) ────────────────────────────────
    /** Turno de la misma caja del que se recibió la gaveta. Vacío = el primero del día. */
    val previousSessionId: String = "",
    /**
     * Efectivo que se esperaba recibir al abrir (lo que dejó el turno anterior o el cierre del día
     * anterior). 0 si no había de dónde saberlo. Con [openingBalance], que es lo que se contó o
     * se recibió, dice si faltaba algo al empezar.
     */
    val openingExpected: Double = 0.0,
    /** Vouchers de tarjeta y transferencias que se recibieron del turno anterior (mismo día). */
    val carriedInCard: Double = 0.0,
    val carriedInTransfer: Double = 0.0,
    /** Efectivo que se retiró de la gaveta al cerrar (permiso aparte). */
    val closingWithdrawal: Double = 0.0,
    /** Lo que se entregó al cerrar: al siguiente turno, o lo que quedó para mañana. */
    val carriedOutCash: Double = 0.0,
    val carriedOutCard: Double = 0.0,
    val carriedOutTransfer: Double = 0.0,
    /** El cierre registró la entrega (los cierres anteriores a esto no la traen). */
    val handoverRecorded: Boolean = false
) {
    /** Diferencia al abrir: contado/recibido menos lo que se esperaba. Solo si había qué esperar. */
    val openingDifference: Double get() = if (openingExpected > 0.004) openingBalance - openingExpected else 0.0
}

/**
 * Efectivo que DEBERÍA haber en la gaveta ahora mismo.
 *
 * `expectedCash` (el campo) solo se llena al cerrar la sesión, así que mientras está abierta
 * vale 0 y no sirve para nada. Esta es la misma fórmula, calculada sobre los totales
 * denormalizados que se van actualizando con cada venta y cada movimiento.
 *
 * Vive acá y no en cada pantalla porque estaba copiada en cuatro (cierre de caja, cuadre
 * sorpresa —dos veces— y el diálogo de traslado), y de eso depende ahora también el tope de
 * lo que se puede pagar desde la caja: cuatro copias de la regla que decide si alcanza el
 * dinero es una que se va a olvidar de sumar algo.
 */
val CashSession.expectedCashNow: Double
    get() = openingBalance + totalCashSales + totalMovementsIn - totalMovementsOut

object CashSessionStatus {
    const val OPEN   = "OPEN"
    const val CLOSED = "CLOSED"

    fun label(status: String) = when (status) {
        OPEN   -> "Abierta"
        CLOSED -> "Cerrada"
        else   -> "Desconocido"
    }
}
