package com.bakery.erp.model

/**
 * Búsqueda del catálogo del proveedor al capturar su factura.
 *
 * Quien captura lee el papel, y el papel no dice "Azúcar" ni "Coca Cola 354 Ml": dice "Azúcar
 * blanca Don Justo cabal" o "CC LATA 354ML 24X". Buscar solo por el nombre del catálogo obligaba
 * a traducir de memoria cada renglón. Por eso se busca también por el nombre que le da ESTE
 * proveedor en la descripción de sus presentaciones.
 */

/**
 * Nombres con que [supplierId] factura este ítem: la descripción de sus presentaciones (en un
 * insumo es justamente eso, "Azúcar blanca Don Justo cabal") y, en reventa, la descripción
 * aparte del empaque (`supplierItemName`). Sin repetir ni vacíos.
 */
fun List<PurchaseFormat>.invoiceNamesFor(supplierId: String): List<String> =
    formatsOf(supplierId)
        .flatMap { listOf(it.supplierItemName.trim(), it.description.trim()) }
        .filter { it.isNotEmpty() }
        .distinct()

/**
 * Solo el nombre con que ESTE proveedor factura el ítem, sin el empaque.
 *
 * Es lo que se dibuja en la fila del catálogo al capturar la factura: ahí el empaque ya va en
 * su propio renglón, y repetirlo arriba en cursiva no agrega nada. Se separa de
 * [invoiceNamesFor] —que sí junta los dos— porque buscar y mostrar quieren cosas distintas:
 * al buscar conviene que enganche con lo que sea, al mostrar conviene no decir dos veces lo
 * mismo.
 */
fun List<PurchaseFormat>.supplierItemNamesFor(supplierId: String): List<String> =
    formatsOf(supplierId)
        .map { it.supplierItemName.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

/**
 * Las presentaciones que aplican a [supplierId]. Las que no traen proveedor entran también:
 * son las de antes de que la presentación tuviera dueño, y esconderlas dejaría al insumo sin
 * fila en la factura de nadie.
 */
private fun List<PurchaseFormat>.formatsOf(supplierId: String): List<PurchaseFormat> =
    filter { it.supplierId == supplierId || it.supplierId.isEmpty() }

/**
 * `true` si [query] aparece en el nombre del catálogo o en alguno de los nombres de factura del
 * proveedor. Sin distinguir mayúsculas ni tildes, y por palabras sueltas en cualquier orden:
 * "don justo azucar" encuentra "Azúcar blanca Don Justo cabal".
 */
fun matchesPurchaseSearch(
    catalogName: String,
    formats: List<PurchaseFormat>,
    supplierId: String,
    query: String
): Boolean {
    val words = normalizeForSearch(query).split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return true
    val haystacks = (listOf(catalogName) + formats.invoiceNamesFor(supplierId)).map(::normalizeForSearch)
    return haystacks.any { h -> words.all { h.contains(it) } }
}

internal fun normalizeForSearch(text: String): String = buildString {
    text.lowercase().forEach { c ->
        append(
            when (c) {
                'á', 'à', 'ä', 'â' -> 'a'
                'é', 'è', 'ë', 'ê' -> 'e'
                'í', 'ì', 'ï', 'î' -> 'i'
                'ó', 'ò', 'ö', 'ô' -> 'o'
                'ú', 'ù', 'ü', 'û' -> 'u'
                else -> c
            }
        )
    }
}
