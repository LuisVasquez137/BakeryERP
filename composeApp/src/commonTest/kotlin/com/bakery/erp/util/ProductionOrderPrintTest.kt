package com.bakery.erp.util

import com.bakery.erp.model.PrintMasaGroup
import com.bakery.erp.model.PrintProductLine
import com.bakery.erp.model.ProductionOrderPrintData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La térmica no ajusta texto: una línea más larga que el ancho del rollo se parte sola y
 * la cantidad —el único número que el panadero busca desde el otro lado de la mesa—
 * termina huérfana en la línea de abajo. Por eso lo que se verifica acá es el ancho de
 * cada línea impresa, no el contenido.
 */
class ProductionOrderPrintTest {

    /**
     * Devuelve solo el texto que la impresora dibuja, línea por línea.
     *
     * No sirve filtrar por "carácter imprimible": los argumentos de los comandos ESC/POS
     * caen dentro del rango ASCII visible (`GS ! 0x11` lleva un '!' literal), así que
     * medir sin quitar las secuencias cuenta bytes de control como columnas.
     */
    private fun visibleLines(bytes: ByteArray): List<String> {
        val out = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            val skip = when {
                b == 0x1B && i + 1 < bytes.size -> when (bytes[i + 1].toInt() and 0xFF) {
                    0x40 -> 2                       // ESC @        init
                    0x61, 0x45, 0x64 -> 3           // ESC a/E/d n  alineación, bold, feed
                    else -> 0
                }
                b == 0x1D && i + 1 < bytes.size -> when (bytes[i + 1].toInt() and 0xFF) {
                    0x21, 0x56 -> 3                 // GS ! n · GS V n
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

    private fun escPosLines(data: ProductionOrderPrintData, mm: Int): List<String> =
        visibleLines(buildProductionOrderEscPos(data, mm))

    private fun sample() = ProductionOrderPrintData(
        branchName = "Planta Democracia",
        shiftName = "Mañana",
        dateLabel = "Lunes 02/09/2026",
        statusLabel = "En producción",
        printedAtLabel = "02/09/2026 05:12",
        groups = listOf(
            PrintMasaGroup(
                title = "Hojaldre",
                totalUnits = 120,
                doughLb = 8.5,
                hasSponge = true,
                items = listOf(
                    PrintProductLine("Barcas", 40, trayBreakdown = "4 band. + 8 sueltos"),
                    PrintProductLine("Cuadrados de piña con azúcar glass", 50),
                    PrintProductLine("Pirujo del doble de tamaño", 30, isSpecial = true)
                )
            ),
            PrintMasaGroup(
                title = "Suave",
                totalUnits = 0,
                doughLb = 3.2,
                hasSponge = false,
                extraDoughLb = 3.2,
                extraDoughFor = "Pan de Capa",
                isFillingOnly = true
            )
        )
    )

    @Test
    fun `ninguna linea excede el ancho del rollo`() {
        listOf(58 to 32, 80 to 48).forEach { (mm, cols) ->
            escPosLines(sample(), mm).forEach { line ->
                assertTrue(
                    line.length <= cols,
                    "Rollo de ${mm}mm: línea de ${line.length} col (max $cols): '$line'"
                )
            }
        }
    }

    @Test
    fun `el titulo de masa cabe a doble ancho`() {
        // A doble ancho la impresora solo dibuja cols/2 caracteres; el resto se pierde
        // sin aviso, así que el builder debe truncar antes.
        val long = sample().let { data ->
            data.copy(groups = data.groups.map { it.copy(title = "MASA CON UN NOMBRE LARGUISIMO") })
        }
        val titleLine = escPosLines(long, 58).first { it.startsWith("MASA CON") }
        assertEquals(16, titleLine.length)
    }

    @Test
    fun `la cantidad queda pegada al margen derecho`() {
        val row = escPosLines(sample(), 58).first { it.startsWith("Barcas") }
        assertEquals(32, row.length)
        assertTrue(row.endsWith("40"), "La cantidad debe cerrar la línea: '$row'")
    }

    @Test
    fun `el especial se marca y el nombre largo no descuadra la cantidad`() {
        val lines = escPosLines(sample(), 58)
        val special = lines.first { it.startsWith("* Pirujo") }
        val largeName = lines.first { it.startsWith("Cuadrados") }
        assertEquals(32, special.length)
        assertEquals(32, largeName.length)
        assertTrue(special.endsWith("30"))
        assertTrue(largeName.endsWith("50"))
    }

    @Test
    fun `la masa que solo existe por relleno se anuncia como no pedida`() {
        val text = escPosLines(sample(), 58).joinToString("\n")
        assertTrue("NO PEDIDA - solo para relleno" in text)
        assertTrue("de: Pan de Capa" in text)
    }

    @Test
    fun `el html lleva todos los productos y sus cantidades`() {
        val html = buildProductionOrderHtml(sample())
        assertTrue("Barcas" in html)
        assertTrue("Cuadrados de piña" in html)
        // El total de la orden es la suma de las masas, no un campo aparte.
        assertTrue(">120<" in html)
        assertTrue("Pan de Capa" in html)
    }

    @Test
    fun `la nota de cliente larga se corta entre palabras y conserva la sangria`() {
        val data = sample().let { d ->
            d.copy(groups = d.groups.map { g ->
                g.copy(items = g.items.map { it.copy(customerNotes = listOf("Doña María: 5 - sin ajonjolí")) })
            })
        }
        val lines = escPosLines(data, 58)
        val first = lines.first { it.trimStart().startsWith(">") }
        val continuation = lines[lines.indexOf(first) + 1]
        assertTrue(first.startsWith("   > "), "La nota cuelga del producto: '$first'")
        assertTrue(continuation.startsWith("     "), "La continuación va más sangrada: '$continuation'")
        // Ninguna palabra partida a la mitad: la térmica ya hace eso sola y es lo que
        // este ajuste viene a evitar.
        assertTrue(lines.none { it.endsWith("ajonjol") })
    }

    @Test
    fun `wrapToWidth parte una palabra sola mas larga que la linea`() {
        // Sin este caso el bucle no tendría dónde cortar y se quedaría girando.
        val wrapped = wrapToWidth("supercalifragilisticoespialidoso", 10)
        assertTrue(wrapped.all { it.length <= 10 })
        assertEquals("supercalifragilisticoespialidoso", wrapped.joinToString(""))
    }

    // ── Troceo para el envío por Bluetooth ────────────────────────────────────
    // Mandar la orden entera de un `write()` la cortaba a la mitad: la térmica no
    // negocia control de flujo y descarta lo que llega con el búfer lleno.

    @Test
    fun `los trozos reconstruyen el documento sin perder ni repetir un byte`() {
        val bytes = buildProductionOrderEscPos(sample(), 58)
        val rebuilt = chunkAtLineBreaks(bytes, 256).fold(ByteArray(0)) { acc, c -> acc + c }
        assertEquals(bytes.toList(), rebuilt.toList())
    }

    @Test
    fun `una orden real se parte en varios envios`() {
        // Si esto diera 1, el arreglo no estaría haciendo nada.
        val bytes = buildProductionOrderEscPos(sample(), 58)
        assertTrue(
            chunkAtLineBreaks(bytes, 256).size > 1,
            "Una orden de ${bytes.size} B debe salir en varios envíos"
        )
    }

    @Test
    fun `cada trozo cierra en salto de linea salvo el ultimo`() {
        // Cortar a media secuencia de control dejaría a la impresora interpretando
        // un argumento como texto.
        val chunks = chunkAtLineBreaks(buildProductionOrderEscPos(sample(), 58), 256)
        chunks.dropLast(1).forEach { chunk ->
            assertEquals(0x0A, chunk.last().toInt(), "Trozo cortado fuera de un salto de línea")
        }
    }

    @Test
    fun `una linea mas larga que el trozo viaja entera`() {
        val bytes = ("x".repeat(50) + "\n").encodeToByteArray()
        val chunks = chunkAtLineBreaks(bytes, 10)
        assertEquals(1, chunks.size)
        assertEquals(bytes.toList(), chunks.first().toList())
    }

    @Test
    fun `un documento vacio no genera envios`() {
        assertEquals(0, chunkAtLineBreaks(ByteArray(0), 256).size)
    }

    @Test
    fun `los acentos se transliteran para las codepages de la termica`() {
        val text = escPosLines(sample(), 58).joinToString("\n")
        assertTrue("Manana" in text, "El turno debe salir sin ñ")
        assertTrue("En produccion" in text)
    }
}
