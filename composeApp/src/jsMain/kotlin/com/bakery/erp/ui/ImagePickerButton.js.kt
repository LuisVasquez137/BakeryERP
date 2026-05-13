package com.bakery.erp.ui

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun ImagePickerButton(
    currentImageUrl: String?,
    selectedBytes: ByteArray?,
    onImageSelected: (ByteArray) -> Unit,
    modifier: Modifier
) {
    OutlinedButton(onClick = {}, enabled = false, modifier = modifier) {
        Text("Imagen: no disponible en web aún")
    }
}
