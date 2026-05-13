package com.bakery.erp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centra y limita el ancho del contenido para pantallas anchas (tablet, web, desktop).
 * En móvil (< 600dp) ocupa todo el ancho disponible.
 */
@Composable
fun AdaptiveContainer(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 800.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = maxContentWidth)
                .wrapContentWidth(Alignment.CenterHorizontally, unbounded = false),
            content = content
        )
    }
}
