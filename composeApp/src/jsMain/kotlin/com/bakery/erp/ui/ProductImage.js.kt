package com.bakery.erp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.skia.Image as SkiaImage
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array

@Composable
actual fun ProductImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale
) {
    if (imageUrl.isNullOrBlank()) {
        DefaultProductIcon(modifier, contentDescription)
        return
    }

    var bitmap by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(imageUrl) {
        val bytes = withTimeoutOrNull(10_000L) { fetchImageBytes(imageUrl) }
        if (bytes == null) {
            console.warn("ProductImage: no se pudieron obtener bytes para $imageUrl (CORS o timeout)")
            return@LaunchedEffect
        }
        bitmap = runCatching {
            SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
        }.onFailure {
            console.error("ProductImage: error decodificando imagen — ${it.message}")
        }.getOrNull()
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        DefaultProductIcon(modifier, contentDescription)
    }
}

private suspend fun fetchImageBytes(url: String): ByteArray? = try {
    val response = window.fetch(url).await()
    if (!response.ok) {
        console.error("ProductImage: fetch fallo HTTP ${response.status} para $url")
        null
    } else {
        val buffer = response.arrayBuffer().await()
        Int8Array(buffer).unsafeCast<ByteArray>()
    }
} catch (e: Throwable) {
    console.error("ProductImage: fetch error para $url — ${e.message}")
    null
}

@Composable
private fun DefaultProductIcon(modifier: Modifier, contentDescription: String?) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.BakeryDining,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(48.dp)
        )
    }
}
