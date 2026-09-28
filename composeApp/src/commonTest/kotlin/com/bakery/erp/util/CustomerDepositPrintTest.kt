package com.bakery.erp.util

import com.bakery.erp.model.CustomerDeposit
import kotlin.test.Test
import kotlin.test.assertTrue

/** El comprobante lo guarda el cliente: si una línea se parte, el folio o el monto no se leen. */
class CustomerDepositPrintTest {

    private val deposit = CustomerDeposit(
        folio = "ANT-0042", customerName = "María José Pérez de León", customerPhone = "5512-3456",
        description = "Pastel de chocolate de tres pisos con fresas y decoración de cumpleaños",
        deliveryLabel = "Sábado 10 am", amount = 1250.0,
        branchName = "Bombero", registerName = "Caja 1", receivedByUserName = "Ana"
    )

    @Test
    fun `ninguna linea se pasa del rollo, tampoco las de doble ancho`() {
        for (mm in listOf(58, 80)) {
            val cols = if (mm >= 76) 48 else 32
            val bytes = depositEscPos(deposit, "Sabro Pan", "28/09/2026 10:15", mm)
            var big = false
            val text = StringBuilder()
            var i = 0
            val widths = mutableListOf<Int>()
            fun flush() { widths += if (big) text.length * 2 else text.length; text.clear() }
            while (i < bytes.size) {
                val b = bytes[i].toInt() and 0xFF
                when {
                    b == 0x1B && bytes[i + 1].toInt() == 0x40 -> i += 2
                    b == 0x1B -> i += 3
                    b == 0x1D && bytes[i + 1].toInt() == 0x21 -> { big = bytes[i + 2].toInt() != 0; i += 3 }
                    b == 0x1D -> i += 3
                    b == 0x0A -> { flush(); i++ }
                    else -> { text.append(b.toChar()); i++ }
                }
            }
            widths.forEach { assertTrue(it <= cols, "[$mm mm] una línea ocupa $it columnas de $cols") }
        }
    }

    @Test
    fun `lleva folio, monto y la aclaracion de que no es factura`() {
        val doc = buildCustomerDepositDocument(deposit, "Sabro Pan", "28/09/2026 10:15")
        assertTrue(doc.html.contains("ANT-0042"))
        assertTrue(doc.html.contains("Q1250.00"))
        assertTrue(doc.html.contains("no es una factura ni un documento fiscal"))
        val thermal = doc.escPosByWidthMm.getValue(58).decodeToString()
        assertTrue(thermal.contains("ANT-0042") && thermal.contains("Q1250.00"))
    }
}
