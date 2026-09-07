package com.bakery.erp.data

import com.bakery.erp.domain.RecipeImageUploader
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.FirebaseStorage
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array

private fun ByteArray.toUint8Array(): Uint8Array {
    val int8 = unsafeCast<Int8Array>()
    return Uint8Array(int8.buffer, int8.byteOffset, int8.length)
}

class JsRecipeImageUploader(
    private val storage: FirebaseStorage
) : RecipeImageUploader {
    override suspend fun uploadStepImage(
        bytes: ByteArray,
        recipeId: String,
        stageId: String,
        stepId: String
    ): String {
        val ref = storage.reference.child("recipes/$recipeId/steps/$stepId.jpg")
        ref.putData(Data(bytes.toUint8Array()))
        return ref.getDownloadUrl()
    }
}
