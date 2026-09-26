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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.await
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.skia.Image as SkiaImage
import org.khronos.webgl.Int8Array

/**
 * Fotos ya decodificadas, por URL. Antes cada ficha volvía a descargar y decodificar su foto
 * cada vez que se dibujaba (al volver al POS, al cambiar de categoría): con esto se hace una
 * sola vez por sesión del navegador. Acotada para no crecer sin fin en catálogos grandes.
 */
private object JsImageCache {
    private const val MAX_ENTRIES = 300
    private val bitmaps = LinkedHashMap<String, ImageBitmap>()
    private val inFlight = mutableMapOf<String, CompletableDeferred<ImageBitmap?>>()

    fun peek(url: String): ImageBitmap? = bitmaps[url]

    /** Descarga y decodifica una sola vez aunque varias fichas pidan la misma foto a la vez. */
    suspend fun load(url: String): ImageBitmap? {
        bitmaps[url]?.let { return it }
        inFlight[url]?.let { return it.await() }
        val deferred = CompletableDeferred<ImageBitmap?>()
        inFlight[url] = deferred
        val bitmap = withTimeoutOrNull(10_000L) { fetchImageBytes(url) }?.let { bytes ->
            runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }
                .onFailure { console.error("ProductImage: error decodificando imagen — ${it.message}") }
                .getOrNull()
        }
        if (bitmap != null) {
            bitmaps[url] = bitmap
            if (bitmaps.size > MAX_ENTRIES) bitmaps.remove(bitmaps.keys.first())
        }
        inFlight.remove(url)
        deferred.complete(bitmap)
        return bitmap
    }
}

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

    // Si ya está en caché se dibuja en el primer cuadro, sin pasar por el ícono.
    var bitmap by remember(imageUrl) { mutableStateOf(JsImageCache.peek(imageUrl)) }

    LaunchedEffect(imageUrl) {
        if (bitmap == null) bitmap = JsImageCache.load(imageUrl)
    }

    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

@Composable
actual fun PrefetchImages(urls: List<String>) {
    LaunchedEffect(urls) {
        urls.distinct().forEach { JsImageCache.load(it) }
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
