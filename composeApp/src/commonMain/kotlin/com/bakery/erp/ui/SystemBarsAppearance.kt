package com.bakery.erp.ui

import androidx.compose.runtime.Composable

/**
 * Controla la apariencia de los íconos del sistema en la status bar (hora, batería, señal).
 *
 *  - `darkIcons = false` → íconos **blancos**. Úsalo cuando el header superior es azul (`TopAppBar`
 *    de la marca), que es el caso por defecto en casi toda la app.
 *  - `darkIcons = true`  → íconos **oscuros**. Úsalo en pantallas cuyo borde superior es claro/ivory
 *    (p.ej. login/auth, POS a pantalla completa).
 *
 * Cada plataforma decide cómo aplicarlo:
 *  - Android: `WindowInsetsControllerCompat.isAppearanceLightStatusBars`.
 *  - JS/web: `<meta name="theme-color">`.
 *  - iOS / wasmJs: no-op por ahora.
 */
@Composable
expect fun StatusBarIcons(darkIcons: Boolean)
