package com.bakery.erp.ui

import androidx.compose.runtime.Composable

@Composable
actual fun rememberBluetoothPermissionRequester(
    onResult: (granted: Boolean) -> Unit
): () -> Unit = { onResult(true) }
