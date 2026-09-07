package com.bakery.erp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@Composable
actual fun isBarcodeScanSupported(): Boolean = true

/**
 * Escáner de Google Play Services. Corre en su propia actividad, así que la app **no
 * declara ni pide el permiso de cámara** — el mismo criterio que la foto de factura con
 * `ACTION_IMAGE_CAPTURE`.
 *
 * El módulo del escáner se descarga bajo demanda la primera vez. Si el dispositivo no lo
 * tiene y no puede bajarlo, la tarea falla y no se llama a [onScanned]: el usuario teclea
 * el código, que es el camino que siempre está disponible.
 */
@Composable
actual fun rememberBarcodeScanner(onScanned: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onScanned)
    val scanner = remember(context) {
        GmsBarcodeScanning.getClient(
            context,
            GmsBarcodeScannerOptions.Builder()
                // Los formatos de producto empacado. Acotar la lista acelera la lectura y
                // evita que un QR pegado en la caja se cuele como código del producto.
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_ITF
                )
                .enableAutoZoom()
                .build()
        )
    }
    return {
        val task: Task<Barcode> = scanner.startScan()
        task.addOnSuccessListener { barcode ->
            barcode.rawValue?.takeIf { it.isNotBlank() }?.let { callback(it.trim()) }
        }
    }
}
