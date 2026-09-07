package com.bakery.erp.domain

/**
 * Sube la foto de referencia de un pedido especial y devuelve el downloadUrl.
 *
 * La foto se sube **antes** de que exista el pedido (la encargada todavía está
 * armándolo), así que la ruta se arma con la clave sintética de la línea
 * (`ProductionOrderLine.productId`, ver `newSpecialProductId`) y no con un
 * orderId: esa clave ya es única y viaja con la línea hasta el lote.
 *
 * Implementación por plataforma, igual que [RecipeImageUploader]: la
 * construcción de `Data()` del SDK GitLive difiere entre Android (ByteArray) y
 * JS (Uint8Array).
 */
interface SpecialOrderImageUploader {
    suspend fun uploadSpecialItemImage(
        bytes: ByteArray,
        companyId: String,
        specialLineKey: String
    ): String
}

/** Sin storage disponible (iOS por ahora): la foto simplemente no se adjunta.
 *  El pedido especial funciona igual — la foto es opcional. */
class NoOpSpecialOrderImageUploader : SpecialOrderImageUploader {
    override suspend fun uploadSpecialItemImage(
        bytes: ByteArray,
        companyId: String,
        specialLineKey: String
    ): String = ""
}
