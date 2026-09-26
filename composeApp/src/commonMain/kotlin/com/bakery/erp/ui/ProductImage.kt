package com.bakery.erp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

@Composable
expect fun ProductImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
)

/**
 * Precarga fotos de productos para que ya estén listas cuando se dibujan (el POS la llama al
 * abrir con todo su catálogo). No dibuja nada.
 */
@Composable
expect fun PrefetchImages(urls: List<String>)
