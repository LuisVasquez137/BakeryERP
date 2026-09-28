package com.bakery.erp.util

import com.bakery.erp.model.FelCertifyResult
import com.bakery.erp.model.ReceiptData
import com.bakery.erp.model.ReceiptLine
import com.bakery.erp.model.felVerificationUrl
import com.bakery.erp.model.receiptInvoiceFrom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El ticket con factura es la representación gráfica ante la SAT: si una línea se pasa del
 * rollo, la térmica la parte sola y la autorización o el NIT quedan ilegibles. Y el QR es lo
 * que el cliente escanea para comprobar que la factura existe.
 */
class ReceiptInvoicePrintTest {

    private val certified = FelCertifyResult(
        dteId = "dte_1", status = "CERTIFICADO", serie = "29E5FAE7", numero = "1619742329",
        uuid = "29E5FAE7-608B-4E79-A61B-9945DE1111E2", docType = "FPEQ", certifiedAt = 1_790_000_000_000,
        total = 1.9, receptorId = "CF", receptorName = "CONSUMIDOR FINAL",
        emisorNit = "59325216", emisorName = "VÁSQUEZ,FLORES,,LUIS,FERNANDO", emisorTradeName = "SABRO PAN",
        emisorAddress = "4 CALLE 33-05 ADICIONAL ZONA 8", phrases = emptyList(), environment = "PRODUCCION"
    )

    private fun receipt(total: Double = 1.9) = ReceiptData(
        companyName = "Sabropan", branchName = "Sucursal: Bombero", saleId = "ab12cd34",
        dateLabel = "25/09/2026 14:35", cashierName = "Ana", registerName = "Caja 1",
        lines = listOf(ReceiptLine("Pan de Q0.50", 2.0, 0.5, 1.0), ReceiptLine("Prueba", 1.0, 0.9, 0.9)),
        subtotal = total, discount = 0.0, total = total, paymentMethodLabel = "Efectivo",
        tenderedAmount = 5.0, changeAmount = 5.0 - total,
        invoice = receiptInvoiceFrom(certified, "25/09/2026 14:35")
    )

    @Test
    fun `la URL de verificacion lleva el formato de la SAT`() {
        assertEquals(
            "https://felpub.c.sat.gob.gt/verificador-web/publico/vistas/verificacionDte.jsf" +
                "?tipo=autorizacion&numero=29E5FAE7-608B-4E79-A61B-9945DE1111E2" +
                "&emisor=59325216&receptor=CF&monto=1.90",
            felVerificationUrl(certified.uuid, "5932521-6", "CF", 1.9)
        )
        assertTrue(felVerificationUrl("X", "59325216", "1234567-8", 10.0)!!.contains("receptor=12345678&"))
        assertNull(felVerificationUrl("", "59325216", "CF", 1.0))
    }

    @Test
    fun `el bloque fiscal sale de la respuesta del servidor`() {
        val inv = receipt().invoice!!
        assertEquals("FACTURA PEQUEÑO CONTRIBUYENTE", inv.title)
        assertEquals("5932521-6", inv.emisorNit)
        // Sin frases guardadas, el FPEQ igual lleva la suya.
        assertEquals(listOf("No genera derecho a crédito fiscal"), inv.phrases)
        assertFalse(inv.isTest)
    }

    @Test
    fun `el QR es cuadrado y lo bastante grande para la URL`() {
        val m = qrMatrix(felVerificationUrl(certified.uuid, "59325216", "CF", 1.9)!!)
        assertTrue(m.size >= 45, "una URL de ~190 caracteres necesita versión 7 o más")
        assertTrue(m.all { it.size == m.size })
        // Esquina superior izquierda: el patrón de posición empieza oscuro.
        assertTrue(m[0][0])
        assertTrue(qrSvg("hola", 30).startsWith("<svg"))
    }

    @Test
    fun `ninguna linea del ticket termico se pasa del rollo`() {
        for (mm in listOf(58, 80)) {
            val cols = if (mm >= 76) 48 else 32
            val lines = visibleLines(buildEscPosReceipt(receipt(), mm))
            lines.forEach { assertTrue(it.length <= cols, "[$mm mm] ${it.length} > $cols: '$it'") }
            assertTrue(lines.any { it.contains("29E5FAE7-608B") }, "falta la autorización")
            assertTrue(lines.any { it == "Serie: 29E5FAE7" })
        }
    }

    @Test
    fun `el ticket termico manda el QR con la URL de la SAT`() {
        val bytes = buildEscPosReceipt(receipt(), 58)
        val text = bytes.decodeToString()
        assertTrue(text.contains("verificacionDte.jsf?tipo=autorizacion"))
        assertTrue(indexOf(bytes, byteArrayOf(0x1D, 0x28, 0x6B)) >= 0, "falta el comando GS ( k")
    }

    @Test
    fun `si la factura cubre menos que el cobro, el ticket lo dice`() {
        val lines = visibleLines(buildEscPosReceipt(receipt(total = 51.9), 58))
        assertTrue(lines.any { it.startsWith("Monto facturado") && it.endsWith("Q1.90") })
        assertFalse(visibleLines(buildEscPosReceipt(receipt(), 58)).any { it.startsWith("Monto facturado") })
    }

    @Test
    fun `la factura no imprime cuanto dio ni el vuelto, el ticket sin factura si`() {
        for (mm in listOf(58, 80)) {
            val conFactura = visibleLines(buildEscPosReceipt(receipt(), mm))
            assertFalse(conFactura.any { it.startsWith("Recibido") || it.startsWith("Vuelto") })
            val sinFactura = visibleLines(buildEscPosReceipt(receipt().copy(invoice = null), mm))
            assertTrue(sinFactura.any { it.startsWith("Vuelto") })
        }
        val html = buildReceiptHtml(receipt())
        assertFalse(html.contains("Vuelto") || html.contains("Recibido"))
    }

    @Test
    fun `sin detalle imprime la unica linea certificada y no los productos`() {
        val sinDetalle = receipt().copy(
            invoice = receiptInvoiceFrom(certified.copy(withoutDetailDescription = "Servicio de alimentos y/o bebidas"), "25/09/2026 14:35")
        )
        for (mm in listOf(58, 80)) {
            val cols = if (mm >= 76) 48 else 32
            val lines = visibleLines(buildEscPosReceipt(sinDetalle, mm))
            assertFalse(lines.any { it.startsWith("Pan de Q0.50") }, "[$mm] no debe listar productos")
            assertTrue(lines.any { it.endsWith("Q1.90") && it.contains("bebidas") }, "[$mm] falta la línea única")
            lines.forEach { assertTrue(it.length <= cols, "[$mm] '$it'") }
        }
        val html = buildReceiptHtml(sinDetalle)
        assertTrue(html.contains("Servicio de alimentos y/o bebidas"))
        assertFalse(html.contains("Pan de Q0.50"))
    }

    @Test
    fun `el largo del QR nunca deja un 0x0A en pL`() {
        // 7 caracteres → pL = 10: se le agrega un & para correrlo.
        val qr = escPosQr("1234567", 5)
        val storeAt = indexOf(qr, byteArrayOf(0x1D, 0x28, 0x6B), from = 24)
        assertTrue((qr[storeAt + 3].toInt() and 0xFF) != 0x0A)
    }

    /** Texto dibujado, sin comandos (incluido el QR, que lleva su dato como bytes visibles). */
    private fun visibleLines(bytes: ByteArray): List<String> {
        val out = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            val skip = when {
                b == 0x1B && i + 1 < bytes.size -> when (bytes[i + 1].toInt() and 0xFF) {
                    0x40 -> 2
                    0x61, 0x45, 0x64 -> 3
                    else -> 0
                }
                b == 0x1D && i + 1 < bytes.size -> when (bytes[i + 1].toInt() and 0xFF) {
                    0x21, 0x56 -> 3
                    0x28 -> 5 + (bytes[i + 3].toInt() and 0xFF) + (bytes[i + 4].toInt() and 0xFF) * 256
                    else -> 0
                }
                else -> 0
            }
            if (skip > 0) { i += skip; continue }
            out.append(b.toChar())
            i++
        }
        return out.toString().split('\n')
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int = 0): Int {
        outer@ for (i in from..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
