package com.bakery.erp.ui

import androidx.compose.runtime.Composable

/**
 * Solicita el permiso runtime de Bluetooth (BLUETOOTH_CONNECT en Android 12+).
 * Devuelve un lambda que dispara el diálogo de permisos. En plataformas que no requieren
 * permiso (web/iOS), llama [onResult] con `true` inmediatamente.
 */
@Composable
expect fun rememberBluetoothPermissionRequester(
    onResult: (granted: Boolean) -> Unit
): () -> Unit
