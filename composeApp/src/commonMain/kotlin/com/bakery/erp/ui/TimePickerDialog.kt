package com.bakery.erp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    title: String = "Seleccionar hora",
    /** `false` por defecto = reloj 12 horas (AM/PM). Más natural para usuarios
     *  no técnicos. Para shifts de producción donde importa el formato 24h, pasar `true`. */
    is24Hour: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour.coerceIn(0, 23),
        initialMinute = initialMinute.coerceIn(0, 59),
        is24Hour = is24Hour
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("Aceptar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/** Parses "HH:mm" to a pair, or returns null if invalid/empty. */
fun parseHHmm(text: String): Pair<Int, Int>? {
    val parts = text.split(":").mapNotNull { it.toIntOrNull() }
    if (parts.size < 2) return null
    val h = parts[0].takeIf { it in 0..23 } ?: return null
    val m = parts[1].takeIf { it in 0..59 } ?: return null
    return h to m
}

/** Formats hour/minute into "HH:mm" with zero padding. */
fun formatHHmm(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/**
 * True if the end time falls on the following calendar day (e.g. 22:00 → 04:00).
 * Times equal to start are treated as same-day (zero-length shift, not overnight).
 */
fun isOvernightShift(startTime: String, endTime: String): Boolean {
    val s = parseHHmm(startTime) ?: return false
    val e = parseHHmm(endTime) ?: return false
    val sMin = s.first * 60 + s.second
    val eMin = e.first * 60 + e.second
    return eMin < sMin
}
