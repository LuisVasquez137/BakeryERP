package com.bakery.erp.data

import com.bakery.erp.model.PurchaseFormat
import dev.gitlive.firebase.firestore.DocumentSnapshot

/**
 * Presentaciones de compra de un producto de reventa, como arreglos paralelos en el
 * documento del producto.
 *
 * Arreglos y no lista de mapas por el mismo bug que los adornos: GitLive no tiene serializer
 * para `Any`, y una lista de objetos guardada nunca vuelve a leerse. Y en el documento y no en
 * subcolección porque `getProducts` lo lee media app y sería una lectura extra por producto.
 *
 * Compartido entre el repositorio de Android y el de web: son dos implementaciones del mismo
 * documento, y si cada una nombrara los campos por su cuenta bastaría un typo para que lo
 * guardado en la tablet no se viera en el navegador.
 */
internal fun productPurchaseFormatFields(formats: List<PurchaseFormat>): Map<String, Any?> = mapOf(
    "purchaseFormatIds" to formats.map { it.id },
    "purchaseFormatDescriptions" to formats.map { it.description },
    "purchaseFormatUnits" to formats.map { it.unit },
    "purchaseFormatSupplierIds" to formats.map { it.supplierId },
    "purchaseFormatSupplierNames" to formats.map { it.supplierName },
    "purchaseFormatUnitsPerPackage" to formats.map { it.unitsPerPackage },
    "purchaseFormatPackageCosts" to formats.map { it.packageCost },
    "purchaseFormatDefaults" to formats.map { it.isDefault },
    "purchaseFormatSupplierItemNames" to formats.map { it.supplierItemName }
)

internal fun DocumentSnapshot.readProductPurchaseFormats(): List<PurchaseFormat> = runCatching {
    val ids = get<List<String>?>("purchaseFormatIds") ?: emptyList()
    val descriptions = get<List<String>?>("purchaseFormatDescriptions") ?: emptyList()
    val units = get<List<String>?>("purchaseFormatUnits") ?: emptyList()
    val supplierIds = get<List<String>?>("purchaseFormatSupplierIds") ?: emptyList()
    val supplierNames = get<List<String>?>("purchaseFormatSupplierNames") ?: emptyList()
    val perPackage = get<List<Double>?>("purchaseFormatUnitsPerPackage") ?: emptyList()
    val costs = get<List<Double>?>("purchaseFormatPackageCosts") ?: emptyList()
    val defaults = get<List<Boolean>?>("purchaseFormatDefaults") ?: emptyList()
    val invoiceNames = runCatching { get<List<String>?>("purchaseFormatSupplierItemNames") }.getOrNull() ?: emptyList()
    ids.indices.map { i ->
        PurchaseFormat(
            id = ids[i],
            description = descriptions.getOrElse(i) { "" },
            unit = units.getOrElse(i) { "" },
            supplierId = supplierIds.getOrElse(i) { "" },
            supplierName = supplierNames.getOrElse(i) { "" },
            unitsPerPackage = perPackage.getOrElse(i) { 1.0 },
            packageCost = costs.getOrElse(i) { 0.0 },
            isDefault = defaults.getOrElse(i) { false },
            supplierItemName = invoiceNames.getOrElse(i) { "" }
        )
    }
}.getOrElse { emptyList() }
