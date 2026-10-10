package com.bakery.erp.presentation.purchases

import com.bakery.erp.model.Branch
import com.bakery.erp.model.CostCenter
import com.bakery.erp.model.purchaseLineIsExpense
import com.bakery.erp.model.ExpenseCategory
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.Product
import com.bakery.erp.model.PurchaseConcept
import com.bakery.erp.model.PurchaseLine
import com.bakery.erp.model.PurchaseLineKind
import com.bakery.erp.model.ingredientPurchaseLineKind
import com.bakery.erp.model.itemName
import com.bakery.erp.model.lineTotal
import com.bakery.erp.model.IngredientUsage

/**
 * Una fila de la compra rápida, tal como se llena en pantalla.
 *
 * Existe aparte de [PurchaseLine] porque quien anota piensa en "papas, Q25, para el
 * restaurante, del mercado" — no en tipos de línea ni en ids de catálogo. Convertirlo a línea
 * es [toPurchaseLine], y ahí es donde se decide el efecto: si la fila quedó pegada a un
 * insumo o a un producto, entra a inventario; si es texto suelto, es gasto y nada más.
 *
 * Ese es el punto del diseño: **la precisión se decide fila por fila, sin configurar nada**.
 * La mesita del teléfono no obliga a dar de alta un producto, y la fruta no deja de contarse
 * por estar en la misma compra que la mesita.
 */
internal data class QuickPurchaseDraftLine(
    val id: String,
    /** Lo que se compró, en las palabras de quien fue. Siempre hay algo escrito acá. */
    val description: String = "",
    /** Insumo del catálogo al que quedó pegada la fila, si se eligió uno. */
    val ingredient: Ingredient? = null,
    /** Producto de reventa, si la fila apunta a uno. */
    val product: Product? = null,
    /**
     * Concepto del catálogo al que quedó pegada la fila (bolsas, flete). NO mueve inventario
     * —sigue siendo una línea de gasto— y lo único que agrega es poder sumar el historial:
     * "¿cuántas bolsas le compré este año?" necesita un id, no un texto.
     */
    val concept: PurchaseConcept? = null,
    /** En qué se contó, cuando la fila tiene concepto. "bolsas", "cajas", "viajes". */
    val conceptUnit: String = "",
    /** Sucursal a la que va. Null = la del encabezado. */
    val branch: Branch? = null,
    /** Dónde se compró, texto libre. */
    val placeName: String = "",
    val quantity: Double = 1.0,
    val amount: Double = 0.0,
    /** Solo para insumos perecederos (la fruta). 0 = no se anotó. */
    val expiryDate: Long = 0L,
    /**
     * En qué renglón del reporte financiero cae esta fila. Es **por fila** y no de toda la
     * compra porque una sola salida trae cosas de categorías distintas —la gasolina es
     * TRANSPORTE, los trapos son OTROS, la reparación es MANTENIMIENTO— y con una sola
     * categoría para todo el desembolso el reporte contesta "cuánto" pero nunca "en qué",
     * que es justamente para lo que existe el desglose.
     */
    val expenseCategory: ExpenseCategory = ExpenseCategory.OTROS,
    /**
     * A quién le corresponde el gasto (ver [CostCenter]). `null` = todavía no se eligió: desde
     * la caja general hay que decirlo, porque ahí se anotan las facturas de todos y dejarlo caer
     * a la sucursal de turno es como el contador terminaba cargado a la planta. Desde una caja
     * de tienda se toma como la sucursal.
     */
    val costCenter: String? = null
) {
    /** `true` si la fila se gasta (y por tanto tiene a quién cargarse). */
    val isExpense: Boolean
        get() = !touchesStock || ingredient?.usage == IngredientUsage.SUPPLY

    /** `true` si la fila va a mover inventario. Lo demás es gasto puro. */
    val touchesStock: Boolean get() = ingredient != null || product != null

    /**
     * Unidad en la que se descuenta del inventario, para poder mostrarla junto a la
     * cantidad. Sin ella "20" es ambiguo y termina entrando 1 lb de sandía donde se
     * compraron 20.
     */
    val stockUnit: String
        get() = ingredient?.effectiveBaseUnit ?: product?.unit.orEmpty()

    val label: String
        get() = description.ifEmpty { ingredient?.name ?: product?.name ?: concept?.name ?: "" }
}

/**
 * Reconstruye la fila de pantalla a partir de la línea guardada, para poder reabrir una
 * compra rápida y corregirla.
 *
 * Los objetos de catálogo se resuelven por id contra lo que ya trae el state. Si un insumo
 * fue dado de baja después de la compra, la fila **conserva su descripción y su monto** y
 * simplemente deja de estar pegada al catálogo: perder el renglón entero sería peor, porque
 * el total dejaría de cuadrar con lo que se pagó.
 */
internal fun PurchaseLine.toQuickDraft(
    ingredients: List<Ingredient>,
    products: List<Product>,
    branches: List<Branch>,
    concepts: List<PurchaseConcept> = emptyList()
): QuickPurchaseDraftLine = QuickPurchaseDraftLine(
    id = id,
    description = itemName,
    ingredient = ingredients.firstOrNull { it.id == ingredientId },
    product = products.firstOrNull { it.id == productId },
    concept = concepts.firstOrNull { it.id == conceptId },
    conceptUnit = conceptUnit,
    branch = branches.firstOrNull { it.id == branchId },
    placeName = placeName,
    quantity = quantityReceived.takeIf { it > 0.0 } ?: 1.0,
    amount = lineTotal,
    expiryDate = expiryDate,
    expenseCategory = runCatching { ExpenseCategory.valueOf(expenseCategory) }
        .getOrElse { ExpenseCategory.OTROS },
    // Lo guardado ya es una decisión: un gasto viejo sin el campo era de su sucursal.
    costCenter = costCenter
)

/**
 * Convierte la fila a la línea que el resto del módulo ya sabe procesar.
 *
 * El `lineKind` sale de a qué quedó pegada la fila, no de una elección del usuario: un insumo
 * de producción es INGREDIENT, uno de operación es SUPPLY (y por eso se gasta), un producto
 * es RESALE_PRODUCT, y lo que no está en ningún catálogo es SERVICE — que acá
 * significa "gasto suelto", no un servicio contratado.
 */
internal fun QuickPurchaseDraftLine.toPurchaseLine(): PurchaseLine {
    val kind = when {
        ingredient != null -> ingredientPurchaseLineKind(ingredient)
        product != null -> PurchaseLineKind.RESALE_PRODUCT
        else -> PurchaseLineKind.SERVICE
    }
    // La cantidad nunca es cero: `updateDraftLine` descarta las líneas en cero, y una fila
    // que el usuario escribió tiene que llegar al documento aunque no haya puesto cantidad.
    val qty = quantity.takeIf { it > 0.0 } ?: 1.0
    return PurchaseLine(
        id = id,
        lineKind = kind,
        ingredientId = ingredient?.id.orEmpty(),
        ingredientName = ingredient?.name.orEmpty(),
        ingredientUnit = ingredient?.effectiveBaseUnit.orEmpty(),
        // El producto se conserva aunque la línea sea de insumo: es el caso de la leche, que
        // se compra al insumo y se vende como producto. Así la corrección vuelve a encontrar
        // su renglón y la presentación del producto sigue actualizando su precio.
        productId = product?.id.orEmpty(),
        productName = product?.name.orEmpty(),
        quantityReceived = qty,
        // El monto tecleado es el TOTAL de la fila —"las papas me costaron Q25"—, no el
        // precio unitario: nadie en el mercado piensa por unidad. Se guarda como costo
        // unitario dividido para que `lineTotal` vuelva a dar exactamente lo tecleado.
        unitCost = amount / qty,
        packageCost = 0.0,
        expiryDate = expiryDate,
        // El texto de la fila es el nombre visible cuando no hay catálogo detrás.
        conceptName = if (kind == PurchaseLineKind.SERVICE) label else "",
        // El concepto solo tiene sentido en la línea de gasto: una fila pegada a un insumo ya
        // se agrupa por su id de catálogo.
        conceptId = if (kind == PurchaseLineKind.SERVICE) concept?.id.orEmpty() else "",
        conceptUnit = if (kind == PurchaseLineKind.SERVICE) conceptUnit.trim() else "",
        expenseCategory = expenseCategory.name,
        branchId = branch?.id.orEmpty(),
        branchName = branch?.name.orEmpty(),
        placeName = placeName.trim(),
        costCenter = if (purchaseLineIsExpense(kind)) costCenter ?: CostCenter.BRANCH else CostCenter.BRANCH
    )
}
