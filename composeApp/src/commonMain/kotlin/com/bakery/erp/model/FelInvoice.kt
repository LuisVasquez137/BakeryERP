package com.bakery.erp.model

import com.bakery.erp.util.fmt

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

/** Certificador, tal como aparece en la representación gráfica oficial de Digifact. */
const val FEL_CERTIFIER_NAME = "DIGIFACT SERVICIOS, SOCIEDAD ANONIMA"
const val FEL_CERTIFIER_NIT = "7745482-0"

/** Verificador público de la SAT, el mismo para cualquier certificador. */
private const val FEL_SAT_VERIFIER =
    "https://felpub.c.sat.gob.gt/verificador-web/publico/vistas/verificacionDte.jsf"

/**
 * URL del QR de la factura, en el formato de la SAT ("Reglas y Validaciones FEL", 5.6): el
 * cliente lo escanea y ve su factura en el portal de la SAT. Null si falta la autorización o
 * el NIT emisor — un QR que no lleva a ningún lado es peor que no imprimirlo.
 */
fun felVerificationUrl(uuid: String, emisorNit: String, receptorId: String, total: Double): String? {
    if (uuid.isBlank() || emisorNit.isBlank()) return null
    val emisor = cleanNit(emisorNit)
    // "CF" no tiene dígitos: cleanNit lo deja vacío y cae a consumidor final.
    val receptor = cleanNit(receptorId).ifEmpty { FEL_CONSUMIDOR_FINAL }
    return "$FEL_SAT_VERIFIER?tipo=autorizacion&numero=$uuid&emisor=$emisor&receptor=$receptor" +
        "&monto=${total.fmt(2)}"
}

/** "FPEQ" → cómo se titula en el ticket. */
fun felDocTypeTitle(docType: String): String = when (docType.uppercase()) {
    "FPEQ" -> "FACTURA PEQUEÑO CONTRIBUYENTE"
    "FCAM" -> "FACTURA CAMBIARIA"
    else -> "FACTURA"
}

/**
 * Frases fiscales a imprimir. Se prefieren las que el servidor guardó al certificar (son las
 * que viajaron en el XML); si faltan, solo se afirma lo que el tipo de documento garantiza.
 * En régimen general la frase depende del ISR del emisor: mejor ninguna que la que no es.
 */
fun felPhrasesFor(result: FelCertifyResult): List<String> = result.phrases.ifEmpty {
    if (result.docType.equals("FPEQ", ignoreCase = true)) listOf("No genera derecho a crédito fiscal") else emptyList()
}

/**
 * Una venta que salió sin factura y se puede facturar después, desde el historial (el cliente
 * la pide al rato, o la cajera olvidó marcarla). Solo pan de una sucursal con NIT: la cuenta
 * del restaurante la factura ComandaYa, y emitirla acá también sería una doble factura del
 * mismo consumo. Una venta anulada o todavía sin recibir en caja no se factura.
 */
fun felCanInvoiceLater(sale: Sale, branchHasNit: Boolean): Boolean =
    branchHasNit &&
        sale.invoiceStatus.isBlank() &&
        sale.status == SaleStatus.COMPLETED &&
        sale.salesChannel == SalesChannel.BAKERY &&
        sale.total > 0.0
