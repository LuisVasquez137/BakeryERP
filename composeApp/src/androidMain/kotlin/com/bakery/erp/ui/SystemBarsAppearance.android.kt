package com.bakery.erp.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun StatusBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = (view.context as? Activity)?.window ?: return
    // isAppearanceLightStatusBars = true  -> íconos oscuros (fondo claro)
    // isAppearanceLightStatusBars = false -> íconos blancos (fondo azul)
    //
    // Al salir de una pantalla clara se regresa a blancos: antes solo se aplicaba y el
    // splash dejaba los íconos negros sobre el encabezado azul el resto de la sesión.
    DisposableEffect(darkIcons) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = darkIcons
        onDispose { if (darkIcons) controller.isAppearanceLightStatusBars = false }
    }
}
