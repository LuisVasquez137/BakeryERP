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
    val notes: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

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
