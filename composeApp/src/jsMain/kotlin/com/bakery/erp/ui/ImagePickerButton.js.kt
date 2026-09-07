package com.bakery.erp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.browser.document
import org.jetbrains.skia.Image as SkiaImage
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import org.w3c.files.get

@Composable
actual fun ImagePickerButton(
    currentImageUrl: String?,
    selectedBytes: ByteArray?,
    onImageSelected: (ByteArray) -> Unit,
    modifier: Modifier,
    @Suppress("UNUSED_PARAMETER") allowCamera: Boolean
) {
    val preview: ImageBitmap? = remember(selectedBytes) {
        selectedBytes?.let {
            runCatching { SkiaImage.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (preview != null) {
            Image(
                bitmap = preview,
                contentDescription = "Vista previa",
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        } else if (!currentImageUrl.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Imagen guardada",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedButton(onClick = { openImageFilePicker(onImageSelected) }) {
            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (preview != null || !currentImageUrl.isNullOrEmpty()) "Cambiar imagen" else "Seleccionar imagen")
        }
    }
}

private fun openImageFilePicker(onImageSelected: (ByteArray) -> Unit) {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = "image/*"
    input.onchange = { _ ->
        val file = input.files?.get(0)
        if (file != null) {
            val reader = FileReader()
            reader.onload = { event ->
                val result = event.target.asDynamic().result
                val uint8 = js("new Uint8Array(result)")
                val len = uint8.length as Int
                val bytes = ByteArray(len) { i -> (uint8[i] as Int).toByte() }
                onImageSelected(bytes)
                Unit
            }
            reader.readAsArrayBuffer(file)
        }
        Unit
    }
    input.click()
}
