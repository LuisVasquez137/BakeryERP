package com.bakery.erp.presentation.pos

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtMoney
import kotlin.math.ceil

private val BILL_DENOMINATIONS = listOf(1, 5, 10, 20, 50, 100, 200)
private val SuccessGreen = Color(0xFF1B5E20)
private val SuccessContainer = Color(0xFFE6F4EA)

/**
 * Cobro del carrito.
 *
 * Diseñado para el mostrador con fila: el total se lee de lejos, la forma de pago es un toque
 * sobre una tarjeta grande, los montos típicos ("Exacto", el billete redondo siguiente) evitan
 * teclear, y el vuelto aparece en grande apenas se sabe cuánto dieron. Los billetes que se
 * tocan quedan registrados para "Último cobro".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val isCash = method == SalePaymentMethod.CASH
    val isCredit = method == SalePaymentMethod.CREDIT
    val discountAmount = if (isCash && discountAccount != null) employeeDiscount else 0.0
    @Suppress("NAME_SHADOWING") val total = baseTotal - discountAmount
    var credit by remember { mutableStateOf<CreditCharge?>(null) }
    val creditReady = credit != null && (credit?.account?.requiresPerson != true || credit?.personName?.isNotBlank() == true)
    var tenderedText by remember { mutableStateOf("") }
    var bills by remember { mutableStateOf(emptyMap<Int, Int>()) }
    val tendered = tenderedText.toDoubleOrNull() ?: 0.0
    val change = if (isCash) (tendered - total).coerceAtLeast(0.0) else 0.0
    val isCashShort = isCash && tenderedText.isNotEmpty() && tendered < total - 0.004

    val methods = SalePaymentMethod.all + listOfNotNull(SalePaymentMethod.CREDIT.takeIf { creditAccounts != null })
    val canConfirm = when (method) {
        SalePaymentMethod.CASH -> tendered >= total - 0.004 && total > 0
        SalePaymentMethod.CREDIT -> creditReady && total > 0
        else -> total > 0
    }

    fun confirm() {
        val c = credit
        if (isCredit && c != null) onConfirmCredit(c)
        else onConfirm(method, if (isCash) tendered else total, if (isCash) bills else emptyMap())
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 560.dp).heightIn(max = 760.dp)
        ) {
            Column {
                // ── Total ─────────────────────────────────────────────
                Box(
                    Modifier.fillMaxWidth()
                        .background(BrandColors.Primary, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Column {
                        Text("Total a cobrar", color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelLarge)
                        AnimatedContent(
                            targetState = total,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "total"
                        ) { t ->
                            Text("Q ${t.fmtMoney(2)}", color = Color.White, fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.displaySmall)
                        }
                        if (discountAmount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Q ${baseTotal.fmtMoney(2)}", color = Color.White.copy(alpha = 0.7f),
                                    textDecoration = TextDecoration.LineThrough,
                                    style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.width(8.dp))
                                Text("Descuento colaborador −Q ${discountAmount.fmtMoney(2)}",
                                    color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Default.Close, "Cancelar", tint = Color.White)
                    }
                }

                // ── Cuerpo ────────────────────────────────────────────
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        methods.forEach { m ->
                            MethodCard(
                                label = SalePaymentMethod.label(m),
                                icon = methodIcon(m),
                                selected = method == m,
                                onClick = { method = m },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    when {
                        isCredit && creditAccounts != null -> {
                            InfoNote("No entra dinero: se carga a la cuenta y se cobra cuando venga a pagar.")
                            CreditChargeSelector(accounts = creditAccounts, selection = credit, onChange = { credit = it })
                        }
                        isCash -> {
                            if (discountAccounts.isNotEmpty() && employeeDiscount > 0.0) {
                                val d = discountAccount
                                if (d == null) {
                                    OutlinedButton(
                                        onClick = { discountPickerOpen = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("¿Es un colaborador? Descuento de Q ${employeeDiscount.fmtMoney(2)}") }
                                } else {
                                    Surface(shape = RoundedCornerShape(12.dp), color = BrandColors.AccentSoft) {
                                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Text("Descuento para ${d.name}", fontWeight = FontWeight.SemiBold,
                                                color = BrandColors.Primary, modifier = Modifier.weight(1f))
                                            TextButton(onClick = { discountAccount = null; onDiscountChange(null) }) {
                                                Text("Quitar")
                                            }
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
                                label = { Text("Le dieron") },
                                singleLine = true,
                                isError = isCashShort,
                                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Montos típicos: lo exacto y los billetes redondos que alcanzan.
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                QuickAmount("Exacto", selected = tenderedText.isNotEmpty() && tendered == total && bills.isEmpty()) {
                                    tenderedText = total.fmt(2); bills = emptyMap()
                                }
                                suggestedAmounts(total).forEach { amount ->
                                    QuickAmount("Q ${amount}", selected = tenderedText.isNotEmpty() && tendered == amount.toDouble()) {
                                        tenderedText = amount.toDouble().fmt(2)
                                        // Si el monto ES un billete, se sabe con qué pagaron.
                                        bills = if (amount in BILL_DENOMINATIONS) mapOf(amount to 1) else emptyMap()
                                    }
                                }
                            }

                            Text("Billetes recibidos", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                BILL_DENOMINATIONS.forEach { v ->
                                    BillButton(value = v, count = bills[v] ?: 0) {
                                        val current = tenderedText.toDoubleOrNull() ?: 0.0
                                        // Si ya había un monto tecleado sin billetes, los billetes
                                        // no alcanzan a describirlo: se deja de llevar la cuenta.
                                        val trackable = bills.isNotEmpty() || tenderedText.isEmpty()
                                        tenderedText = (current + v).fmt(2)
                                        bills = if (trackable) bills + (v to ((bills[v] ?: 0) + 1)) else emptyMap()
                                    }
                                }
                                if (tenderedText.isNotEmpty()) {
                                    TextButton(onClick = { tenderedText = ""; bills = emptyMap() }) { Text("Borrar") }
                                }
                            }

                            ChangePanel(
                                hasAmount = tenderedText.isNotEmpty(),
                                short = isCashShort,
                                missing = (total - tendered).coerceAtLeast(0.0),
                                change = change
                            )
                        }
                        method == SalePaymentMethod.CARD ->
                            InfoNote("Pasá la tarjeta y confirmá cuando el voucher salga aprobado.")
                        else ->
                            InfoNote("Confirmá que la transferencia ya se acreditó antes de cobrar.")
                    }
                }

                // ── Acción ────────────────────────────────────────────
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { confirm() },
                        enabled = canConfirm,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Text(
                            if (isCredit) "Cargar Q ${total.fmtMoney(2)} a la cuenta" else "Cobrar Q ${total.fmtMoney(2)}",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

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

private fun methodIcon(method: String): ImageVector = when (method) {
    SalePaymentMethod.CASH -> Icons.Default.Payments
    SalePaymentMethod.CARD -> Icons.Default.CreditCard
    SalePaymentMethod.TRANSFER -> Icons.Default.AccountBalance
    else -> Icons.Default.PersonOutline
}

/**
 * Billetes redondos que alcanzan para el total, de menor a mayor, sin repetir lo exacto.
 * Para Q37.50: Q40, Q50, Q100. Es lo que el cliente suele entregar.
 */
internal fun suggestedAmounts(total: Double): List<Int> {
    if (total <= 0) return emptyList()
    return listOf(5, 10, 20, 50, 100, 200)
        .map { step -> (ceil(total / step) * step).toInt() }
        .filter { it > total + 0.004 }
        .distinct()
        .sorted()
        .take(4)
}

@Composable
private fun MethodCard(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container by animateColorAsState(
        if (selected) BrandColors.PrimaryContainer else MaterialTheme.colorScheme.surface, label = "method"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) BrandColors.Primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(76.dp)
    ) {
        Box {
            Column(
                Modifier.fillMaxSize().padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(icon, null, tint = if (selected) BrandColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) BrandColors.Primary else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center)
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, null, tint = BrandColors.Primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp))
            }
        }
    }
}

@Composable
private fun QuickAmount(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.SemiBold) },
        shape = RoundedCornerShape(10.dp)
    )
}

/** Un billete: el valor grande y, si ya se tocó, cuántos. */
@Composable
private fun BillButton(value: Int, count: Int, onClick: () -> Unit) {
    val active = count > 0
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (active) BrandColors.PrimaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (active) BrandColors.Primary else Color.Transparent),
        modifier = Modifier.size(width = 76.dp, height = 48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("Q$value", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium,
                color = if (active) BrandColors.Primary else MaterialTheme.colorScheme.onSurface)
            if (active) {
                Surface(
                    color = BrandColors.Primary, shape = CircleShape,
                    modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(18.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("$count", color = Color.White, style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangePanel(hasAmount: Boolean, short: Boolean, missing: Double, change: Double) {
    val (bg, fg, label, amount) = when {
        !hasAmount -> Quad(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant,
            "Vuelto", "—")
        short -> Quad(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error,
            "Faltan", "Q ${missing.fmtMoney(2)}")
        else -> Quad(SuccessContainer, SuccessGreen, "Vuelto", "Q ${change.fmtMoney(2)}")
    }
    Surface(shape = RoundedCornerShape(14.dp), color = bg, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            AnimatedContent(targetState = amount, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "change") { a ->
                Text(a, color = fg, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun InfoNote(text: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class Quad(val bg: Color, val fg: Color, val label: String, val amount: String)
