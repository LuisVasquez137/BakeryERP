package com.bakery.erp.model

/**
 * Resumen de ventas de **una sucursal en un día**.
 * Vive en `companies/{c}/salesDaily/{branchId}_{yyyy-MM-dd}`.
 *
 * **Por qué existe.** Los reportes y el Home preguntan por rangos de días, no por ventas
 * sueltas: leer la venta una por una para contestar "¿cuánto vendí este mes?" cuesta una
 * lectura por ticket y crece para siempre. Con esto, un mes son ~30 documentos por sucursal.
 * El detalle por venta sigue en `sales/` y se lee solo cuando se abre un día concreto —
 * **se guarda por venta, se lee por día.**
 *
 * **Por qué mapas paralelos y no objetos anidados.** GitLive no deserializa `Any`:
 * `get<Map<String, Any?>>()` revienta con "Serializer for Any not found" y el `runCatching`
 * de turno se lo traga en silencio. Con mapas de tipo concreto (`Map<String, Double>`,
 * `Map<String, String>`) la lectura es segura. Es el mismo recurso que usa
 * `ProductionLotLine.sourceOrderAllocations` con sus arreglos paralelos.
 *
 * Las claves de hora son `String` ("0".."23") porque las claves de un mapa de Firestore
 * siempre lo son; convertirlas a `Int` es trabajo del que arma el `SalesSummary`.
 */
data class SalesDaily(
    val id: String = "",
    val branchId: String = "",
    val branchName: String = "",
    /** Día operativo `yyyy-MM-dd`, en la zona horaria del dispositivo. */
    val businessDate: String = "",
    /** Inicio del día en millis — es por lo que se filtra el rango en el servidor. */
    val dateMillis: Long = 0L,
    val revenue: Double = 0.0,
    val transactionCount: Int = 0,
    val unitsSold: Double = 0.0,
    val paymentTotal: Map<String, Double> = emptyMap(),
    val paymentCount: Map<String, Int> = emptyMap(),
    val cashierName: Map<String, String> = emptyMap(),
    val cashierTotal: Map<String, Double> = emptyMap(),
    val cashierCount: Map<String, Int> = emptyMap(),
    val hourTotal: Map<String, Double> = emptyMap(),
    val productName: Map<String, String> = emptyMap(),
    val productQty: Map<String, Double> = emptyMap(),
    val productRevenue: Map<String, Double> = emptyMap(),
    /**
     * Canal -> monto. Es la razón de ser del desglose: de las 5 sucursales, 3 venden pan y
     * comida de restaurante en el mismo local, así que "cuánto vendió la sucursal" y
     * "cuánto de eso fue restaurante" son dos preguntas distintas sobre el mismo día.
     */
    val channelRevenue: Map<String, Double> = emptyMap(),
    val channelCount: Map<String, Int> = emptyMap(),
    val channelUnits: Map<String, Double> = emptyMap(),
    val updatedAt: Long = 0L
)

fun salesDailyId(branchId: String, businessDate: String): String = "${branchId}_$businessDate"

/**
 * Lo que **una** venta le aporta al resumen de su día. Se arma en el momento de cobrar, que
 * es cuando las líneas ya están en la mano y no cuestan una lectura extra.
 */
data class SaleContribution(
    val branchId: String,
    val branchName: String,
    val businessDate: String,
    val dateMillis: Long,
    val hour: Int,
    val total: Double,
    val paymentMethod: String,
    val cashierId: String,
    val cashierName: String,
    val channel: String,
    val units: Double,
    val productName: Map<String, String>,
    val productQty: Map<String, Double>,
    val productRevenue: Map<String, Double>
)

/**
 * Suma una venta al resumen del día. Función pura: el mismo cálculo sirve para el aporte
 * incremental al cobrar y para reconstruir un día entero desde cero, así que no hay dos
 * fórmulas que puedan discrepar.
 */
operator fun SalesDaily.plus(c: SaleContribution): SalesDaily = copy(
    id = salesDailyId(c.branchId, c.businessDate),
    branchId = c.branchId,
    // El nombre se refresca con el de la última venta: si renombran la sucursal, el resumen
    // del día en curso deja de mostrar el nombre viejo sin necesidad de migrar nada.
    branchName = c.branchName,
    businessDate = c.businessDate,
    dateMillis = c.dateMillis,
    revenue = revenue + c.total,
    transactionCount = transactionCount + 1,
    unitsSold = unitsSold + c.units,
    paymentTotal = paymentTotal.addDouble(c.paymentMethod, c.total),
    paymentCount = paymentCount.addInt(c.paymentMethod, 1),
    cashierName = cashierName + (c.cashierId to c.cashierName),
    cashierTotal = cashierTotal.addDouble(c.cashierId, c.total),
    cashierCount = cashierCount.addInt(c.cashierId, 1),
    hourTotal = hourTotal.addDouble(c.hour.toString(), c.total),
    productName = productName + c.productName,
    productQty = c.productQty.entries.fold(productQty) { acc, (k, v) -> acc.addDouble(k, v) },
    productRevenue = c.productRevenue.entries.fold(productRevenue) { acc, (k, v) -> acc.addDouble(k, v) },
    channelRevenue = channelRevenue.addDouble(c.channel, c.total),
    channelCount = channelCount.addInt(c.channel, 1),
    channelUnits = channelUnits.addDouble(c.channel, c.units)
)

private fun Map<String, Double>.addDouble(key: String, value: Double): Map<String, Double> =
    this + (key to (this[key] ?: 0.0) + value)

private fun Map<String, Int>.addInt(key: String, value: Int): Map<String, Int> =
    this + (key to (this[key] ?: 0) + value)
