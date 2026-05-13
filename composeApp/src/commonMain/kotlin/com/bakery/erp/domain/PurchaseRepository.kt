package com.bakery.erp.domain

import com.bakery.erp.model.Purchase
import com.bakery.erp.model.PurchaseLine

interface PurchaseRepository {
    suspend fun getPurchases(companyId: String): List<Purchase>
    suspend fun getPurchase(companyId: String, purchaseId: String): Purchase?
    suspend fun savePurchase(companyId: String, purchase: Purchase, lines: List<PurchaseLine>)
    suspend fun getLines(companyId: String, purchaseId: String): List<PurchaseLine>
    suspend fun updatePurchaseStatus(companyId: String, purchaseId: String, newStatus: String)
}
