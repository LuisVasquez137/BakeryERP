package com.bakery.erp.domain

import com.bakery.erp.model.POSCategory
import com.bakery.erp.model.PriceList
import com.bakery.erp.model.Product
import com.bakery.erp.model.ProductPrice
import com.bakery.erp.model.listRulePrice
import com.bakery.erp.model.roundToCents
import com.bakery.erp.model.samePrice

/**
 * Precio de cada producto en una lista (la del revendedor): el precio fijo del producto en esa
 * lista si lo tiene, si no la regla de la lista ([listRulePrice]: su escalón por precio público,
 * o el porcentaje), y si no hay nada, "sin precio" ([PriceSource.NONE]). El escalón se reporta
 * como [PriceSource.LIST_FACTOR]: las dos cosas son "la regla de la lista", no un precio del
 * producto, y así el despacho y las ventas viejas no necesitan una etiqueta nueva.
 *
 * Es la misma cascada que el despacho (`resolveWholesalePrice`), pero para una lista ya elegida
 * y sobre todo el catálogo de una vez. Vive acá y no en un ViewModel porque la usan dos: el
 * pedido que hace el revendedor desde su cuenta y la venta a revendedor en la caja. Si cada uno
 * calculara por su lado, el revendedor vería un precio en su pedido y la cajera le cobraría otro.
 */
fun resolveListPrices(
    list: PriceList,
    overrides: List<ProductPrice>,
    products: List<Product>
): Map<String, ResolvedPrice> {
    val overrideByProduct = overrides.associateBy { it.productId }
    return products.associate { p ->
        val override = overrideByProduct[p.id]
        val rule = list.listRulePrice(p.salePrice)
        p.id to when {
            override != null && override.unitPrice > 0 -> ResolvedPrice(
                unitPrice = roundToCents(override.unitPrice),
                priceListId = list.id,
                priceListName = list.name,
                found = true,
                source = PriceSource.PRODUCT_OVERRIDE
            )
            rule != null -> ResolvedPrice(
                unitPrice = rule,
                priceListId = list.id,
                priceListName = list.name,
                found = true,
                source = PriceSource.LIST_FACTOR
            )
            else -> ResolvedPrice(
                unitPrice = 0.0,
                priceListId = list.id,
                priceListName = list.name,
                found = false,
                source = PriceSource.NONE
            )
        }
    }
}

/**
 * Precio por pan de un grupo de precio ("Pan Q0.50") para la lista ya resuelta en [prices].
 *
 * El grupo se cobra sin decir qué pan es, así que solo tiene UN precio de revendedor si todos
 * sus panes cuestan lo mismo en la lista. Si uno tiene precio fijo distinto, no hay forma
 * honesta de cobrar el grupo junto y se devuelve `null`: la cajera tiene que elegir los panes
 * uno por uno. Los miembros son los que de verdad entran al grupo — mismo `salePrice` que
 * `groupUnitPrice`, igual que el reparto del conteo de cierre.
 */
fun groupListPrice(
    group: POSCategory,
    productsById: Map<String, Product>,
    prices: Map<String, ResolvedPrice>,
    list: PriceList
): Double? {
    val members = group.productIds.mapNotNull { productsById[it] }
        .filter { samePrice(it.salePrice, group.groupUnitPrice) }
    if (members.isEmpty()) return list.listRulePrice(group.groupUnitPrice)
    val memberPrices = members.map { prices[it.id]?.takeIf { r -> r.found }?.unitPrice ?: return null }
    val first = memberPrices.first()
    return first.takeIf { memberPrices.all { samePrice(it, first) } }
}
