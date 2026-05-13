package com.bakery.erp

import androidx.compose.runtime.Composable
import com.bakery.erp.navigation.AppNavigation
import com.bakery.erp.ui.theme.AppTheme

@Composable
fun App() {
    AppTheme {
        AppNavigation()
    }
}
