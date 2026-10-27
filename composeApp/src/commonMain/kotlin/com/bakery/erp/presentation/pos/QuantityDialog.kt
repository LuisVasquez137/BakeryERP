package com.bakery.erp.presentation.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
    val manual = manualText.toDoubleOrNull() ?: 0.0
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
            // Con scroll: en la tablet acostada el diálogo es bajo y, sin esto, los botones
            // aplastaban el campo de "Otra cantidad" hasta dejarlo en una rayita.
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Arriba, siempre a la vista: 40 panes no se arman tocando botones.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manualText,
                        onValueChange = { manualText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Otra cantidad") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (manual > 0) onConfirm(manual) }),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { if (manual > 0) onConfirm(manual) },
                        enabled = manual > 0,
                        modifier = Modifier.height(56.dp)
                    ) { Text("Agregar") }
                }
                // Grid 3x5 de cantidades 1..15.
                for (row in 0 until 5) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        for (col in 0 until 3) {
                            val n = row * 3 + col + 1
                            ElevatedButton(
                                onClick = { onConfirm(n.toDouble()) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("$n", style = MaterialTheme.typography.titleMedium,
                                     fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
