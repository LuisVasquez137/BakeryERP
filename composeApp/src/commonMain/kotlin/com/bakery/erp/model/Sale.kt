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
    /**
     * Colaborador que cobró, identificado con PIN en un equipo de sucursal. [cashierId] es la
     * CUENTA (la misma para todas las cajeras del equipo); esto dice quién era. Vacío en
     * ventas de cuentas personales y en las anteriores a oct 2026.
     */
    val cashierEmployeeId: String = "",
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
    /**
     * Unidades vendidas por grupo de precio ("Pan Q0.50"), en arreglos paralelos:
     * `groupIds[i]` vendió `groupUnits[i]`. Son líneas que no descuentan ningún producto al
     * cobrarse — el conteo de cierre las reparte (ver `allocateGroupSales`).
     *
     * Van denormalizadas en el encabezado para que el conteo lea UNA consulta de ventas y
     * no la subcolección de líneas de cada ticket. Arreglos y no mapa porque GitLive no
     * decodifica `Map<String, Any?>`.
     */
    val groupIds: List<String> = emptyList(),
    val groupUnits: List<Double> = emptyList(),
    /**
     * Empaques cobrados en esta venta (bolsas, cajas): cuántos y por cuánto. Es lo que contesta
     * "¿se cobró la bolsa?" en el historial sin abrir las líneas de cada ticket. El detalle de
     * cuál empaque va en las líneas ([SaleLine.packagingItemId]). 0 = no se cobró ninguno (o
     * venta anterior a esto, o empresa que no cobra empaque).
     */
    val packagingUnits: Double = 0.0,
    val packagingAmount: Double = 0.0,
    /**
     * Un cobro en caja puede pagar varias ventas de una vez: el pan del mostrador más la
     * cuenta del restaurante que el cliente trajo. Todas llevan el mismo [checkoutId] y los
     * mismos datos del pago —[checkoutTotal], [tenderedAmount], [changeAmount] y los
     * billetes—, porque lo que se reclama ("me dio mal el vuelto") es el cobro, no cada venta.
     * Vacío = venta cobrada sola (o anterior a esto): el cobro es la venta misma.
     */
    val checkoutId: String = "",
    /** Lo que se cobró en total en ese cobro. 0 = igual a [total]. */
    val checkoutTotal: Double = 0.0,
    /**
     * Con qué billetes pagó el cliente, en arreglos paralelos: `billValues[i]` × `billCounts[i]`.
     * Solo se sabe cuando la cajera los marcó tocando los billetes; si tecleó el monto, van
     * vacíos y queda solo [tenderedAmount].
     */
    val billValues: List<Double> = emptyList(),
    val billCounts: List<Double> = emptyList(),
    /**
     * Venta a crédito ([SalePaymentMethod.CREDIT]): a qué cuenta se cargó y quién se llevó la
     * mercadería. Snapshot, igual que el resto de la venta: sigue diciendo lo mismo aunque la
     * cuenta se renombre. El cargo vive en `creditMovements` con `saleId` = esta venta.
     */
    val creditAccountId: String = "",
    val creditAccountName: String = "",
    val creditPersonName: String = "",
    /**
     * Venta a un revendedor: se cobró con SU lista de precios, no al precio público. Snapshot
     * del cliente y de la lista, igual que el crédito: el reporte tiene que poder separar lo
     * que se vendió más barato a revendedores aunque mañana cambie su lista. Vacío = mostrador.
     */
    val resellerId: String = "",
    val resellerName: String = "",
    val resellerPriceListName: String = "",
    /**
     * Descuento de colaborador por pagar en efectivo: a qué cuenta (colaborador) se le hizo y
     * con qué %. El monto va en [discount]. Snapshot: el % puede cambiar mañana.
     */
    val discountAccountId: String = "",
    val discountLabel: String = "",
    /**
     * Factura electrónica (FEL). Vacío = no se facturó. Ver [FelInvoiceStatus]: la app escribe
     * SOLICITADA junto con la venta y todo lo demás lo escribe la Cloud Function.
     */
    val invoiceStatus: String = "",
    /** Documento en `companies/{c}/dtes`. Varias ventas de un mismo cobro comparten uno. */
    val dteId: String = "",
    val invoiceSerie: String = "",
    val invoiceNumero: String = "",
    val invoiceUuid: String = "",
    /**
     * A quién se pidió facturar ("CF" o NIT). Viaja en la venta para que el reintento del
     * servidor pueda emitirla aunque la llamada desde la caja nunca haya llegado.
     */
    val invoiceReceptorId: String = "",
    val invoiceReceptorName: String = "",
    /** La factura pedida es "sin detalle". Viaja con la venta por la misma razón que el receptor. */
    val invoiceWithoutDetail: Boolean = false,
    /** Anticipo aplicado a esta venta ([CustomerDeposit]). El monto va también en el desglose. */
    val depositId: String = "",
    val depositFolio: String = "",
    val depositAmount: Double = 0.0,
    /**
     * Cargo de domicilio de una cuenta de ComandaYa. No viene como línea: la factura lo agrega
     * aparte, o saldría por menos de lo cobrado.
     */
    val externalDeliveryFee: Double = 0.0,
    /** Factura que ComandaYa emitió para esta cuenta ([FelInvoiceStatus.EXTERNA]). */
    val externalDteId: String = "",
    /**
     * Facturada en BakeOS Y en ComandaYa: el mismo consumo tiene dos facturas y hay que anular
     * una. Lo marca el servidor al enterarse; no se corrige solo.
     */
    val invoiceConflict: Boolean = false,
    val createdAt: Long = 0L
)

/** Total del cobro al que pertenece esta venta. */
val Sale.effectiveCheckoutTotal: Double
    get() = checkoutTotal.takeIf { it > 0.0 } ?: total

/** Billetes con los que se pagó, de mayor a menor. Vacío si se tecleó el monto. */
val Sale.tenderedBills: List<Pair<Double, Int>>
    get() = billValues.zip(billCounts).map { it.first to it.second.toInt() }
        .filter { it.second > 0 }
        .sortedByDescending { it.first }

/** Unidades vendidas por grupo de precio en esta venta. */
val Sale.groupUnitsById: Map<String, Double>
    get() = groupIds.zip(groupUnits).groupBy({ it.first }, { it.second })
        .mapValues { (_, v) -> v.sum() }

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
    /**
     * Fiado: no entra dinero, se carga a una cuenta de crédito. No suma al efectivo esperado
     * de la gaveta (`expectedCashNow` solo mira `totalCashSales`), así que no descuadra caja.
     */
    const val CREDIT   = "CREDIT"
    /**
     * La parte de una venta que pagó un anticipo ([CustomerDeposit]) recibido otro día o en otra
     * caja. No es un método que se elija: aparece en el desglose. No suma al efectivo esperado
     * de la gaveta — ese dinero entró como movimiento en la caja donde se recibió.
     */
    const val CUSTOMER_DEPOSIT = "CUSTOMER_DEPOSIT"
    /**
     * Pago dividido (parte tarjeta, parte efectivo…). Solo va en el encabezado: el detalle por
     * método está en `paymentBreakdown`, que es lo que suman la caja y los reportes. Ver
     * `SplitPayment.kt`.
     */
    const val MIXED = "MIXED"

    fun label(method: String) = when (method) {
        CASH     -> "Efectivo"
        CARD     -> "Tarjeta"
        TRANSFER -> "Transferencia"
        CREDIT   -> "Crédito"
        CUSTOMER_DEPOSIT -> "Anticipo"
        MIXED    -> "Mixto"
        else     -> "Desconocido"
    }

    /** Formas con las que entra dinero. [CREDIT] se ofrece aparte, solo con permiso. */
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
