package com.bakery.erp.data

import com.bakery.erp.domain.ProductRepository
import com.bakery.erp.model.Product

class NoOpProductRepository : ProductRepository {
    override suspend fun getProducts(companyId: String): List<Product> = emptyList()
    override suspend fun addProduct(companyId: String, product: Product) = Unit
    override suspend fun updateProduct(companyId: String, product: Product) = Unit
    override suspend fun deleteProduct(companyId: String, productId: String) = Unit
    override suspend fun updateRawWeights(
        companyId: String,
        gramsByProductId: Map<String, Double>,
        unit: String
    ) = Unit
    override suspend fun uploadImage(imageBytes: ByteArray, productId: String): String = ""
}
