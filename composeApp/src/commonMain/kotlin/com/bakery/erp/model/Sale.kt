package com.bakery.erp.model

/** Cabecera de venta — una transacción del POS. */
data class Sale(
    val id: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val registerId: String = "",
    val registerName: String = "",
    val sessionId: String = "",
    val cashierId: String = "",
    val cashierName: String = "",
    val subtotal: Double = 0.0,
    /** Descuento total aplicado a la venta. 0 en MVP — placeholder Fase 2. */
    val discount: Double = 0.0,
    val total: Double = 0.0,
    val paymentMethod: String = SalePaymentMethod.CASH,
    /** Monto entregado por el cliente (solo efectivo; en tarjeta/transferencia = total). */
    val tenderedAmount: Double = 0.0,
    /** Vuelto en efectivo entregado al cliente (0 en tarjeta/transferencia). */
    val changeAmount: Double = 0.0,
    val status: String = SaleStatus.COMPLETED,
    /** Denormalizado para listas; igual a lines.size. */
    val lineCount: Int = 0,
    /**
     * Si la venta es un cobro de anticipo o saldo de un [CustomerOrder], ID del pedido.
     * Permite que el POS cobre el saldo de un pastel vinculándolo al encargo.
     */
    val linkedCustomerOrderId: String = "",
    /**
     * Quién escribió la venta. Ausente en las ventas viejas, que por definición son del POS.
     * Ver [SaleSource].
     */
    val source: String = SaleSource.POS,
    /**
     * Qué se vendió. Es lo que permite separar el consolidado: de las 5 sucursales, 3
     * venden pan Y comida de restaurante, y en el resumen del día tienen que poder verse
     * aparte. Ver [SalesChannel].
     */
    val salesChannel: String = SalesChannel.BAKERY,
    /**
     * Id de la venta en el sistema de origen (vacío si nació en BakeOS). Es la clave de
     * idempotencia de lo que entra de afuera: reprocesar la misma orden sobreescribe en vez
     * de duplicarla.
     */
    val externalId: String = "",
    /**
     * Quién tiene el dinero mientras no lo entregue en caja: el usuario de ComandaYa que
     * cerró la orden. Sin esto la lista de pendientes dice que falta dinero pero no a quién
     * preguntarle, que es la mitad del valor.
     */
    val externalUserId: String = "",
    val externalUserName: String = "",
    /** "Mesa 4", "Para llevar" — para que quien está en caja identifique la orden. */
    val externalTableLabel: String = "",
    /**
     * Número de orden tal como lo muestra ComandaYa. Es por lo que el mesero y la cajera se
     * refieren a la cuenta en voz alta ("la 47"), así que sin él la lista obliga a adivinar
     * por mesa y monto.
     */
    val externalOrderNumber: String = "",
    /**
     * Hora en que se tomó la orden en ComandaYa. NO es [createdAt]: ésa es la hora del cobro,
     * que es la que decide a qué día contable pertenece la venta. Las dos se necesitan y por
     * eso viajan separadas.
     */
    val externalOrderAt: Long = 0L,
    /**
     * Método -> monto. Una cuenta dividida se cobra con varios: el efectivo cuadra contra la
     * gaveta y la tarjeta contra los vouchers, así que el arqueo necesita los dos por
     * separado. Vacío = todo el total fue por [paymentMethod].
     */
    val paymentBreakdown: Map<String, Double> = emptyMap(),
    /** Cuándo se recibió el efectivo en caja. 0 mientras siga pendiente. */
    val collectedAt: Long = 0L,
    val createdAt: Long = 0L
)

/** De dónde vino la venta. */
object SaleSource {
    const val POS       = "POS"
    const val COMANDAYA = "COMANDAYA"
}

/** Qué se vendió — no dónde. Una misma sucursal puede tener los dos canales. */
object SalesChannel {
    const val BAKERY     = "BAKERY"
    const val RESTAURANT = "RESTAURANT"

    fun label(channel: String) = when (channel) {
        BAKERY     -> "Panadería"
        RESTAURANT -> "Restaurante"
        else       -> "Otro"
    }

    val all = listOf(BAKERY, RESTAURANT)
}

object SalePaymentMethod {
    const val CASH     = "CASH"
    const val CARD     = "CARD"
    const val TRANSFER = "TRANSFER"

    fun label(method: String) = when (method) {
        CASH     -> "Efectivo"
        CARD     -> "Tarjeta"
        TRANSFER -> "Transferencia"
        else     -> "Desconocido"
    }

    val all = listOf(CASH, CARD, TRANSFER)
}

/** El desglose efectivo de una venta: el explícito si lo trae, si no todo al método único. */
val Sale.effectivePaymentBreakdown: Map<String, Double>
    get() = paymentBreakdown.takeIf { it.isNotEmpty() } ?: mapOf(paymentMethod to total)

object SaleStatus {
    const val COMPLETED = "COMPLETED"
    /** Reservado para Fase 2 (anulación post-cobro). */
    const val VOIDED    = "VOIDED"
    /**
     * Venta real cuyo efectivo todavía no está en ninguna gaveta: el mesero cobró y marcó
     * la orden como pagada en ComandaYa, pero aún no se la entregó a caja. Deja de estar
     * pendiente cuando alguien la recibe desde el POS y queda atribuida a su sesión.
     *
     * Cuenta como ingreso desde que llega — el cliente ya pagó — pero no como efectivo
     * esperado de ninguna caja. Son dos hechos distintos en momentos distintos.
     */
    const val PENDING_COLLECTION = "PENDING_COLLECTION"

    /**
     * ¿Esta venta suma al ingreso del día? Lo que separa a `PENDING_COLLECTION` de
     * `COMPLETED` es dónde está el dinero, no si se vendió.
     */
    fun countsAsRevenue(status: String) =
        status == COMPLETED || status == PENDING_COLLECTION

    fun label(status: String) = when (status) {
        COMPLETED           -> "Completada"
        VOIDED              -> "Anulada"
        PENDING_COLLECTION  -> "Por recibir en caja"
        else                -> "Desconocido"
    }
}
