package com.bakery.erp.domain

import com.bakery.erp.model.PurchaseConcept

/**
 * Catálogo de cosas que se compran seguido y no llevan inventario. Ver [PurchaseConcept].
 *
 * Es deliberadamente chico: se lee entero de una vez (son decenas de nombres, no miles) y se
 * escribe solo al capturar una factura. No tiene filtros en el servidor porque no hay volumen
 * que los justifique, y filtrar en memoria evita el problema de siempre — un `where` sobre un
 * campo que los documentos viejos no tienen deja fuera justo lo más antiguo.
 */
interface PurchaseConceptRepository {
    suspend fun getConcepts(companyId: String): List<PurchaseConcept>

    /** Alta o actualización completa. Devuelve el concepto con su id ya asignado. */
    suspend fun saveConcept(companyId: String, concept: PurchaseConcept): PurchaseConcept

    /**
     * Escritura PARCIAL de lo que deja una compra: el proveedor al que se le compró, el
     * precio pagado y la fecha. Parcial y no `set()` porque quien registra la factura no
     * tiene el concepto completo en la mano, y reescribirlo entero le borraría el nombre o la
     * unidad a quien lo esté editando desde el catálogo al mismo tiempo.
     */
    suspend fun recordPurchase(
        companyId: String,
        conceptId: String,
        supplierIds: List<String>,
        lastUnitCost: Double,
        lastPurchaseDate: Long
    )

    suspend fun deleteConcept(companyId: String, conceptId: String)
}
