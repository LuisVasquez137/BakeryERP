package com.bakery.erp.model

/**
 * Qué clase de desembolso documenta un `Purchase`.
 *
 * Los dos comparten documento a propósito, en vez de vivir en colecciones separadas: los
 * cuatro `PurchaseLineKind`, el ingreso a inventario, la generación de gastos, la foto y la
 * corrección por diferencia al editar o borrar son EXACTAMENTE los mismos. Un modelo
 * paralelo habría duplicado `applyStockForPurchase`, `applyStockDelta`,
 * `syncPurchaseSideEffects` y la cascada de borrado — cuatro caminos que mueven inventario,
 * que es justo la duplicación que este módulo lleva evitando.
 *
 * Lo que de verdad cambia entre uno y otro es dónde viven la sucursal y el lugar de compra.
 */
object PurchaseKind {
    /** Factura de un proveedor: un lugar, una sucursal, con crédito y número de factura. */
    const val INVOICE = "INVOICE"

    /**
     * Compra rápida: se paga en el momento, no hay factura ni crédito ni vencimiento, y lo
     * que se compró son varias cosas chiquitas que pueden ir a sucursales distintas. Por eso
     * la sucursal y el lugar bajan del encabezado a la línea.
     *
     * Se llama por lo que se hace —anotar rápido— y no por quién se movió: cubre igual la
     * salida al mercado que al repartidor de gaseosas que llegó a tocar la puerta. El nombre
     * anterior ("vuelta de compras") solo describía el primer caso y dejaba el segundo sin
     * lugar obvio donde anotarse, que es como los gastos del día terminaban capturándose a
     * mano en el módulo de Gastos, sin inventario y sin comprobante.
     */
    const val QUICK = "QUICK"

    /**
     * Valor con el que se guardaron las compras rápidas antes del cambio de nombre. Se lee,
     * no se escribe: renombrar el dato guardado obligaría a migrar documentos para arreglar
     * una etiqueta que el usuario nunca ve.
     */
    const val LEGACY_MARKET_RUN = "MARKET_RUN"

    /** `true` si el valor guardado —nuevo o viejo— corresponde a una compra rápida. */
    fun isQuick(kind: String): Boolean = kind == QUICK || kind == LEGACY_MARKET_RUN

    fun label(kind: String) = if (isQuick(kind)) "Compra rápida" else "Factura"
}

/** Ver [PurchaseKind.isQuick]. Tolera el valor con el que se guardaron las compras viejas. */
val Purchase.isQuick: Boolean
    get() = PurchaseKind.isQuick(kind)

/**
 * Con qué nombre se reconoce la compra en la lista.
 *
 * Una factura se busca por proveedor. Una compra rápida no tiene, así que salía como
 * "Sin proveedor" y todas se veían iguales — imposible encontrar la de las sandías entre
 * treinta filas idénticas. Se identifican por lo que traen, que es como uno se acuerda de
 * ellas, y sale de [itemNames], ya denormalizado para la búsqueda: no hay que leer la
 * subcolección de cada una ni migrar nada.
 */
fun Purchase.listTitle(maxItems: Int = 3): String {
    if (!isQuick) return supplierName.ifEmpty { "Sin proveedor" }
    if (itemNames.isEmpty()) return supplierName.ifEmpty { "Compra rápida" }
    val shown = itemNames.take(maxItems).joinToString(", ")
    val rest = itemNames.size - maxItems
    return if (rest > 0) "$shown +$rest" else shown
}

data class Purchase(
    val id: String = "",
    /** Ver [PurchaseKind]. Las compras anteriores no lo traen y son todas facturas. */
    val kind: String = PurchaseKind.INVOICE,
    /**
     * En una factura es LA sucursal que recibe. En una compra rápida es solo el default de las
     * líneas: cada una puede ir a otra. Ver `PurchaseLine.effectiveBranchId`.
     */
    val branchId: String = "",
    val branchName: String = "",
    /**
     * Proveedor del catálogo. **Puede ir vacío** con [supplierName] lleno: es el proveedor
     * ocasional —la tienda de la esquina, el puesto del mercado— al que no vale la pena dar
     * de alta para una compra que no se repite. En una compra rápida va vacío siempre: ahí el lugar
     * es de cada línea, no de la compra.
     */
    val supplierId: String = "",
    val supplierName: String = "",
    val date: Long = 0L,
    val notes: String? = null,
    val lineCount: Int = 0,
    val createdAt: Long = 0L,
    val invoiceNumber: String? = null,
    val receivedById: String = "",
    val receivedByName: String = "",
    val status: String = PurchaseStatus.RECEIVED_PENDING_PAYMENT,
    // ── Monto (denormalizado desde las líneas al guardar) ────────────────────
    // Sin esto la lista no puede mostrar montos ni KPIs sin leer N subcolecciones.
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    /**
     * Parte de [totalAmount] que entró a inventario (materia prima y reventa), y parte que
     * ya se gastó (suministros y servicios).
     *
     * Denormalizados desde las líneas por la misma razón que [totalAmount]: el reporte
     * financiero recorre un mes entero y abrir la subcolección de cada compra sería una
     * lectura por fila. Ambos en 0 = compra anterior al campo; ver `Purchase.amountSplit()`,
     * que deduce el reparto del encabezado en ese caso.
     *
     * El corte importa porque **el pedazo gastado ya está contado** en `ExpenseSummary`:
     * sumar la compra entera a los egresos contaría dos veces el cloro y el mantenimiento.
     */
    val inventoryAmount: Double = 0.0,
    val expensedAmount: Double = 0.0,
    /**
     * IVA contenido en [totalAmount]. Los precios se capturan CON IVA incluido —es el número
     * que trae la factura del proveedor— así que el impuesto se despeja hacia atrás y nunca
     * se suma encima. Se guarda calculado y no derivado para que una factura de agosto siga
     * mostrando su IVA aunque la tasa cambie.
     */
    val taxAmount: Double = 0.0,
    /** Snapshot de la tasa aplicada. 0 = exento / sin desglose. */
    val taxRatePercent: Double = 0.0,
    /**
     * Total que dice el papel, tecleado por quien capturó. Existe para dejar constancia de
     * que alguien lo cotejó: si difiere de [totalAmount] la captura tiene un error, y saberlo
     * después vale más que el propio número. 0 = no se declaró.
     */
    val declaredTotal: Double = 0.0,
    /**
     * Foto de la factura física. Vacío = no se adjuntó.
     *
     * Es el respaldo de lo capturado: cuando el total no cuadra o el proveedor reclama, lo
     * que resuelve la discusión es el papel, no el documento de Firestore.
     */
    val invoicePhotoUrl: String = "",
    /** Vencimiento más próximo entre las líneas perecederas. 0 = nada que caduque. */
    val nearestExpiryDate: Long = 0L,
    /** Insumo al que corresponde [nearestExpiryDate]. Sin él la alerta diría "algo vence". */
    val nearestExpiryItemName: String = "",
    // ── Crédito ──────────────────────────────────────────────────────────────
    val paymentTermsDays: Int = 0,   // snapshot del proveedor. 0 = contado
    val dueDate: Long = 0L,          // 0 = sin vencimiento
    // ── Recepción ────────────────────────────────────────────────────────────
    // 0 = el stock todavía NO entró (status PENDING_RECEIPT).
    val receivedAt: Long = 0L,
    // ── Búsqueda por contenido (denormalizado desde las líneas) ──────────────
    // Permite buscar "Aceite" en la lista sin abrir la subcolección de cada factura.
    val itemIds: List<String> = emptyList(),
    val itemNames: List<String> = emptyList(),
    /**
     * Tipos de línea que contiene la factura (`PurchaseLineKind`). Denormalizado por la
     * misma razón que [itemIds]: filtrar "compras de administración" en la lista sin
     * abrir la subcolección de cada una. Vacío en compras anteriores a los tres flujos —
     * ahí se asume materia prima, que es lo único que existía.
     */
    val lineKinds: List<String> = emptyList(),
    // ── De qué bolsa salió la plata ─────────────────────────────────────
    /**
     * Ver `ExpensePaidFrom`. Vacío = compra anterior al campo, o capturada sin declararlo.
     *
     * Vive en la compra y no solo en los gastos que genera porque **una compra rápida puede
     * no generar ninguno**: si todo lo que trae es materia prima o reventa, es inventario y
     * no hay `Expense` donde colgar la atribución. El efectivo igual salió de la gaveta y
     * al cuadrar tiene que faltar.
     */
    val paidFrom: String = "",
    /**
     * Sesión de caja de la que salió el efectivo, cuando [paidFrom] es `CASH_DRAWER`. Es el
     * vínculo con el `CashMovement` de tipo EXPENSE que baja el efectivo esperado al cierre.
     */
    val cashSessionId: String = "",
    /** Nombre de la caja, para decir "salió de Caja 2" sin leer la sesión. */
    val cashRegisterName: String = ""
)

object PurchaseStatus {
    const val PENDING_RECEIPT = "PENDING_RECEIPT"
    const val RECEIVED_PENDING_PAYMENT = "RECEIVED_PENDING_PAYMENT"
    const val PAID = "PAID"

    fun label(status: String) = when (status) {
        PENDING_RECEIPT -> "Pendiente de recibir"
        RECEIVED_PENDING_PAYMENT -> "Pendiente de pago"
        PAID -> "Pagado"
        else -> "Desconocido"
    }
}

/** Saldo pendiente de la factura. Nunca negativo (un sobrepago no vuelve deuda a favor). */
val Purchase.balanceDue: Double
    get() = (totalAmount - paidAmount).coerceAtLeast(0.0)

/** Tolerancia de centavos: un saldo de Q0.004 es una factura saldada, no una deuda. */
const val PURCHASE_BALANCE_EPSILON = 0.01

/**
 * Lo que se le muestra al usuario. El estado ALMACENADO se queda en tres valores simples
 * (`PurchaseStatus`); "vencida" y "abonada" son proyecciones contra la fecha de hoy y el
 * saldo — guardarlas obligaría a un job que reescriba documentos cada medianoche.
 * Mismo criterio que `effectiveOrderDisplay` en producción.
 */
enum class PurchaseDisplayStatus(val label: String, val shortLabel: String) {
    PENDING_RECEIPT("Pendiente de recibir", "Por recibir"),
    OVERDUE("Vencida", "Vencida"),
    PARTIALLY_PAID("Abonada", "Abonada"),
    PENDING_PAYMENT("Pendiente de pago", "Pte. pago"),
    PAID("Pagada", "Pagada")
}

fun Purchase.displayStatus(nowMillis: Long): PurchaseDisplayStatus = when {
    status == PurchaseStatus.PENDING_RECEIPT -> PurchaseDisplayStatus.PENDING_RECEIPT
    status == PurchaseStatus.PAID || balanceDue <= PURCHASE_BALANCE_EPSILON -> PurchaseDisplayStatus.PAID
    dueDate > 0L && dueDate < nowMillis -> PurchaseDisplayStatus.OVERDUE
    paidAmount > PURCHASE_BALANCE_EPSILON -> PurchaseDisplayStatus.PARTIALLY_PAID
    else -> PurchaseDisplayStatus.PENDING_PAYMENT
}

/**
 * Días completos para el vencimiento; negativo = días de mora. `null` si no hay crédito.
 *
 * Se cuentan días COMPLETOS en ambas direcciones (la división de enteros trunca hacia cero):
 * a 30 h del vencimiento faltan "1 día", y 5 días y medio de mora son "5 días". Redondear
 * hacia arriba inflaría el plazo que queda, que es justo el número que no conviene inflar.
 */
fun Purchase.daysUntilDue(nowMillis: Long): Int? {
    if (dueDate <= 0L) return null
    return ((dueDate - nowMillis) / 86_400_000L).toInt()
}

data class PurchaseLine(
    val id: String = "",
    val ingredientId: String = "",
    val ingredientName: String = "",
    val ingredientUnit: String = "",       // Unidad base (allowedUnits[0]) — para stock
    val quantityReceived: Double = 0.0,   // En unidad base — lo que se suma al stock
    val packagesReceived: Double = 0.0,   // Cuántas presentaciones recibió el baker (display)
    val packageDescription: String = "",  // Descripción del formato (ej: "Saco 100 lb") (display)
    val unitCost: Double = 0.0,
    val packageCost: Double = 0.0,        // Costo total del paquete al momento de la compra (ej: Q200/saco)
    val formatId: String = "",            // ID del PurchaseFormat — para actualizar precio si cambia
    // Reventa: si lineKind == RESALE_PRODUCT, la línea es un producto terminado de reventa
    // (no un insumo). Ingresa a FinishedProductStock en vez de BranchStock. Reutiliza
    // quantityReceived (unidades) y unitCost (costo por unidad).
    val lineKind: String = PurchaseLineKind.INGREDIENT,
    val productId: String = "",
    val productName: String = "",
    /** Caducidad de lo recibido en esta línea. 0 = no aplica o no se anotó. */
    val expiryDate: Long = 0L,
    /** Código de lote impreso en el empaque, si lo trae. Documental. */
    val lotCode: String = "",
    // ── Línea de servicio (lineKind == SERVICE) ──────────────────────────────
    /**
     * Descripción libre del servicio ("Mantenimiento del horno rotativo"). Es el nombre
     * visible de la línea cuando no hay catálogo detrás.
     */
    val conceptName: String = "",
    /**
     * Concepto del catálogo al que apunta la línea (ver [PurchaseConcept]). Vacío = texto
     * libre, que sigue siendo lo correcto para lo que no se vuelve a comprar.
     *
     * Es lo único que permite sumar el historial: sin id estable, el mismo renglón escrito
     * dos veces distinto son dos cosas distintas y "¿cuántas bolsas le compré?" no se puede
     * contestar. No reemplaza a [conceptName] — ése se guarda igual como snapshot, así una
     * factura vieja sigue diciendo lo que decía aunque el concepto se renombre después.
     */
    val conceptId: String = "",
    /** Unidad en que se contó ("bolsa", "caja", "viaje"). Display y agregación, no stock. */
    val conceptUnit: String = "",
    /**
     * Categoría del gasto que genera la línea. Se guarda el nombre del enum
     * `ExpenseCategory`; vacío cae a OTROS. Aplica a SERVICE y a SUPPLY.
     */
    val expenseCategory: String = "",
    /**
     * Activo fijo al que se le imputa el gasto (el horno que se reparó, la camioneta que
     * cargó combustible). Con esto la línea escribe además su `AssetHistoryEntry`, así el
     * mantenimiento capturado desde la factura actualiza la próxima fecha del horno sin
     * teclearlo dos veces. Vacío = gasto general.
     */
    val assetId: String = "",
    val assetName: String = "",
    /**
     * Tipo de evento con el que entra al historial del activo (nombre de `AssetEventType`:
     * MAINTENANCE, REPAIR…). Solo con [assetId]. Vacío = MAINTENANCE.
     */
    val assetEventType: String = "",
    // ── Compra rápida (Purchase.kind == QUICK) ──────────────────────────────
    /**
     * Sucursal a la que va ESTA línea. Vacío = la del encabezado.
     *
     * Existe porque una compra rápida surte a varias sucursales de un solo desembolso: las
     * papas al restaurante, la fruta a la panadería, los trapos a la otra. Con la sucursal
     * solo en el encabezado habría que partirla en tres compras y el desembolso
     * dejaría de ser uno.
     */
    val branchId: String = "",
    val branchName: String = "",
    /**
     * Dónde se compró esto, en texto libre ("mercado", "la tienda de la esquina"). No es un
     * proveedor del catálogo a propósito: la papa se compra donde esté más barata ese día, y
     * dar de alta cada puesto del mercado sería un catálogo que nadie mantiene.
     */
    val placeName: String = "",
    /**
     * A quién le corresponde el gasto de esta línea (ver [CostCenter]). Solo tiene sentido en
     * las líneas que se gastan (`purchaseLineIsExpense`); en las de inventario va vacío,
     * porque su costo lo recoge la producción o la venta de la sucursal que lo recibió.
     *
     * Vive en la línea y no en el encabezado por la compra rápida: un mismo desembolso trae la
     * gasolina del camión (Reparto) y los trapos de la tienda.
     */
    val costCenter: String = CostCenter.BRANCH
)

/**
 * Pone [costCenter] en las líneas que se gastan y lo limpia en las de inventario. La factura lo
 * elige una vez para todo el papel y lo estampa al guardar, así una línea que se agrega después
 * de elegir no se queda sin él.
 */
fun List<PurchaseLine>.withExpenseCostCenter(costCenter: String): List<PurchaseLine> = map {
    it.copy(costCenter = if (purchaseLineIsExpense(it.lineKind)) costCenter else CostCenter.BRANCH)
}

/** Sucursal efectiva de la línea: la suya si la declaró, si no la del encabezado. */
fun PurchaseLine.effectiveBranchId(purchase: Purchase): String =
    branchId.ifEmpty { purchase.branchId }

fun PurchaseLine.effectiveBranchName(purchase: Purchase): String =
    branchName.ifEmpty { purchase.branchName }

/**
 * Qué es cada renglón de la factura y, por tanto, qué mueve al guardarla.
 *
 * Los cuatro viven en el MISMO documento de compra a propósito: la factura de la
 * abarrotería trae huevos, tostadas y cloro en el mismo papel, con un solo total, un
 * solo crédito y un solo abono. Partirla en tres documentos con el mismo número de
 * factura dejaría "¿ya le pagué a Molinos?" sin respuesta.
 */
object PurchaseLineKind {
    /** Materia prima → `BranchStock` + Kardex + lote FEFO si es perecedera. */
    const val INGREDIENT = "INGREDIENT"

    /** Producto que se revende sin transformar → `FinishedProductStock` + movimiento. */
    const val RESALE_PRODUCT = "RESALE_PRODUCT"

    /**
     * Suministro de operación (cloro, papel) → `BranchStock` + Kardex **y** gasto.
     *
     * Hace las dos cosas porque nada más lo va a expensar después: nadie registra el
     * consumo de cloro y ningún costo de venta lo recoge. Si no se gasta el día que se
     * compra, no aparece nunca en el estado de resultados. No hay doble conteo por lo
     * mismo — su inventario es un conteo de control, no una valuación.
     */
    const val SUPPLY = "SUPPLY"

    /**
     * Suministro que NO lleva conteo de existencias (bolsas, bandejas desechables) →
     * **solo gasto**, sin `BranchStock` ni Kardex.
     *
     * Tiene catálogo, presentación y proveedor como cualquier suministro —por eso aparece
     * como fila en la factura y se captura tecleando la cantidad, no abriendo un diálogo por
     * renglón— y lo único que no hace es inventario. Lo decide `Ingredient.tracksStock`,
     * que es por ítem: el cloro se cuenta, la bolsa no.
     */
    const val SUPPLY_NO_STOCK = "SUPPLY_NO_STOCK"

    /**
     * Servicio o gasto sin inventario (mantenimiento del horno, combustible, flete) →
     * solo gasto, y opcionalmente una entrada en el historial del activo.
     *
     * Es el único tipo SIN catálogo: la ferretería vende algo distinto cada vez, así que
     * la línea es texto libre. Obligar a dar de alta un ítem para una reparación única
     * llenaría el catálogo de cosas que nunca se vuelven a comprar.
     */
    const val SERVICE = "SERVICE"

    /** Los cuatro, en el orden en que se muestran al capturar. */
    val ALL = listOf(INGREDIENT, RESALE_PRODUCT, SUPPLY, SUPPLY_NO_STOCK, SERVICE)

    fun label(kind: String) = when (kind) {
        INGREDIENT -> "Materia prima"
        RESALE_PRODUCT -> "Reventa"
        SUPPLY -> "Suministros"
        SUPPLY_NO_STOCK -> "Suministros sin stock"
        SERVICE -> "Servicios y gastos"
        else -> "Otro"
    }
}

/** `true` si el tipo de línea mueve algún inventario al guardarse. */
fun purchaseLineTouchesStock(kind: String): Boolean =
    kind != PurchaseLineKind.SERVICE && kind != PurchaseLineKind.SUPPLY_NO_STOCK

/** `true` si el tipo de línea genera un `Expense` en el período de la factura. */
fun purchaseLineIsExpense(kind: String): Boolean =
    kind == PurchaseLineKind.SUPPLY ||
        kind == PurchaseLineKind.SUPPLY_NO_STOCK ||
        kind == PurchaseLineKind.SERVICE

/**
 * Id del catálogo al que apunta la línea. Una línea de servicio no apunta a ningún
 * catálogo y devuelve vacío — por eso `itemIds` del encabezado nunca la incluye.
 */
val PurchaseLine.itemId: String
    get() = when (lineKind) {
        PurchaseLineKind.RESALE_PRODUCT -> productId
        PurchaseLineKind.SERVICE -> ""
        else -> ingredientId
    }

/** Nombre visible de la línea, sea insumo, reventa, suministro o servicio. */
val PurchaseLine.itemName: String
    get() = when (lineKind) {
        PurchaseLineKind.RESALE_PRODUCT -> productName
        PurchaseLineKind.SERVICE -> conceptName
        else -> ingredientName
    }

/**
 * Importe de la línea. Se prefiere el costo por presentación (`packageCost × paquetes`),
 * que es lo que dice la factura del proveedor; si no hay formato se cae al costo unitario.
 */
val PurchaseLine.lineTotal: Double
    get() = when {
        packageCost > 0.0 && packagesReceived > 0.0 -> packageCost * packagesReceived
        packageCost > 0.0 -> packageCost
        else -> unitCost * quantityReceived
    }

fun List<PurchaseLine>.purchaseTotal(): Double = sumOf { it.lineTotal }

/** Tipos presentes, en el orden canónico, para denormalizar en el encabezado. */
fun List<PurchaseLine>.kindsPresent(): List<String> =
    PurchaseLineKind.ALL.filter { kind -> any { it.lineKind == kind } }

/**
 * Tipos de la factura para mostrar y filtrar. Una compra vieja no tiene el campo y es,
 * por definición, de materia prima: era lo único que el módulo sabía registrar.
 */
/** Sucursales distintas a las que reparte esta compra. Una factura siempre da una. */
fun List<PurchaseLine>.branchesTouched(purchase: Purchase): List<Pair<String, String>> =
    map { it.effectiveBranchId(purchase) to it.effectiveBranchName(purchase) }
        .filter { it.first.isNotEmpty() }
        .distinct()

val Purchase.effectiveLineKinds: List<String>
    get() = lineKinds.ifEmpty { listOf(PurchaseLineKind.INGREDIENT) }

/** Línea que caduca primero entre las que declaran vencimiento. */
fun List<PurchaseLine>.nearestExpiryLine(): PurchaseLine? =
    filter { it.expiryDate > 0L }.minByOrNull { it.expiryDate }

/** Vencimiento más próximo entre las líneas que lo declaran. 0 si ninguna. */
fun List<PurchaseLine>.nearestExpiry(): Long = nearestExpiryLine()?.expiryDate ?: 0L

/**
 * Desglose de un total que YA incluye impuesto. Con 12% de IVA, Q112 son Q100 de base y
 * Q12 de impuesto — se divide, no se multiplica. Sumar el 12% sobre Q112 daría Q125.44 y
 * la factura dejaría de cuadrar con el papel, que es justo lo que no puede pasar.
 */
data class TaxBreakdown(val base: Double, val tax: Double, val total: Double)

fun taxBreakdownFromGross(totalWithTax: Double, ratePercent: Double): TaxBreakdown {
    if (ratePercent <= 0.0 || totalWithTax <= 0.0) {
        return TaxBreakdown(totalWithTax, 0.0, totalWithTax)
    }
    val base = totalWithTax / (1.0 + ratePercent / 100.0)
    return TaxBreakdown(base, totalWithTax - base, totalWithTax)
}

/** Tasa de IVA de Guatemala. Vive acá y no en la UI para que el backfill use la misma. */
const val DEFAULT_TAX_RATE_PERCENT = 12.0

/** Base imponible de la compra: el total menos el impuesto que ya venía adentro. */
val Purchase.subtotal: Double
    get() = (totalAmount - taxAmount).coerceAtLeast(0.0)

/**
 * Diferencia entre lo capturado y el total que dice el papel. `null` si no se declaró.
 * Positivo = capturamos de más.
 */
fun Purchase.captureDifference(): Double? =
    if (declaredTotal <= 0.0) null else totalAmount - declaredTotal
