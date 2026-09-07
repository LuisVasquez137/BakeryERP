package com.bakery.erp.data

import com.bakery.erp.domain.DocumentPrinter
import com.bakery.erp.domain.PrintableDocument
import kotlinx.coroutines.CompletableDeferred
import platform.UIKit.UIPrintInfo
import platform.UIKit.UIPrintInfoOrientation
import platform.UIKit.UIPrintInfoOutputType
import platform.UIKit.UIPrintInteractionController
import platform.UIKit.UIMarkupTextPrintFormatter

/**
 * Impresión en iOS vía AirPrint. Muestra el diálogo nativo del sistema con las
 * impresoras de la red y la opción de guardar en archivos.
 *
 * **No imprime ESC/POS por Bluetooth**: iOS no expone el perfil SPP a apps normales —
 * requiere una impresora certificada MFi y su SDK. Las térmicas Bluetooth genéricas
 * que usa la panadería solo funcionan desde Android; en iPhone el papel sale por
 * AirPrint o PDF, con el mismo contenido.
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class IosDocumentPrinter : DocumentPrinter {

    override val isAvailable: Boolean
        get() = UIPrintInteractionController.isPrintingAvailable()

    override suspend fun print(document: PrintableDocument): Result<Unit> {
        if (!UIPrintInteractionController.isPrintingAvailable()) {
            return Result.failure(IllegalStateException("Este dispositivo no soporta AirPrint"))
        }
        val deferred = CompletableDeferred<Result<Unit>>()

        val controller = UIPrintInteractionController.sharedPrintController
        val info = UIPrintInfo.printInfo().apply {
            outputType = UIPrintInfoOutputType.UIPrintInfoOutputGeneral
            jobName = document.jobName.ifEmpty { "Documento" }
            // Tanto el rollo como la hoja se imprimen a lo alto; AirPrint elige el
            // tamaño real según la impresora que seleccione el usuario.
            orientation = UIPrintInfoOrientation.UIPrintInfoOrientationPortrait
        }
        controller.printInfo = info
        controller.printFormatter = UIMarkupTextPrintFormatter(document.html)

        controller.presentAnimated(true) { _, completed, error ->
            when {
                error != null -> deferred.complete(
                    Result.failure(IllegalStateException(error.localizedDescription))
                )
                // `completed == false` es que el usuario cerró el diálogo: no es un error.
                else -> deferred.complete(Result.success(Unit))
            }
        }
        return deferred.await()
    }
}
