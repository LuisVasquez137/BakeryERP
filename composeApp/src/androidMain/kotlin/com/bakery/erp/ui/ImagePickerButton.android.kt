package com.bakery.erp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.core.content.ContextCompat
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
actual fun ImagePickerButton(
    currentImageUrl: String?,
    selectedBytes: ByteArray?,
    onImageSelected: (ByteArray) -> Unit,
    modifier: Modifier,
    allowCamera: Boolean
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
            bytes?.let(onImageSelected)
        }
    }

    // El archivo destino se crea ANTES de abrir la cámara y su Uri se recuerda acá:
    // `TakePicture` solo devuelve un booleano, no la imagen, así que sin guardarla no
    // habría de dónde leer la foto al volver.
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingPhotoUri
        pendingPhotoUri = null
        if (success && uri != null) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                .getOrNull()?.let(onImageSelected)
        }
    }

    fun openCamera() {
        runCatching {
            val file = File.createTempFile("captura_", ".jpg", context.cacheDir)
            // Se entrega por FileProvider y no como "file://": desde Android 7 pasarle una
            // Uri de archivo a otra app lanza FileUriExposedException.
            val uri = FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", file
            )
            pendingPhotoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    // La app declara CAMERA (selfie del kiosko de asistencia). Con el permiso declarado y no
    // concedido, ACTION_IMAGE_CAPTURE lanza SecurityException: hay que pedirlo antes.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) openCamera() }

    fun launchCamera() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) openCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val previewBitmap = remember(selectedBytes) {
            selectedBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }

        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap.asImageBitmap(),
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

        val hasImage = previewBitmap != null || !currentImageUrl.isNullOrEmpty()
        if (allowCamera) {
            OutlinedButton(onClick = { launchCamera() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (hasImage) "Tomar otra foto" else "Tomar foto")
            }
            OutlinedButton(onClick = { launcher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Elegir de la galería")
            }
        } else {
            OutlinedButton(onClick = { launcher.launch("image/*") }) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (hasImage) "Cambiar imagen" else "Seleccionar imagen")
            }
        }
    }
}
