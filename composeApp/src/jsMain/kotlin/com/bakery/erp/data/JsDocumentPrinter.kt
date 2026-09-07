package com.bakery.erp.data

import com.bakery.erp.domain.DocumentPrinter
import com.bakery.erp.domain.PrintableDocument
import kotlinx.browser.window
import org.w3c.dom.HTMLIFrameElement

/**
 * Impresión en navegador: inyecta un iframe oculto con el HTML del documento y dispara
 * `window.print()` sobre ese iframe. No toca la página actual, y deja al usuario elegir
 * cualquier impresora del sistema operativo (incluidas térmicas instaladas como
 * impresora normal) o guardar en PDF.
 *
 * El ESC/POS del documento se ignora acá a propósito: el navegador no puede abrir un
 * socket Bluetooth SPP, y para eso ya está el diálogo de impresión del sistema.
 */
class JsDocumentPrinter : DocumentPrinter {

    override val isAvailable: Boolean = true

    override suspend fun print(document: PrintableDocument): Result<Unit> = runCatching {
        val iframe = kotlinx.browser.document.createElement("iframe") as HTMLIFrameElement
        iframe.style.position = "fixed"
        iframe.style.right = "0"
        iframe.style.bottom = "0"
        iframe.style.width = "0"
        iframe.style.height = "0"
        iframe.style.border = "0"
        kotlinx.browser.document.body?.appendChild(iframe)

        val doc = iframe.contentDocument
            ?: throw IllegalStateException("No se pudo acceder al iframe de impresión")
        doc.open()
        doc.write(document.html)
        doc.close()

        // Dar un pequeño respiro al render antes de imprimir.
        window.setTimeout({
            iframe.contentWindow?.focus()
            iframe.contentWindow?.print()
            window.setTimeout({ iframe.remove() }, 1000)
        }, 250)
    }
}
