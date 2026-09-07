package com.bakery.erp.presentation.purchases

import com.bakery.erp.model.Branch
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.Product
import com.bakery.erp.model.PurchaseLine
import com.bakery.erp.model.PurchaseLineKind
import com.bakery.erp.model.IngredientUsage

/**
 * Una fila de la vuelta de compras, tal como se llena en pantalla.
 *
 * Existe aparte de [PurchaseLine] porque quien anota la vuelta piensa en "papas, Q25, para el
 * restaurante, del mercado" — no en tipos de línea ni en ids de catálogo. Convertirlo a línea
 * es [toPurchaseLine], y ahí es donde se decide el efecto: si la fila quedó pegada a un
 * insumo o a un producto, entra a inventario; si es texto suelto, es gasto y nada más.
 *
 * Ese es el punto del diseño: **la precisión se decide fila por fila, sin configurar nada**.
 * La mesita del teléfono no obliga a dar de alta un producto, y la fruta no deja de contarse
 * por estar en la misma vuelta que la mesita.
 */
internal data class MarketRunDraftLine(
    val id: String,
    /** Lo que se compró, en las palabras de quien fue. Siempre hay algo escrito acá. */
    val description: String = "",
    /** Insumo del catálogo al que quedó pegada la fila, si se eligió uno. */
    val ingredient: Ingredient? = null,
    /** Producto de reventa, si la fila apunta a uno. */
    val product: Product? = null,
    /** Sucursal a la que va. Null = la del encabezado. */
    val branch: Branch? = null,
    /** Dónde se compró, texto libre. */
    val placeName: String = "",
    val quantity: Double = 1.0,
    val amount: Double = 0.0,
    /** Solo para insumos perecederos (la fruta). 0 = no se anotó. */
    val expiryDate: Long = 0L
) {
    /** `true` si la fila va a mover inventario. Lo demás es gasto puro. */
    val touchesStock: Boolean get() = ingredient != null || product != null

    val label: String get() = description.ifEmpty { ingredient?.name ?: product?.name ?: "" }
}

/**
 * Convierte la fila a la línea que el resto del módulo ya sabe procesar.
 *
 * El `lineKind` sale de a qué quedó pegada la fila, no de una elección del usuario: un insumo
 * de producción es INGREDIENT, uno de operación es SUPPLY (y por eso se gasta), un producto
 * es RESALE_PRODUCT, y lo que no está en ningún catálogo es SERVICE — que en la vuelta
 * significa "gasto suelto", no un servicio contratado.
 */
internal fun MarketRunDraftLine.toPurchaseLine(expenseCategory: String): PurchaseLine {
    val kind = when {
        ingredient != null && ingredient.usage == IngredientUsage.SUPPLY -> PurchaseLineKind.SUPPLY
        ingredient != null -> PurchaseLineKind.INGREDIENT
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
        expenseCategory = expenseCategory,
        branchId = branch?.id.orEmpty(),
        branchName = branch?.name.orEmpty(),
        placeName = placeName.trim()
    )
}
