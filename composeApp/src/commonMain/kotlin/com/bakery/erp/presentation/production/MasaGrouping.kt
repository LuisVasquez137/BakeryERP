package com.bakery.erp.presentation.production

/**
 * Agrupado por masa, compartido por todas las pantallas del flujo de producción.
 *
 * Una receta genera varios productos (de la hojaldre salen barcas, cuadrados y
 * milhojas). Verlos juntos importa en cada paso del flujo por una razón distinta:
 * al pedir, para no buscarlos desperdigados en una lista alfabética; al
 * consolidar, para ver cuánta masa de cada tipo se va a mojar; al hornear,
 * porque la amasada es una sola para todo el grupo.
 *
 * Vive en un solo lugar para que las cuatro pantallas no se desincronicen.
 */
internal data class MasaGroup<T>(
    /** `recipeId`, o [NO_RECIPE_KEY] para lo que no sale de ninguna masa. */
    val key: String,
    val title: String,
    val items: List<T>
)

internal const val NO_RECIPE_KEY = "__sin_masa__"
internal const val NO_RECIPE_TITLE = "Otros productos"

/**
 * Agrupa [items] por la masa de la que sale cada uno.
 *
 * Los grupos se ordenan por [groupOrderIndex] (menor primero) y, a igualdad, por
 * nombre. Dentro de cada grupo se usa [itemOrderIndex] con el mismo criterio.
 * Sin esos parámetros el resultado es alfabético puro.
 *
 * Lo que no tiene receta —productos de reventa, o alguno al que le falta
 * declararse en "Productos que genera"— cae en un grupo aparte **al final**: no
 * es un error, pero tampoco debe mezclarse con las masas ni encabezar la lista.
 */
internal fun <T> groupByMasa(
    items: List<T>,
    recipeIdOf: (T) -> String?,
    recipeNameOf: (T) -> String?,
    nameOf: (T) -> String,
    groupOrderIndex: (recipeId: String) -> Int = { Int.MAX_VALUE },
    itemOrderIndex: (T) -> Int = { Int.MAX_VALUE }
): List<MasaGroup<T>> {
    val grouped = items.groupBy { recipeIdOf(it)?.takeIf { id -> id.isNotEmpty() } }

    val withRecipe = grouped
        .filterKeys { it != null }
        .map { (recipeId, groupItems) ->
            MasaGroup(
                key = recipeId!!,
                title = groupItems.firstNotNullOfOrNull { recipeNameOf(it)?.takeIf(String::isNotBlank) }
                    ?: "Masa sin nombre",
                items = groupItems.sortedWith(compareBy({ itemOrderIndex(it) }, { nameOf(it) }))
            )
        }
        .sortedWith(compareBy({ groupOrderIndex(it.key) }, { it.title }))

    val withoutRecipe = grouped[null].orEmpty()
    return if (withoutRecipe.isEmpty()) withRecipe
    else withRecipe + MasaGroup(
        key = NO_RECIPE_KEY,
        title = NO_RECIPE_TITLE,
        items = withoutRecipe.sortedBy { nameOf(it) }
    )
}

/**
 * Traduce un orden configurado **por producto** a un orden **por masa**.
 *
 * Las pantallas guardan la secuencia como lista de `productId`
 * ([com.bakery.erp.model.Branch.productSortOrder]). Al pasar a vista agrupada, la
 * posición de una masa es la del primero de sus productos en esa lista: si la
 * encargada sube "Barcas" al principio, sube toda la hojaldre. Es lo que se
 * espera al ver la lista ya agrupada, y evita tener que reconfigurar todo o
 * inventar un segundo campo solo para el orden de las masas.
 */
internal fun recipeOrderIndexFrom(
    productSortOrder: List<String>,
    recipeIdOfProduct: (productId: String) -> String?
): Map<String, Int> {
    val result = mutableMapOf<String, Int>()
    productSortOrder.forEachIndexed { index, productId ->
        val recipeId = recipeIdOfProduct(productId)?.takeIf { it.isNotEmpty() } ?: return@forEachIndexed
        // Solo la primera aparición: define la posición de la masa entera.
        // (`putIfAbsent` es solo JVM — no existe en commonMain.)
        if (recipeId !in result) result[recipeId] = index
    }
    return result
}
