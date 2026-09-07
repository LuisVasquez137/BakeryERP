package com.bakery.erp.ui

import androidx.compose.runtime.Composable

/**
 * Sin escáner de cámara acá. El código se teclea, o lo escribe un lector físico —que para
 * la app es un teclado más. La pantalla esconde el botón y no pierde ninguna función.
 */
@Composable
actual fun isBarcodeScanSupported(): Boolean = false

@Composable
actual fun rememberBarcodeScanner(onScanned: (String) -> Unit): () -> Unit = {}
