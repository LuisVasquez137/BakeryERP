package com.bakery.erp.ui

import androidx.compose.runtime.Composable

/**
 * No-op por ahora en iOS. Cambiar el estilo de la status bar requiere
 * `UIViewControllerBasedStatusBarAppearance = false` en Info.plist + APIs deprecadas; se deja para
 * una fase posterior si se hace foco en iOS.
 */
@Composable
actual fun StatusBarIcons(darkIcons: Boolean) {
    // intentionally empty
}
