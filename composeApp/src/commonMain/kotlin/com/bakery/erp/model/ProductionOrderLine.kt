package com.bakery.erp.model

import kotlin.random.Random
import kotlin.time.Clock

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
    val customerLineRefs: List<CustomerLineRef> = emptyList(),
    /**
     * Unidades de esta línea que vienen del ajuste automático por lluvia
     * probable (ver `withRainBump`), snapshot al prellenar el pedido. Se
     * resta al guardar la muestra histórica del patrón para que el ajuste
     * no se vuelva la nueva "normalidad" semana tras semana — ver
     * `FirebaseProductionRepository.recordOrderInPattern`.
     */
    val rainBumpUnits: Int = 0,
    /**
     * Cuántas de [requestedQuantity] necesita la sucursal en el **primer viaje**
     * (el pan que sale temprano). `null` = "repartilo como quieras", que es el
     * caso normal y no le agrega ninguna fricción al pedido de todos los días.
     *
     * Es un solo campo a propósito: el segundo viaje se deriva restando
     * (`requestedQuantity - earlyShiftQuantity`), así los dos viajes nunca pueden
     * sumar algo distinto de lo pedido — mismo criterio de conservación que
     * `rebalanceShiftSplit`.
     *
     * Está en vocabulario de **viajes y no de turnos** porque la encargada de
     * sucursal piensa "el camión de la mañana", no "el turno de noche"; el puente
     * a los turnos de la planta es `ProductionShift.dispatchOrder`.
     *
     * Solo tiene sentido en pedidos sin turno hacia una planta de 2 turnos (los
     * que pasan por el reparto). En cualquier otro caso se ignora.
     */
    val earlyShiftQuantity: Double? = null,
    /**
     * Por qué necesita esa cantidad temprano ("encargo doña María 7am"). La
     * encargada de planta necesita el motivo para decidir si lo respeta cuando la
     * amasada no alcanza — un número sin explicación no se puede negociar.
     */
    val earlyShiftNote: String = "",
    /**
     * Pedido especial: una pieza que NO existe en el catálogo — la misma masa
     * que ya se hace, pero en otra figura o tamaño ("un pirujo del doble").
     *
     * No se crea un [Product] para esto a propósito: es un encargo de un día,
     * y darle de alta lo metería en el POS, en las listas de precios y en la
     * carga de pesos para siempre. En vez de eso la línea se basta sola: trae
     * la masa de la que sale ([specialRecipeId]) y el peso en crudo de la
     * pieza ([specialRawWeightGrams]), que son exactamente los dos datos que
     * `ProductionLotLine` necesita para la receta escalada, el mínimo de
     * amasada y el descuento de insumos al cerrar el lote.
     *
     * El `productId` de una línea especial es un id sintético ([newSpecialProductId])
     * que la mantiene separada de cualquier otra línea a lo largo de todo el
     * flujo (consolidación del lote, reparto por turnos, nota de despacho), y
     * `productName` lleva la descripción que escribió la encargada.
     */
    val isSpecial: Boolean = false,
    /** Masa de la que sale la pieza especial. Es lo que la ubica en su grupo de
     *  masa y lo que permite escalar la receta — sin esto no hay nada que amasar. */
    val specialRecipeId: String = "",
    val specialRecipeName: String = "",
    /** Peso en crudo de UNA pieza, en gramos — mismo significado que
     *  [Product.rawWeightGrams], solo que declarado en el pedido. */
    val specialRawWeightGrams: Double = 0.0,
    /** Foto de referencia opcional (Firebase Storage). El panadero la ve en la
     *  lista de producción: "así la quieren". */
    val specialPhotoUrl: String = "",
    /**
     * Precio unitario que la encargada declaró para el especial, o 0 si no lo
     * sabe todavía. Un especial no está en ninguna lista de precios, así que su
     * línea de despacho se marca [PriceSourceTag.SPECIAL_ORDER] y queda exenta
     * del bloqueo "hay líneas sin precio" — un encargo de 5 panes no puede
     * dejar detenido el reparto del día entero.
     */
    val specialUnitPrice: Double = 0.0
)

/** Prefijo de los `productId` sintéticos de los pedidos especiales. */
const val SPECIAL_PRODUCT_ID_PREFIX = "esp_"

/**
 * Id sintético para una línea especial. No es un producto de catálogo, pero
 * todo el flujo de producción agrupa por `productId`, así que necesita uno
 * propio y estable: dos especiales distintos (aunque salgan de la misma masa)
 * nunca deben fundirse en una sola línea de lote.
 */
fun newSpecialProductId(): String =
    SPECIAL_PRODUCT_ID_PREFIX +
        Clock.System.now().toEpochMilliseconds().toString(36) +
        "_" + Random.nextInt(46_656, 1_679_615).toString(36)

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
