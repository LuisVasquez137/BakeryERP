package com.bakery.erp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun ImagePickerButton(
    currentImageUrl: String?,
    selectedBytes: ByteArray?,
    onImageSelected: (ByteArray) -> Unit,
    modifier: Modifier = Modifier
)
