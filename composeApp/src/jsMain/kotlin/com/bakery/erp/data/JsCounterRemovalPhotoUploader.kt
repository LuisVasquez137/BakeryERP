package com.bakery.erp.data

import com.bakery.erp.domain.CounterRemovalPhotoUploader
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.FirebaseStorage
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array

class JsCounterRemovalPhotoUploader(
    private val storage: FirebaseStorage
) : CounterRemovalPhotoUploader {
    override suspend fun upload(bytes: ByteArray, companyId: String, removalId: String): String {
        val int8 = bytes.unsafeCast<Int8Array>()
        val ref = storage.reference.child("companies/$companyId/counterRemovals/$removalId.jpg")
        ref.putData(Data(Uint8Array(int8.buffer, int8.byteOffset, int8.length)))
        return ref.getDownloadUrl()
    }
}
