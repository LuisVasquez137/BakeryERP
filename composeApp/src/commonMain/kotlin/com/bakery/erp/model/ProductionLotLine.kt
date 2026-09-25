package com.bakery.erp.model

data class ProductionLotLine(
    val id: String = "",
    val productId: String = "",
    val productName: String = "",
    val productUnit: String = "",
    val rawWeightGrams: Double? = null,
    val plannedQuantity: Double = 0.0,
    val producedQuantity: Double = 0.0,
    /**
     * true una vez que el panadero confirmó explícitamente el conteo real de
     * esta línea (botón "Guardar todo" en `RegisterProductionSheet`), aunque
     * no haya cambiado el valor prellenado. Bloquea "Terminar lote y
     * descontar stock" mientras falte alguna masa por confirmar — ver
     * `ProductionViewModel.saveProducedQuantities`.
     */
    val producedCountConfirmed: Boolean = false,
    /**
     * Unidades que se amasaron y se PERDIERON: se quemaron, se cayeron, salieron mal.
     *
     * No baja [producedQuantity] ni toca los pedidos — las sucursales reciben lo suyo
     * si el panadero repuso lo perdido. Lo único que cambia es el consumo de insumos:
     * esa masa se gastó igual. Ver [kneadedQuantity] y `ProductionAdjustment.kt`.
     *
     * **No entra en la lista de lo que hay que amasar**, a diferencia de
     * [extraQuantity]: lo quemado ya se amasó una vez, y sumarlo mostraría una amasada
     * que nunca se hace de un solo golpe.
     */
    val wasteQuantity: Double = 0.0,
    val wasteReason: String = "",
    val wasteNotes: String = "",
    /**
     * Unidades amasadas de MÁS, fuera de lo que pidieron las sucursales: el encargo de
     * 100 pirujos que llegó a media mañana.
     *
     * Consumen insumos y entran al stock terminado de la planta al cerrar el lote, por
     * el mismo camino que [plantRetainedQuantity] (ver [plantIngestQuantity]). No van
     * en ninguna nota de despacho, porque ésas se construyen desde los pedidos fuente.
     *
     * **Sí entra en la lista de lo que hay que amasar**: todavía no se hizo.
     */
    val extraQuantity: Double = 0.0,
    val extraReason: String = "",
    val extraNotes: String = "",
    val recipeId: String = "",
    val recipeName: String = "",
    /**
     * Unidades que se hornean pero **no se despachan**: quedan en la planta.
     *
     * Aparece cuando la receta no llegaba al mínimo de la amasadora y la
     * encargada decidió hornear el mínimo: el excedente que no repartió entre
     * las sucursales solicitantes se queda acá y entra al stock terminado de la
     * planta al completar el lote (`AJUSTE` en el Kardex de terminado).
     *
     * `plannedQuantity` incluye estas unidades; las notas de despacho no, porque
     * se construyen desde los pedidos fuente.
     */
    val plantRetainedQuantity: Double = 0.0,
    // Esponja opcional: si el panadero declara usar esponja, se descuenta del batch elegido
    // y los ingredientes que aporta se restan de la masa al cerrar el lote.
    val spongeBatchId: String = "",
    val spongeBatchName: String = "",
    val spongeGramsUsed: Double = 0.0,
    /**
     * true una vez que el panadero confirmó el uso de esta esponja y ya se
     * descontó del lote de preparación (ver `ProductionViewModel.confirmSpongeForGroup`).
     * Evita que `completeLot` la descuente otra vez y bloquea la edición en la
     * UI hasta que se use "Quitar esponja".
     */
    val spongeConsumed: Boolean = false,
    // Snapshot de los adornos del producto al crear la línea, con el batch elegido.
    val decorationsSnapshot: List<ProductionLotDecoration> = emptyList(),
    /**
     * Snapshot de [Product.doughFillings] al crear la línea (ver [DoughFilling]).
     * A diferencia de [decorationsSnapshot], esto no se descuenta de un batch —
     * se suma como demanda extra a la masa de `recipeId` de cada relleno al
     * completar el lote (misma receta, misma amasada; ver `completeLot`).
     */
    val doughFillingsSnapshot: List<DoughFilling> = emptyList(),
    /**
     * Notas por encargo de cliente externo. Permite que el panadero vea
     * "Doña María: 5 pirujos grandes" sin perder la consolidación de cantidades.
     * Se llena cuando la línea proviene total o parcialmente de un [CustomerOrder].
     */
    val customerSpecs: List<CustomerLineSpec> = emptyList(),
    /**
     * Desglose de cuánto de esta línea le corresponde a cada [ProductionOrder]
     * fuente, cuando el lote viene de un reparto por turnos
     * (`ProductionViewModel.saveShiftSplitLots`). Vacío para lotes del flujo
     * normal (un solo turno) — ahí `createDispatchNotesForLot` sigue usando el
     * pedido completo, como antes. Con reparto por turnos, en cambio, dos lotes
     * distintos comparten `sourceOrderIds`, y sin este desglose no hay forma de
     * saber cuánto de la cantidad pedida por CADA sucursal produjo este turno en
     * particular.
     */
    val sourceOrderAllocations: List<SourceOrderAllocation> = emptyList(),
    /**
     * Línea que viene de un pedido especial ([ProductionOrderLine.isSpecial]):
     * la misma masa de siempre en otra figura o tamaño, pedida por una sucursal
     * para un encargo puntual. No hay producto de catálogo detrás, así que
     * [recipeId] y [rawWeightGrams] vienen copiados de la línea del pedido y no
     * de `productsById` — todo lo demás (receta escalada, mínimo de amasada,
     * descuento en `completeLot`) funciona igual que cualquier otra línea.
     */
    val isSpecial: Boolean = false,
    /** Foto de referencia que mandó la sucursal, para que el panadero vea la
     *  figura pedida en vez de deducirla de la descripción. */
    val specialPhotoUrl: String = ""
)

/** Cuánto de una [ProductionLotLine] le corresponde a un pedido fuente específico. */
data class SourceOrderAllocation(
    val orderId: String = "",
    val branchName: String = "",
    val quantity: Double = 0.0
)

/** Marca de cliente sobre una línea consolidada de lote — informativo. */
data class CustomerLineSpec(
    val customerOrderId: String = "",
    val customerName: String = "",
    val quantity: Double = 0.0,
    val customSpec: String = "",
    /** Tipo del [CustomerOrder] de origen: RESELLER | RETAIL_SPECIAL | CAKE. Permite
     *  pintar el badge correcto al panadero sin tener que cargar el pedido. */
    val customerType: String = "",
)

