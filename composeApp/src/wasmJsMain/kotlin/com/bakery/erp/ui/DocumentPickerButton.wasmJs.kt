package com.bakery.erp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun DocumentPickerButton(
    onDocumentSelected: (bytes: ByteArray, filename: String) -> Unit,
    modifier: Modifier,
    content: @Composable (() -> Unit) -> Unit
) {
    content { }
}
