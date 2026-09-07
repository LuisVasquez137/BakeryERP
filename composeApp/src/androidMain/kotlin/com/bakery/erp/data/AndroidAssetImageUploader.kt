package com.bakery.erp.data

import com.bakery.erp.domain.AssetImageUploader
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.FirebaseStorage

class AndroidAssetImageUploader(
    private val storage: FirebaseStorage
) : AssetImageUploader {
    override suspend fun upload(imageBytes: ByteArray, assetId: String): String {
        val ref = storage.reference.child("assets/$assetId.jpg")
        ref.putData(Data(imageBytes))
        return ref.getDownloadUrl()
    }
}
