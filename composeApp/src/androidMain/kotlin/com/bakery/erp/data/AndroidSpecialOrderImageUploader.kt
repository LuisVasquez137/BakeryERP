package com.bakery.erp.data

import com.bakery.erp.domain.SpecialOrderImageUploader
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.FirebaseStorage

class AndroidSpecialOrderImageUploader(
    private val storage: FirebaseStorage
) : SpecialOrderImageUploader {
    override suspend fun uploadSpecialItemImage(
        bytes: ByteArray,
        companyId: String,
        specialLineKey: String
    ): String {
        val ref = storage.reference.child("specialOrders/$companyId/$specialLineKey.jpg")
        ref.putData(Data(bytes))
        return ref.getDownloadUrl()
    }
}
