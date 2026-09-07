package com.bakery.erp.model

data class ProductPrice(
    val id: String = "",
    val productId: String = "",
    val priceListId: String = "",
    val priceListName: String = "",
    val unitPrice: Double = 0.0,
    val updatedAt: Long = 0L
)

fun priceDocId(productId: String, priceListId: String) = "${productId}_$priceListId"
