package com.bakery.erp.model

data class PriceList(
    val id: String = "",
    val name: String = "",
    val type: String = PriceListType.WHOLESALE,
    val isDefault: Boolean = false,
    val active: Boolean = true,
    /**
     * Factor multiplicador sobre `Product.salePrice` (precio al cliente final) usado
     * cuando no hay un `ProductPrice` explícito para esta lista. `0.80` = 80% del retail.
     * `null` = no aplica fallback automático; sin override explícito el precio queda
     * como "no definido" y bloquea el cierre del despacho.
     *
     * Solo tiene sentido para tipos WHOLESALE y CUSTOM. Se ignora en RETAIL.
     */
    val priceFactor: Double? = null,
    /**
     * Escalones "precio público → precio de la lista" (Q0.50 → Q0.40, Q1.50 → Q1.25). Le ganan
     * al [priceFactor]: el dueño piensa los precios del revendedor así, por escalón, y un solo
     * porcentaje no los da (Q0.40 es 80% de Q0.50; Q1.25 es 83.3% de Q1.50). Un producto con
     * precio fijo propio en la lista le gana a los dos. Ver [listRulePrice].
     */
    val priceSteps: List<PriceStep> = emptyList(),
    val notes: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/** Todo producto que se vende al público a [publicPrice] sale a [listPrice] en la lista. */
data class PriceStep(val publicPrice: Double, val listPrice: Double)

/** Dos precios son el mismo si no difieren ni en medio centavo (los Double no comparan exacto). */
fun samePrice(a: Double, b: Double): Boolean = kotlin.math.abs(a - b) < 0.005

/**
 * Precio que la regla general de la lista le da a un producto de precio público [publicPrice]:
 * su escalón si tiene, si no el porcentaje, redondeado a centavos. Null = la lista no le da
 * precio. No mira el precio fijo del producto: ese se consulta aparte y le gana a esto.
 *
 * La lista de cliente final no tiene regla: su precio es el público.
 */
fun PriceList.listRulePrice(publicPrice: Double): Double? {
    if (type == PriceListType.RETAIL || publicPrice <= 0) return null
    priceSteps.firstOrNull { samePrice(it.publicPrice, publicPrice) && it.listPrice > 0 }
        ?.let { return roundToCents(it.listPrice) }
    val factor = priceFactor ?: return null
    return if (factor > 0) roundToCents(publicPrice * factor) else null
}

/** Una caja no puede cobrar medio centavo: 85% de Q0.50 da 0.425. */
fun roundToCents(v: Double): Double = kotlin.math.round(v * 100) / 100

object PriceListType {
    const val RETAIL    = "RETAIL"
    const val WHOLESALE = "WHOLESALE"
    const val CUSTOM    = "CUSTOM"

    fun label(type: String) = when (type) {
        RETAIL    -> "Cliente final"
        WHOLESALE -> "Mayorista"
        CUSTOM    -> "Especial"
        else      -> "Desconocido"
    }

    val all = listOf(RETAIL, WHOLESALE, CUSTOM)
}
