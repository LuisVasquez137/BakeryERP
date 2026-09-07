package com.bakery.erp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import kotlinx.browser.document

/**
 * En web ajustamos `<meta name="theme-color">`, que algunos navegadores móviles usan para teñir la
 * barra del sistema. Best-effort: si no hay soporte, no pasa nada.
 */
@Composable
actual fun StatusBarIcons(darkIcons: Boolean) {
    SideEffect {
        val color = if (darkIcons) "#FDFBF7" else "#005B8D"
        val existing = document.querySelector("meta[name=theme-color]")
        if (existing != null) {
            existing.setAttribute("content", color)
        } else {
            val meta = document.createElement("meta")
            meta.setAttribute("name", "theme-color")
            meta.setAttribute("content", color)
            document.head?.appendChild(meta)
        }
    }
}
