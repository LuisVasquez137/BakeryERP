package com.bakery.erp.domain

/**
 * Sube la foto de la factura física y devuelve el downloadUrl.
 *
 * La ruta es `purchaseInvoices/{companyId}/{purchaseId}.jpg`: al reeditar una compra la foto
 * se sobreescribe en vez de dejar huérfanas las anteriores. Implementación por plataforma,
 * igual que [RecipeImageUploader] — la construcción de `Data()` del SDK GitLive difiere entre
 * Android (ByteArray) y JS (Uint8Array).
 */
interface PurchaseInvoiceImageUploader {
    suspend fun uploadInvoicePhoto(bytes: ByteArray, companyId: String, purchaseId: String): String
}

/** Sin storage disponible: la compra se guarda igual, la foto es opcional. */
class NoOpPurchaseInvoiceImageUploader : PurchaseInvoiceImageUploader {
    override suspend fun uploadInvoicePhoto(
        bytes: ByteArray,
        companyId: String,
        purchaseId: String
    ): String = ""
}
