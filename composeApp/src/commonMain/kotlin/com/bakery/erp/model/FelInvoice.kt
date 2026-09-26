package com.bakery.erp.model

/**
 * Estado de la factura de una venta ([Sale.invoiceStatus]). Vacío = la venta no se facturó.
 *
 * [SOLICITADA] la pone la APP al crear la venta, en la misma escritura: si la llamada a la
 * Cloud Function se pierde (la tablet se quedó sin internet justo al cobrar), el servidor
 * nunca se enteraría y la venta quedaría sin factura para siempre. El reintento automático
 * barre las SOLICITADA viejas, así que la factura sale igual. El resto de los estados los
 * escribe solo el servidor.
 */
object FelInvoiceStatus {
    const val SOLICITADA = "SOLICITADA"
    const val CERTIFICANDO = "CERTIFICANDO"
    const val CERTIFICADO = "CERTIFICADO"
    /** Falla transitoria (Digifact caído, sin red): se reintenta sola. */
    const val PENDIENTE = "PENDIENTE"
    /** Digifact/SAT rechazó el contenido o falta configuración: reintentar no sirve. */
    const val RECHAZADO = "RECHAZADO"
    const val ANULADO = "ANULADO"

    fun label(status: String): String = when (status) {
        SOLICITADA, CERTIFICANDO -> "Facturando…"
        CERTIFICADO -> "Facturada"
        PENDIENTE -> "Factura pendiente"
        RECHAZADO -> "Factura rechazada"
        ANULADO -> "Factura anulada"
        else -> ""
    }

    /** Todavía puede terminar en factura sin que nadie haga nada. */
    fun isInFlight(status: String) = status == SOLICITADA || status == CERTIFICANDO || status == PENDIENTE
}

/** Consumidor final: el receptor por defecto de la SAT. */
const val FEL_CONSUMIDOR_FINAL = "CF"

/**
 * Desde este monto la SAT no acepta "Consumidor Final": el comprador tiene que identificarse
 * con NIT (o CUI). Emitir una factura CF por encima la rechaza el certificador.
 */
const val FEL_CF_LIMIT = 2500.0

fun felRequiresIdentifiedBuyer(invoicedTotal: Double): Boolean = invoicedTotal >= FEL_CF_LIMIT

/** A quién se le factura, tal como lo eligió la cajera. */
data class FelInvoiceRequest(
    /** "CF" o el NIT limpio. */
    val receptorId: String = FEL_CONSUMIDOR_FINAL,
    val receptorName: String = "CONSUMIDOR FINAL"
) {
    val isConsumidorFinal get() = receptorId == FEL_CONSUMIDOR_FINAL
}

/**
 * Qué impide facturar con este receptor, o null si está bien. Lógica pura para que el diálogo
 * de cobro y el ViewModel digan exactamente lo mismo.
 */
fun felReceptorProblem(request: FelInvoiceRequest, invoicedTotal: Double): String? = when {
    request.isConsumidorFinal && felRequiresIdentifiedBuyer(invoicedTotal) ->
        "Desde Q ${FEL_CF_LIMIT.toInt()} la SAT pide el NIT del cliente."
    request.isConsumidorFinal -> null
    !isValidGuatemalaNit(request.receptorId) -> "El NIT no es válido."
    request.receptorName.isBlank() -> "Falta el nombre del cliente."
    else -> null
}

/** Lo que devuelve la Cloud Function al certificar: lo necesario para mostrar e imprimir. */
data class FelCertifyResult(
    val dteId: String = "",
    val status: String = "",
    val message: String = "",
    val serie: String = "",
    val numero: String = "",
    val uuid: String = "",
    val docType: String = "",
    val certifiedAt: Long = 0L,
    val total: Double = 0.0,
    val vat: Double = 0.0,
    val receptorId: String = FEL_CONSUMIDOR_FINAL,
    val receptorName: String = "CONSUMIDOR FINAL",
    val emisorNit: String = "",
    val emisorName: String = "",
    val emisorTradeName: String = "",
    val emisorAddress: String = "",
    val phrases: List<String> = emptyList(),
    val environment: String = ""
)
