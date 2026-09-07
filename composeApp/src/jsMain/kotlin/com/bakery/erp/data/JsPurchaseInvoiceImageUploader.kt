package com.bakery.erp.data

import com.bakery.erp.domain.PurchaseInvoiceImageUploader
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.FirebaseStorage
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array

private fun ByteArray.toUint8Array(): Uint8Array {
    val int8 = unsafeCast<Int8Array>()
    return Uint8Array(int8.buffer, int8.byteOffset, int8.length)
}

class JsPurchaseInvoiceImageUploader(
    private val storage: FirebaseStorage
) : PurchaseInvoiceImageUploader {
    override suspend fun uploadInvoicePhoto(
        bytes: ByteArray,
        companyId: String,
        purchaseId: String
    ): String {
        val ref = storage.reference.child("purchaseInvoices/$companyId/$purchaseId.jpg")
        ref.putData(Data(bytes.toUint8Array()))
        return ref.getDownloadUrl()
    }
}
