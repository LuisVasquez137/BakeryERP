package com.bakery.erp.data

import com.bakery.erp.domain.RecipeImageUploader

/** Default para plataformas sin Storage funcional (iOS). Devuelve cadena vacía. */
class NoOpRecipeImageUploader : RecipeImageUploader {
    override suspend fun uploadStepImage(
        bytes: ByteArray,
        recipeId: String,
        stageId: String,
        stepId: String
    ): String = ""
}
