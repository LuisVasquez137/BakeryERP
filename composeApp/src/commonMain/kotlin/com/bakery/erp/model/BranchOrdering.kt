package com.bakery.erp.model

/**
 * Aplica el orden configurado por la planta ([Branch.dispatchBranchOrder]) a una
 * lista de elementos que tienen sucursal destino.
 *
 * Lo que no está en la lista va después, alfabético por nombre. Eso es lo que
 * permite guardar solo el orden explícito: una sucursal nueva aparece al final
 * en vez de desaparecer, y no hay que reconfigurar nada al darla de alta.
 *
 * Genérico sobre el elemento (y no una función que reciba `List<DispatchNote>`)
 * porque el mismo orden lo van a querer la matriz de tablet y las pestañas de
 * teléfono, que hoy comparten el tipo pero no tienen por qué seguir haciéndolo.
 */
fun <T> applyBranchOrder(
    items: List<T>,
    order: List<String>,
    branchIdOf: (T) -> String,
    branchNameOf: (T) -> String
): List<T> {
    val position = order.withIndex().associate { (i, id) -> id to i }
    return items.sortedWith(
        compareBy(
            { position[branchIdOf(it)] ?: Int.MAX_VALUE },
            { branchNameOf(it).lowercase() }
        )
    )
}
