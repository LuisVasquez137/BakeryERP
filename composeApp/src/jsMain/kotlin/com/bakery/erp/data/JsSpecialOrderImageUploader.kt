package com.bakery.erp.data

import com.bakery.erp.domain.SpecialOrderImageUploader
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.FirebaseStorage
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array

private fun ByteArray.asUint8Array(): Uint8Array {
    val int8 = unsafeCast<Int8Array>()
    return Uint8Array(int8.buffer, int8.byteOffset, int8.length)
}

class JsSpecialOrderImageUploader(
    private val storage: FirebaseStorage
) : SpecialOrderImageUploader {
    override suspend fun uploadSpecialItemImage(
        bytes: ByteArray,
        companyId: String,
        specialLineKey: String
    ): String {
        val ref = storage.reference.child("specialOrders/$companyId/$specialLineKey.jpg")
        ref.putData(Data(bytes.asUint8Array()))
        return ref.getDownloadUrl()
    }
}
