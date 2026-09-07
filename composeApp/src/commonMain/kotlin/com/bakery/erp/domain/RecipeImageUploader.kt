package com.bakery.erp.domain

/**
 * Sube la foto de un paso de receta a Firebase Storage y devuelve el downloadUrl.
 *
 * Igual que [ProductRepository.uploadImage], la implementación es específica de
 * plataforma porque la construcción de `Data()` del SDK GitLive difiere entre
 * Android (ByteArray) y JS (Uint8Array). Se inyecta vía Koin desde los módulos
 * de plataforma (androidModule / módulo JS) hacia [RecipeViewModel].
 */
interface RecipeImageUploader {
    suspend fun uploadStepImage(
        bytes: ByteArray,
        recipeId: String,
        stageId: String,
        stepId: String
    ): String
}
