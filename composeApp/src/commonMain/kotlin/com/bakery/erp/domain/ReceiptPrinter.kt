package com.bakery.erp.domain

import com.bakery.erp.model.ReceiptData

/**
 * Imprime un ticket de venta. La implementación es plataforma-específica:
 * - Android: usa el Print Framework (dialogo nativo de impresión; selecciona impresora
 *   Bluetooth, WiFi, PDF o vía PrintService como RawBT).
 * - JS/Web: abre el HTML en una ventana nueva y dispara window.print().
 * - iOS/wasm: no-op por ahora.
 *
 * [isAvailable] indica si esta plataforma soporta impresión. Útil para ocultar el botón.
 */
interface ReceiptPrinter {
    val isAvailable: Boolean
    suspend fun print(data: ReceiptData): Result<Unit>
}

class NoOpReceiptPrinter : ReceiptPrinter {
    override val isAvailable: Boolean = false
    override suspend fun print(data: ReceiptData): Result<Unit> =
        Result.failure(UnsupportedOperationException("Impresión no disponible en esta plataforma"))
}
