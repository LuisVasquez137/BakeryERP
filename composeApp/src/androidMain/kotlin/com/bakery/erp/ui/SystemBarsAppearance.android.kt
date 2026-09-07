package com.bakery.erp.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun StatusBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = (view.context as? Activity)?.window ?: return
    SideEffect {
        val controller = WindowCompat.getInsetsController(window, view)
        // isAppearanceLightStatusBars = true  -> íconos oscuros (fondo claro)
        // isAppearanceLightStatusBars = false -> íconos blancos (fondo azul)
        controller.isAppearanceLightStatusBars = darkIcons
    }
}
