package com.bakery.erp.model

/**
 * Orden y búsqueda de la lista de proveedores.
 *
 * Firestore devuelve los documentos en orden arbitrario, así que la lista salía distinta entre
 * cargas y no había dónde apoyar la vista para recorrerla. El orden canónico es alfabético por
 * empresa, y la búsqueda es lo que hace usable un catálogo de más de una pantalla.
 */

/**
 * Alfabético por empresa, sin distinguir mayúsculas ni tildes: "Ávila" va entre "Ayala" y
 * "Axila", no al final de la lista, que es donde lo manda comparar por el código del carácter.
 * Desempata por vendedor, para que dos sucursales del mismo distribuidor salgan siempre en el
 * mismo orden. Un proveedor sin empresa se ordena por su vendedor — es el único nombre que tiene.
 */
fun List<Supplier>.sortedForList(): List<Supplier> =
    sortedWith(
        compareBy(
            { normalizeForSearch(it.company.ifBlank { it.salesperson }.trim()) },
            { normalizeForSearch(it.salesperson.trim()) }
        )
    )

/**
 * `true` si [query] aparece en el nombre de la empresa o en el del vendedor. Sin distinguir
 * mayúsculas ni tildes, y por palabras sueltas en cualquier orden **a través de los dos campos**:
 * "azucarera mario" encuentra a Mario de Azucarera del Sur, que es como uno se acuerda de un
 * proveedor cuando lo busca. Query en blanco = no filtra.
 */
fun matchesSupplierSearch(supplier: Supplier, query: String): Boolean {
    val words = normalizeForSearch(query).split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return true
    val haystack = normalizeForSearch("${supplier.company} ${supplier.salesperson}")
    return words.all { haystack.contains(it) }
}
