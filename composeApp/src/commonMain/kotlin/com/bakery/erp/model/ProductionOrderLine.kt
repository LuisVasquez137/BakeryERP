package com.bakery.erp.model

data class ProductionOrderLine(
    val id: String = "",
    val productId: String = "",
    val productName: String = "",
    val requestedQuantity: Double = 0.0,
    val productUnit: String = "",
    /**
     * Desglose de quién contribuyó a esta línea: una `CustomerLineRef` por cada
     * línea de pedido de revendedor que se sumó a este producto. La diferencia
     * entre `requestedQuantity` y la suma de los `quantity` de los refs es la
     * cantidad que la encargada pidió por sí misma.
     */
    val customerLineRefs: List<CustomerLineRef> = emptyList()
)

/**
 * Referencia a una línea de pedido de revendedor que se consolidó en una línea
 * de PO. Permite mostrar al panadero / a la encargada el desglose de cuánto
 * de la cantidad total viene de cada revendedor.
 */
data class CustomerLineRef(
    val customerOrderId: String = "",
    val customerName: String = "",
    val customerOrderLineId: String = "",
    val quantity: Double = 0.0,
    val customSpec: String = "",
)
