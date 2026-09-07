package com.bakery.erp.ui

/**
 * Abre el diálogo nativo de compartir con el texto dado. En Android usa
 * `Intent.ACTION_SEND` con chooser. En web usa `navigator.share` si está
 * disponible; si no, copia al portapapeles.
 */
expect fun shareText(text: String, subject: String = "")
