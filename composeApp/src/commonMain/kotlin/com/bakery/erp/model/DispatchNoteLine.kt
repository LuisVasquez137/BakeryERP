package com.bakery.erp.model

data class DispatchNoteLine(
    val id: String = "",
    val productId: String = "",
    val productName: String = "",
    val productUnit: String = "",
    val requestedQuantity: Double = 0.0,
    val packedQuantity: Double = 0.0,
    val packedReason: String? = null,
    val receivedQuantity: Double = 0.0,
    val receivedReason: String? = null,
    val discrepancyType: String? = null,
    val unitWholesalePrice: Double = 0.0,
    /** Origen del precio resuelto al crear la línea: [PriceSourceTag]. */
    val priceSource: String = PriceSourceTag.NONE,
    /**
     * Timestamp (ms epoch) cuando la encargada de despacho marcó la cantidad como ya contada
     * en la matriz de empaque. Null = no contada. Persiste para que el check no se pierda al
     * salir/volver de la app. Sólo se usa mientras la nota está en estado DRAFT.
     */
    val lineCheckedAt: Long? = null,
    /**
     * Timestamp (ms epoch) cuando la encargada confirmó visualmente la llegada de este producto
     * al recibir al repartidor (checklist de llegada, sin cantidades). Null = aún no confirmado.
     * Es un sello de tipo (llegó/no llegó), distinto del conteo final con cantidades.
     */
    val lineArrivedConfirmedAt: Long? = null,
    /**
     * Línea de un pedido especial (ver [ProductionOrderLine.isSpecial]): pieza
     * fuera de catálogo, hecha por encargo. Se marca para poder mostrarla como
     * tal en empaque y recepción — la encargada que cuenta necesita saber que
     * ese pan no es el de siempre.
     */
    val isSpecial: Boolean = false,
    /**
     * Peso en crudo de UNA pieza del especial, en gramos (snapshot de
     * [ProductionOrderLine.specialRawWeightGrams]).
     *
     * Se copia acá en vez de resolverse contra el pedido: la nota es un documento
     * histórico y el peso es lo único que distingue "el pirujo del doble" de un
     * pirujo normal a la hora de empacar y de contar en la sucursal. Se muestra en
     * onzas, que es como se pidió.
     */
    val specialRawWeightGrams: Double = 0.0,
    /**
     * Masa del especial (snapshot de [ProductionOrderLine.specialRecipeId] /
     * `specialRecipeName`).
     *
     * Sin esto el especial se despacha suelto: el agrupado por masa de las
     * pantallas de despacho resuelve la receta contra el catálogo de productos
     * (`recipeRefByProductId`), y el `productId` de un especial es sintético — no
     * está ahí, así que caía siempre en "Otros productos", separado justamente de
     * la masa con la que se horneó y con la que viaja.
     */
    val specialRecipeId: String = "",
    val specialRecipeName: String = ""
)

/** Mismo conjunto que `domain.PriceSource` pero como String para serializar. */
object PriceSourceTag {
    const val PRODUCT_OVERRIDE = "PRODUCT_OVERRIDE"
    const val LIST_FACTOR      = "LIST_FACTOR"
    const val NONE             = "NONE"
    /**
     * Pedido especial: pieza fuera de catálogo, sin lugar en ninguna lista de
     * precios. Lleva el precio que declaró la encargada al pedirlo (o 0 si no
     * lo declaró) y, a diferencia de [NONE], **no bloquea** el cierre de la
     * nota: un encargo de 5 panes no puede dejar parado el reparto del día.
     */
    const val SPECIAL_ORDER    = "SPECIAL_ORDER"
}

object DiscrepancyType {
    const val SHORT   = "SHORT"     // llegó menos de lo empacado
    const val DAMAGED = "DAMAGED"   // llegó dañado
    const val EXTRA   = "EXTRA"     // llegó más de lo empacado

    fun label(type: String?) = when (type) {
        SHORT   -> "Faltante"
        DAMAGED -> "Dañado"
        EXTRA   -> "Sobrante"
        else    -> ""
    }

    val all = listOf(SHORT, DAMAGED, EXTRA)
}
