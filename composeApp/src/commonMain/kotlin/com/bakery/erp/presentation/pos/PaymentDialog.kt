package com.bakery.erp.presentation.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.util.fmtMoney

private val BILL_DENOMINATIONS = listOf(1, 5, 10, 20, 50, 100, 200)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentDialog(
    total: Double,
    onDismiss: () -> Unit,
    onConfirm: (method: String, tendered: Double) -> Unit
) {
    var method by remember { mutableStateOf(SalePaymentMethod.CASH) }
    var tenderedText by remember { mutableStateOf("") }
    val tendered = tenderedText.toDoubleOrNull() ?: 0.0
    val change = if (method == SalePaymentMethod.CASH) (tendered - total).coerceAtLeast(0.0) else 0.0
    val isCashShort = method == SalePaymentMethod.CASH && tenderedText.isNotEmpty() && tendered < total

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cobrar Q ${total.fmtMoney(2)}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SalePaymentMethod.all.forEach { m ->
                        FilterChip(
                            selected = method == m,
                            onClick = { method = m },
                            label = { Text(SalePaymentMethod.label(m)) }
                        )
                    }
                }

                if (method == SalePaymentMethod.CASH) {
                    OutlinedTextField(
                        value = tenderedText,
                        onValueChange = { tenderedText = it.filter { c -> c.isDigit() || c == '.' } },
                        prefix = { Text("Q ") },
                        label = { Text("Recibido") },
                        singleLine = true,
                        isError = isCashShort,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Toca un billete para sumarlo:",
                         style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BILL_DENOMINATIONS.forEach { v ->
                            AssistChip(
                                onClick = {
                                    val current = tenderedText.toDoubleOrNull() ?: 0.0
                                    tenderedText = (current + v).fmtMoney(2)
                                },
                                label = { Text("Q$v") }
                            )
                        }
                        AssistChip(onClick = { tenderedText = "" }, label = { Text("Limpiar") })
                    }

                    Card(colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total"); Text("Q ${total.fmtMoney(2)}")
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Recibido"); Text("Q ${tendered.fmtMoney(2)}")
                            }
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Vuelto", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Q ${change.fmtMoney(2)}",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCashShort) Color(0xFFB00020)
                                            else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                } else {
                    Card(colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total a cobrar", fontWeight = FontWeight.SemiBold)
                            Text("Q ${total.fmtMoney(2)}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val effective = if (method == SalePaymentMethod.CASH) tendered else total
                    onConfirm(method, effective)
                },
                enabled = when (method) {
                    SalePaymentMethod.CASH -> tendered >= total && total > 0
                    else -> total > 0
                }
            ) { Text("Confirmar venta") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
