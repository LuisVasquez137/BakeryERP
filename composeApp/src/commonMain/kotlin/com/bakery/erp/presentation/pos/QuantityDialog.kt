package com.bakery.erp.presentation.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuantityDialog(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var manualText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                                      color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Grid 3x5 de cantidades 1..15.
                for (row in 0 until 5) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        for (col in 0 until 3) {
                            val n = row * 3 + col + 1
                            ElevatedButton(
                                onClick = { onConfirm(n.toDouble()) },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("$n", style = MaterialTheme.typography.titleMedium,
                                     fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manualText,
                        onValueChange = { manualText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Otra cantidad") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val q = manualText.toDoubleOrNull() ?: 0.0
                            if (q > 0) onConfirm(q)
                        },
                        enabled = (manualText.toDoubleOrNull() ?: 0.0) > 0
                    ) { Text("✓") }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
