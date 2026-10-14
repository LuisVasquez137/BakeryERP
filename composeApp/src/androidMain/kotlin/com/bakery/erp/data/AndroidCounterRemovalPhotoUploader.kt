package com.bakery.erp.data

import com.bakery.erp.domain.CounterRemovalPhotoUploader
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.FirebaseStorage

class AndroidCounterRemovalPhotoUploader(
    private val storage: FirebaseStorage
) : CounterRemovalPhotoUploader {
    override suspend fun upload(bytes: ByteArray, companyId: String, removalId: String): String {
        val ref = storage.reference.child("companies/$companyId/counterRemovals/$removalId.jpg")
        ref.putData(Data(bytes))
        return ref.getDownloadUrl()
    }
}
