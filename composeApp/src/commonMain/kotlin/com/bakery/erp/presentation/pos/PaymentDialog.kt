package com.bakery.erp.presentation.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtMoney

private val BILL_DENOMINATIONS = listOf(1, 5, 10, 20, 50, 100, 200)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentDialog(
    total: Double,
    onDismiss: () -> Unit,
    /**
     * [bills] = billetes que la cajera tocó (valor → cantidad). Vacío si tecleó el monto: en
     * ese caso no se sabe con qué billetes pagaron, y es mejor no inventarlo.
     */
    onConfirm: (method: String, tendered: Double, bills: Map<Int, Int>) -> Unit,
    /**
     * Cuentas a las que se puede fiar. `null` = esta cajera no puede vender a crédito (o el
     * carrito trae una cuenta del restaurante) y la opción no aparece.
     */
    creditAccounts: List<com.bakery.erp.model.CreditAccount>? = null,
    onConfirmCredit: (CreditCharge) -> Unit = {},
    /**
     * Colaboradores con derecho a descuento. Se ofrece solo si [employeeDiscount] > 0, o sea
     * si algún producto del carrito lleva % de colaborador.
     */
    discountAccounts: List<com.bakery.erp.model.CreditAccount> = emptyList(),
    /** Lo que se descontaría con efectivo: el % de cada producto sobre su línea. */
    employeeDiscount: Double = 0.0,
    onDiscountChange: (com.bakery.erp.model.CreditAccount?) -> Unit = {}
) {
    var discountAccount by remember { mutableStateOf<com.bakery.erp.model.CreditAccount?>(null) }
    var discountPickerOpen by remember { mutableStateOf(false) }
    val baseTotal = total
    var method by remember { mutableStateOf(SalePaymentMethod.CASH) }
    val discountAmount = if (method == SalePaymentMethod.CASH && discountAccount != null) employeeDiscount else 0.0
    @Suppress("NAME_SHADOWING") val total = baseTotal - discountAmount
    var credit by remember { mutableStateOf<CreditCharge?>(null) }
    val isCredit = method == SalePaymentMethod.CREDIT
    val creditReady = credit != null && (credit?.account?.requiresPerson != true || credit?.personName?.isNotBlank() == true)
    var tenderedText by remember { mutableStateOf("") }
    var bills by remember { mutableStateOf(emptyMap<Int, Int>()) }
    val tendered = tenderedText.toDoubleOrNull() ?: 0.0
    val change = if (method == SalePaymentMethod.CASH) (tendered - total).coerceAtLeast(0.0) else 0.0
    val isCashShort = method == SalePaymentMethod.CASH && tenderedText.isNotEmpty() && tendered < total

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cobrar Q ${total.fmtMoney(2)}", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (SalePaymentMethod.all + listOfNotNull(SalePaymentMethod.CREDIT.takeIf { creditAccounts != null }))
                        .forEach { m ->
                            FilterChip(
                                selected = method == m,
                                onClick = { method = m },
                                label = { Text(SalePaymentMethod.label(m)) }
                            )
                        }
                }

                if (isCredit && creditAccounts != null) {
                    Text("No entra dinero: se carga a la cuenta y se cobra cuando venga a pagar.",
                         style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CreditChargeSelector(
                        accounts = creditAccounts,
                        selection = credit,
                        onChange = { credit = it }
                    )
                } else if (method == SalePaymentMethod.CASH) {
                    if (discountAccounts.isNotEmpty() && employeeDiscount > 0.0) {
                        val d = discountAccount
                        if (d == null) {
                            OutlinedButton(onClick = { discountPickerOpen = true }) {
                                Text("¿Es un colaborador? Descuento de Q ${employeeDiscount.fmtMoney(2)}")
                            }
                        } else {
                            Card(colors = CardDefaults.cardColors(containerColor = com.bakery.erp.ui.theme.BrandColors.AccentSoft)) {
                                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${d.name} · descuento de colaborador (efectivo)",
                                             fontWeight = FontWeight.SemiBold)
                                        Text("Descuento: −Q ${discountAmount.fmtMoney(2)} (antes Q ${baseTotal.fmtMoney(2)})",
                                             style = MaterialTheme.typography.labelSmall)
                                    }
                                    TextButton(onClick = { discountAccount = null; onDiscountChange(null) }) { Text("Quitar") }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = tenderedText,
                        onValueChange = {
                            tenderedText = it.filter { c -> c.isDigit() || c == '.' }
                            // Tecleado a mano: los billetes marcados ya no dicen cuánto dio.
                            bills = emptyMap()
                        },
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
                                    // Si ya había un monto tecleado sin billetes, los billetes
                                    // no alcanzan a describirlo: se deja de llevar la cuenta.
                                    val trackable = bills.isNotEmpty() || tenderedText.isEmpty()
                                    tenderedText = (current + v).fmt(2)
                                    bills = if (trackable) bills + (v to ((bills[v] ?: 0) + 1)) else emptyMap()
                                },
                                label = {
                                    val n = bills[v] ?: 0
                                    Text(if (n > 0) "Q$v ×$n" else "Q$v")
                                }
                            )
                        }
                        AssistChip(onClick = { tenderedText = ""; bills = emptyMap() }, label = { Text("Limpiar") })
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
                    val c = credit
                    if (isCredit && c != null) onConfirmCredit(c)
                    else {
                        val effective = if (method == SalePaymentMethod.CASH) tendered else total
                        onConfirm(method, effective, if (method == SalePaymentMethod.CASH) bills else emptyMap())
                    }
                },
                enabled = when (method) {
                    SalePaymentMethod.CASH -> tendered >= total && total > 0
                    SalePaymentMethod.CREDIT -> creditReady && total > 0
                    else -> total > 0
                }
            ) { Text(if (isCredit) "Cargar a la cuenta" else "Confirmar venta") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )

    if (discountPickerOpen) {
        AlertDialog(
            onDismissRequest = { discountPickerOpen = false },
            title = { Text("Colaborador con descuento") },
            text = {
                CreditAccountPicker(
                    accounts = discountAccounts,
                    selected = discountAccount,
                    onSelect = {
                        discountAccount = it; onDiscountChange(it); discountPickerOpen = false
                    },
                    placeholder = "Buscar colaborador"
                )
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { discountPickerOpen = false }) { Text("Cancelar") } }
        )
    }
}
