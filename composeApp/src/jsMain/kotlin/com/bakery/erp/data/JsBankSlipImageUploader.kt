package com.bakery.erp.data

import com.bakery.erp.domain.BankSlipImageUploader
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.FirebaseStorage
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array

class JsBankSlipImageUploader(
    private val storage: FirebaseStorage
) : BankSlipImageUploader {
    override suspend fun uploadSlipPhoto(bytes: ByteArray, companyId: String, depositId: String): String {
        val int8 = bytes.unsafeCast<Int8Array>()
        val ref = storage.reference.child("companies/$companyId/bankDeposits/$depositId.jpg")
        ref.putData(Data(Uint8Array(int8.buffer, int8.byteOffset, int8.length)))
        return ref.getDownloadUrl()
    }
}
