package com.bakery.erp

import androidx.compose.runtime.Composable
import com.bakery.erp.navigation.AppNavigation
import com.bakery.erp.ui.ConnectivityBannerHost
import com.bakery.erp.ui.StatusBarIcons
import com.bakery.erp.ui.theme.AppTheme

@Composable
fun App() {
    AppTheme {
        // Default global: headers azules → íconos del sistema en blanco.
        // Pantallas con borde superior claro (splash, auth) lo sobrescriben con darkIcons = true.
        StatusBarIcons(darkIcons = false)
        ConnectivityBannerHost { AppNavigation() }
    }
}
