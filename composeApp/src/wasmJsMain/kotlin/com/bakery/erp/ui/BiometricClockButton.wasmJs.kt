package com.bakery.erp.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
actual fun BiometricClockButton(
    isClockedIn: Boolean,
    isLoading: Boolean,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    modifier: Modifier
) {
    Button(
        onClick = { if (!isClockedIn) onClockIn() else onClockOut() },
        enabled = !isLoading,
        modifier = modifier.fillMaxWidth().height(56.dp)
    ) {
        Text(if (isClockedIn) "Marcar Salida" else "Marcar Entrada")
    }
}
