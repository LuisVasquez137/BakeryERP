package com.bakery.erp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.bakery.erp.model.SelfieCheck

actual val isSelfieCameraAvailable: Boolean = false

@Composable
actual fun SelfieCamera(
    modifier: Modifier,
    onStatus: (SelfieCameraStatus) -> Unit,
    onCaptured: (jpeg: ByteArray, check: SelfieCheck) -> Unit
) {
    LaunchedEffect(Unit) { onStatus(SelfieCameraStatus.NO_CAMERA) }
}
