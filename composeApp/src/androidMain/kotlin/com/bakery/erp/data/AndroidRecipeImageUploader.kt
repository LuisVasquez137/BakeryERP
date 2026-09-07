package com.bakery.erp.data

import com.bakery.erp.domain.RecipeImageUploader
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.FirebaseStorage

class AndroidRecipeImageUploader(
    private val storage: FirebaseStorage
) : RecipeImageUploader {
    override suspend fun uploadStepImage(
        bytes: ByteArray,
        recipeId: String,
        stageId: String,
        stepId: String
    ): String {
        val ref = storage.reference.child("recipes/$recipeId/steps/$stepId.jpg")
        ref.putData(Data(bytes))
        return ref.getDownloadUrl()
    }
}
