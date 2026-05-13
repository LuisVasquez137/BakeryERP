package com.bakery.erp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun ProductImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier
)
