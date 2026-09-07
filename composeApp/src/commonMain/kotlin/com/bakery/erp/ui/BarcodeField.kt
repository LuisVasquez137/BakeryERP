package com.bakery.erp.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/**
 * Campo de código de barras: se teclea, se lee con un lector físico (que para la app es un
 * teclado) o se escanea con la cámara donde la haya.
 *
 * El botón de cámara solo aparece si la plataforma lo soporta. No se deja gris y deshabilitado
 * a propósito: un botón que nunca se puede tocar solo genera la pregunta de por qué.
 */
@Composable
fun BarcodeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    label: String = "Código de barras",
    supportingText: String? = null
) {
    val scan = rememberBarcodeScanner { onValueChange(it) }
    val canScan = isBarcodeScanSupported()
    OutlinedTextField(
        value = value,
        // Un código no lleva espacios ni saltos: el lector físico manda Enter al final y
        // sin esto el salto entra al campo y el código deja de coincidir con el catálogo.
        onValueChange = { onValueChange(it.filterNot { c -> c.isWhitespace() }) },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = {
            when {
                value.isNotEmpty() -> IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Borrar código")
                }
                canScan -> IconButton(onClick = scan) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear con la cámara")
                }
            }
        }
    )
}
