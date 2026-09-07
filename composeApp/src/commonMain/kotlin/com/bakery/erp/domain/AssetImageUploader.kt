package com.bakery.erp.domain

/**
 * Sube la imagen de un activo fijo a un storage de plataforma y devuelve la URL pública.
 * Implementación por plataforma:
 *  - Android: AndroidAssetImageUploader (Firebase Storage)
 *  - JS / fallback: NoOp
 */
interface AssetImageUploader {
    suspend fun upload(imageBytes: ByteArray, assetId: String): String
}

class NoOpAssetImageUploader : AssetImageUploader {
    override suspend fun upload(imageBytes: ByteArray, assetId: String): String = ""
}
