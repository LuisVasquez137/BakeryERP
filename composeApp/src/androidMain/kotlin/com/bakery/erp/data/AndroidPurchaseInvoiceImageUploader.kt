package com.bakery.erp.data

import com.bakery.erp.domain.PurchaseInvoiceImageUploader
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.FirebaseStorage

class AndroidPurchaseInvoiceImageUploader(
    private val storage: FirebaseStorage
) : PurchaseInvoiceImageUploader {
    override suspend fun uploadInvoicePhoto(
        bytes: ByteArray,
        companyId: String,
        purchaseId: String
    ): String {
        val ref = storage.reference.child("purchaseInvoices/$companyId/$purchaseId.jpg")
        ref.putData(Data(bytes))
        return ref.getDownloadUrl()
    }
}
