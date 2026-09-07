package com.bakery.erp.domain

/** Tamaño de papel al que debe salir el documento cuando NO se imprime por térmica. */
enum class PrintMediaSize {
    /** Rollo angosto (58/80 mm) — tickets. */
    RECEIPT,
    /** Hoja completa — órdenes de producción impresas en una impresora normal. */
    PAGE
}

/**
 * Documento listo para imprimir, con las dos representaciones que necesitan las
 * plataformas: HTML (impresión por navegador o por el Print Framework de Android/iOS)
 * y ESC/POS ya renderizado por ancho de rollo (impresora térmica Bluetooth).
 *
 * Se construyen ambos ancho de rollo por adelantado en vez de recibir un lambda para
 * que el documento sea un valor puro — así se puede armar en el ViewModel/pantalla,
 * pasarlo entre capas y testearlo sin tocar la impresora.
 */
class PrintableDocument(
    /** Nombre del trabajo de impresión, visible en la cola del sistema. */
    val jobName: String,
    val html: String,
    val mediaSize: PrintMediaSize,
    /** Ancho de rollo en mm (58/80) → bytes ESC/POS. Vacío = solo imprimible por HTML. */
    val escPosByWidthMm: Map<Int, ByteArray> = emptyMap()
)

/**
 * Impresión de un documento arbitrario. Es la generalización de [ReceiptPrinter]:
 * mismo transporte (térmica Bluetooth con respaldo al diálogo del sistema), pero sin
 * atarse al formato de ticket de venta.
 *
 * - Android: si hay impresora térmica configurada envía ESC/POS por RFCOMM; si falla o
 *   no hay ninguna, cae al Print Framework (diálogo del sistema: PDF, WiFi, RawBT...).
 * - JS/Web: iframe oculto + `window.print()` — impresión normal en hoja.
 * - iOS: AirPrint vía `UIPrintInteractionController` (sin ESC/POS por Bluetooth: iOS no
 *   permite SPP sin accesorio MFi).
 */
interface DocumentPrinter {
    val isAvailable: Boolean
    suspend fun print(document: PrintableDocument): Result<Unit>
}

class NoOpDocumentPrinter : DocumentPrinter {
    override val isAvailable: Boolean = false
    override suspend fun print(document: PrintableDocument): Result<Unit> =
        Result.failure(UnsupportedOperationException("Impresión no disponible en esta plataforma"))
}
