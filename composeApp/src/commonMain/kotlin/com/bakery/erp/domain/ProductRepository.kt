package com.bakery.erp.domain

import com.bakery.erp.model.Product

interface ProductRepository {
    suspend fun getProducts(companyId: String): List<Product>
    suspend fun addProduct(companyId: String, product: Product)
    suspend fun updateProduct(companyId: String, product: Product)
    suspend fun deleteProduct(companyId: String, productId: String)
    suspend fun uploadImage(imageBytes: ByteArray, productId: String): String
}
