package com.bakery.erp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision

@Composable
actual fun ProductImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale
) {
    if (!imageUrl.isNullOrEmpty()) {
        val context = LocalContext.current
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                // Inexacto: una foto ya decodificada más grande (la precargada) sirve para
                // cualquier ficha más chica sin volver a decodificar.
                .precision(Precision.INEXACT)
                .build(),
            contentDescription = contentDescription,
            // Fondo mientras carga: la ficha no "salta" de vacío a foto.
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = contentScale
        )
    } else {
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
}

/**
 * Deja listas en memoria las fotos antes de que se dibujen, a un tamaño de ficha (512 px) y no
 * a la resolución de la cámara: decodificar una foto de 12 MP para un cuadro de 120 dp era la
 * otra mitad de la espera.
 */
@Composable
actual fun PrefetchImages(urls: List<String>) {
    val context = LocalContext.current
    LaunchedEffect(urls) {
        val loader = context.imageLoader
        urls.distinct().forEach { url ->
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(512)
                    .precision(Precision.INEXACT)
                    .build()
            )
        }
    }
}
