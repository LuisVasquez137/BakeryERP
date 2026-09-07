package com.bakery.erp.util

/**
 * Primitivas ESC/POS compartidas por los documentos que se imprimen en térmica
 * (ticket de venta y orden de producción).
 *
 * Comandos cubiertos:
 *  - ESC @         init
 *  - ESC a n       alineación (0=izq, 1=centro, 2=der)
 *  - ESC E n       bold on/off
 *  - GS ! n        tamaño: nibble alto = ancho, nibble bajo = alto (1..8 cada uno)
 *  - ESC d n       feed n líneas
 *  - GS V 0        corte total de papel
 */

/** Caracteres por línea a tamaño normal: 58mm = 32, 80mm = 48. */
fun escPosColumns(paperWidthMm: Int): Int = if (paperWidthMm >= 76) 48 else 32

class EscPosWriter(val cols: Int) {

    private val out = mutableListOf<Byte>()

    fun raw(vararg bytes: Int) { bytes.forEach { out.add(it.toByte()) } }

    fun text(s: String) { out.addAll(escPosTransliterate(s).encodeToByteArray().toList()) }

    fun lf() = raw(0x0A)

    fun line(s: String = "") { text(s); lf() }

    fun init() = raw(0x1B, 0x40)

    fun center() = raw(0x1B, 0x61, 0x01)
    fun left() = raw(0x1B, 0x61, 0x00)
    fun right() = raw(0x1B, 0x61, 0x02)

    fun bold(on: Boolean) = raw(0x1B, 0x45, if (on) 0x01 else 0x00)

    /** Multiplicadores 1..8. `size(1, 1)` vuelve al tamaño normal. */
    fun size(width: Int, height: Int) {
        val w = (width.coerceIn(1, 8) - 1) shl 4
        val h = height.coerceIn(1, 8) - 1
        raw(0x1D, 0x21, w or h)
    }

    fun normalSize() = size(1, 1)

    fun separator(char: Char = '-', width: Int = cols) = line(char.toString().repeat(width))

    fun feedAndCut(feedLines: Int = 4) {
        raw(0x1B, 0x64, feedLines.coerceIn(0, 255))
        raw(0x1D, 0x56, 0x00)
    }

    fun toByteArray(): ByteArray = out.toByteArray()
}

/**
 * Etiqueta a la izquierda, valor pegado a la derecha, rellenando con espacios.
 * [width] permite calcularlo para líneas impresas a doble ancho (la mitad de columnas).
 */
fun padBetween(width: Int, left: String, right: String): String {
    val pad = width - left.length - right.length
    return if (pad >= 1) left + " ".repeat(pad) + right
    else left.take((width - right.length - 1).coerceAtLeast(1)) + " " + right
}

/**
 * Parte el flujo ESC/POS en envíos que terminan en salto de línea, sin pasar de
 * [maxChunk] salvo que una sola línea ya lo exceda.
 *
 * Existe porque una térmica de rollo no negocia control de flujo por Bluetooth SPP: hay
 * que mandarle el documento a ritmo, en trozos y con pausas, o descarta lo que llegue con
 * el búfer lleno. Quién impone el ritmo es el transporte de cada plataforma; acá vive
 * solo el corte, que es lógica pura y conviene tener probada.
 *
 * Cortar en `0x0A` y no en cualquier byte es deliberado: ningún comando ESC/POS que
 * emitimos lleva `0x0A` como argumento (los de tamaño, negrita y alineación usan
 * 0x00–0x11), así que un `0x0A` en el flujo siempre es un salto real y nunca se parte una
 * secuencia de control por la mitad. Una línea más larga que [maxChunk] se manda entera:
 * partirla es justo lo que hay que evitar.
 */
fun chunkAtLineBreaks(bytes: ByteArray, maxChunk: Int): List<ByteArray> {
    if (bytes.isEmpty()) return emptyList()
    if (maxChunk <= 0) return listOf(bytes)

    val chunks = mutableListOf<ByteArray>()
    var start = 0
    var cursor = 0
    while (cursor < bytes.size) {
        val isLineEnd = bytes[cursor].toInt() == 0x0A
        val reachedLimit = cursor - start + 1 >= maxChunk
        if (isLineEnd && reachedLimit) {
            chunks.add(bytes.copyOfRange(start, cursor + 1))
            start = cursor + 1
        }
        cursor++
    }
    if (start < bytes.size) chunks.add(bytes.copyOfRange(start, bytes.size))
    return chunks
}

/**
 * Parte un texto en líneas de a lo sumo [width] columnas, cortando entre palabras.
 *
 * La térmica sí hace su propio salto de línea, pero al ras de la columna: parte la
 * palabra por la mitad ("...sin ajonjol / i"). Cortar acá deja el texto legible, y
 * [continuationIndent] sangra la continuación para que se lea como parte de la misma
 * anotación y no como una línea nueva.
 */
fun wrapToWidth(text: String, width: Int, continuationIndent: String = ""): List<String> {
    if (width <= 0) return listOf(text)
    val nextWidth = (width - continuationIndent.length).coerceAtLeast(1)

    val chunks = mutableListOf<String>()
    var current = ""
    // La primera línea usa el ancho completo; las siguientes pierden la sangría.
    fun capacity(): Int = if (chunks.isEmpty()) width else nextWidth

    text.split(' ').filter { it.isNotEmpty() }.forEach { word ->
        var w = word
        while (true) {
            val candidate = if (current.isEmpty()) w else "$current $w"
            if (candidate.length <= capacity()) { current = candidate; break }
            if (current.isNotEmpty()) { chunks.add(current); current = ""; continue }
            // Palabra sola más larga que la línea: no hay espacio donde cortar.
            chunks.add(w.take(capacity()))
            w = w.drop(capacity())
            if (w.isEmpty()) break
        }
    }
    if (current.isNotEmpty()) chunks.add(current)

    return chunks.mapIndexed { i, chunk -> if (i == 0) chunk else continuationIndent + chunk }
}

/**
 * Sustituye acentos y caracteres no-ASCII por equivalentes — las térmicas baratas
 * usan codepages que no coinciden con UTF-8 y salen con basura.
 */
fun escPosTransliterate(s: String): String = buildString(s.length) {
    s.forEach { c ->
        append(when (c) {
            'á', 'à', 'ä', 'â' -> 'a'
            'é', 'è', 'ë', 'ê' -> 'e'
            'í', 'ì', 'ï', 'î' -> 'i'
            'ó', 'ò', 'ö', 'ô' -> 'o'
            'ú', 'ù', 'ü', 'û' -> 'u'
            'Á', 'À', 'Ä', 'Â' -> 'A'
            'É', 'È', 'Ë', 'Ê' -> 'E'
            'Í', 'Ì', 'Ï', 'Î' -> 'I'
            'Ó', 'Ò', 'Ö', 'Ô' -> 'O'
            'Ú', 'Ù', 'Ü', 'Û' -> 'U'
            'ñ' -> 'n'
            'Ñ' -> 'N'
            '¿' -> '?'
            '¡' -> '!'
            '·' -> '-'
            '★', '☆' -> '*'
            '≈' -> '~'
            '‘', '’' -> '\''
            '“', '”' -> '"'
            else -> c
        })
    }
}
