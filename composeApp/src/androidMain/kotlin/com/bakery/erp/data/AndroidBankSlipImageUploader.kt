package com.bakery.erp.data

import com.bakery.erp.domain.BankSlipImageUploader
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.FirebaseStorage

class AndroidBankSlipImageUploader(
    private val storage: FirebaseStorage
) : BankSlipImageUploader {
    override suspend fun uploadSlipPhoto(bytes: ByteArray, companyId: String, depositId: String): String {
        val ref = storage.reference.child("companies/$companyId/bankDeposits/$depositId.jpg")
        ref.putData(Data(bytes))
        return ref.getDownloadUrl()
    }
}
