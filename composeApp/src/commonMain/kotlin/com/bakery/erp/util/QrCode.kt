package com.bakery.erp.util

import qrcode.raw.ErrorCorrectionLevel
import qrcode.raw.QRCodeProcessor

/**
 * Matriz de un código QR (true = módulo oscuro), sin zona de silencio.
 *
 * Se usa [QRCodeProcessor] directo y no `QRCode.ofSquares()`: aquél solo calcula la matriz,
 * éste además arma el render de cada plataforma (Bitmap, Canvas…), que acá no se necesita.
 * Corrección M: aguanta un ticket arrugado o con una mancha sin volver el QR ilegible.
 */
fun qrMatrix(data: String): List<BooleanArray> =
    QRCodeProcessor(data, ErrorCorrectionLevel.MEDIUM).encode().map { row ->
        BooleanArray(row.size) { row[it].dark }
    }

/**
 * El QR como SVG en línea, para el ticket en hoja/PDF y web. Un solo `<path>` con un cuadro
 * por módulo: pesa poco, escala sin pixelarse y no depende de ninguna librería del navegador.
 */
fun qrSvg(data: String, sizeMm: Int): String {
    val matrix = qrMatrix(data)
    val quiet = 4 // zona de silencio que exige el estándar, en módulos
    val n = matrix.size + quiet * 2
    val path = buildString {
        matrix.forEachIndexed { y, row ->
            row.forEachIndexed { x, dark -> if (dark) append("M${x + quiet} ${y + quiet}h1v1h-1z") }
        }
    }
    return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $n $n" width="${sizeMm}mm" height="${sizeMm}mm" shape-rendering="crispEdges"><rect width="$n" height="$n" fill="#fff"/><path d="$path" fill="#000"/></svg>"""
}

/**
 * Comando ESC/POS que imprime un QR generado por la propia impresora (`GS ( k`, modelo 2).
 * [moduleDots] = tamaño de cada módulo en puntos: 5 cabe holgado en 58 mm.
 *
 * El largo del dato va en dos bytes (pL pH). Si pL fuera 0x0A, `chunkAtLineBreaks` podría
 * partir el envío justo ahí y dejar el comando cortado entre dos escrituras: en ese caso se
 * agrega un `&` al final, que en la URL de verificación no cambia nada.
 */
fun escPosQr(data: String, moduleDots: Int): ByteArray {
    var payload = data
    if (((payload.length + 3) and 0xFF) == 0x0A) payload += "&"
    val bytes = payload.encodeToByteArray()
    val len = bytes.size + 3
    val out = mutableListOf<Byte>()
    fun raw(vararg b: Int) { b.forEach { out.add(it.toByte()) } }
    raw(0x1D, 0x28, 0x6B, 4, 0, 0x31, 0x41, 0x32, 0x00)          // modelo 2
    raw(0x1D, 0x28, 0x6B, 3, 0, 0x31, 0x43, moduleDots)          // tamaño del módulo
    raw(0x1D, 0x28, 0x6B, 3, 0, 0x31, 0x45, 0x31)                // corrección M
    raw(0x1D, 0x28, 0x6B, len and 0xFF, (len shr 8) and 0xFF, 0x31, 0x50, 0x30)
    out.addAll(bytes.toList())                                    // dato
    raw(0x1D, 0x28, 0x6B, 3, 0, 0x31, 0x51, 0x30)                // imprimir
    return out.toByteArray()
}
