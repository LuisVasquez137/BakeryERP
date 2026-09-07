package com.bakery.erp.model

/**
 * Relleno de masa cruda de OTRA receta, ya amasada — no un [ProductDecoration].
 *
 * Caso real: el Pan de Capa es masa de Capa por fuera con una bolita de masa
 * Suave adentro. La bolita no sale de un lote de preparación aparte (como una
 * concha o un relleno comprado): es literalmente masa de Suave, la misma que
 * también se corta para hacer Pan Suave normal. El panadero amasa Suave UNA
 * vez y de ahí saca tanto los Pan Suave como las bolitas para el Pan de Capa.
 *
 * Por eso [rawWeightGrams] no se descuenta de un batch — se SUMA a la demanda
 * total de masa de [recipeId] (junto con los productos que sí salen de esa
 * receta), para que el mínimo de amasada, la receta escalada y el descuento
 * de insumos de esa masa ya incluyan lo que hace falta para este relleno. Ver
 * `ProductionViewModel` — el total de una masa ya no es solo la suma de sus
 * propios productos, sino también los `doughFillings` de productos de OTRAS
 * masas que apunten a ella.
 */
data class DoughFilling(
    val recipeId: String = "",
    val recipeName: String = "",
    /** Peso en crudo de la bolita/porción, en gramos — independiente del
     *  [Product.rawWeightGrams] propio (la envoltura sale de otra masa). */
    val rawWeightGrams: Double = 0.0
)
