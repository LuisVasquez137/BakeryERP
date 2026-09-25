package com.bakery.erp.domain

import com.bakery.erp.model.Product

interface ProductRepository {
    suspend fun getProducts(companyId: String): List<Product>
    suspend fun addProduct(companyId: String, product: Product)
    suspend fun updateProduct(companyId: String, product: Product)
    suspend fun deleteProduct(companyId: String, productId: String)

    /**
     * Actualiza solo el peso en crudo de varios productos a la vez.
     *
     * Escritura parcial y en lote: la pantalla de carga rápida edita decenas de
     * productos de corrido y un `updateProduct` completo por cada uno
     * reescribiría todos sus campos (con el riesgo de pisar algo editado en otro
     * dispositivo) además de ser mucho más lento.
     *
     * [unit] ("oz" o "g") es la unidad en la que el panadero tecleó en esta
     * pasada — se guarda junto al gramaje para que el detalle/formulario del
     * producto lo vuelva a mostrar en esa misma unidad. Antes solo se
     * escribía `rawWeightGrams`, así que un peso cargado en onzas quedaba con
     * `rawWeightUnit` sin tocar (o en su default "g") y se veía redondeado a
     * gramos enteros en cualquier otra pantalla — el dato guardado era
     * exacto, pero parecía impreciso porque se mostraba en la unidad
     * equivocada.
     */
    suspend fun updateRawWeights(companyId: String, gramsByProductId: Map<String, Double>, unit: String)

    /**
     * Reescribe solo las presentaciones de compra de un producto de reventa (y el costo de
     * referencia que se deriva de ellas). Escritura parcial: la llama la captura de la factura
     * —al agregarle una presentación a un proveedor o al registrar un precio nuevo— y un
     * `updateProduct` completo desde ahí pisaría lo que alguien editó del producto mientras
     * tanto.
     */
    suspend fun updatePurchaseFormats(companyId: String, product: Product)
    suspend fun uploadImage(imageBytes: ByteArray, productId: String): String
}
