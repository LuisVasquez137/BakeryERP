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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeliveryDining
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.model.minimumProblem
import com.bakery.erp.model.surchargeFor
import com.bakery.erp.model.surchargeDescription
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
 *
 * En pantalla ancha (tablet horizontal) va en dos paneles: a la izquierda cómo paga, a la
 * derecha lo del cliente —factura arriba, anticipo, descuento— y el botón de cobrar. En una
 * columna angosta la factura quedaba al fondo y había que desplazarse para llenarla.
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
    onConfirm: (method: String, tendered: Double, bills: Map<Int, Int>, invoice: com.bakery.erp.model.FelInvoiceRequest?,
                /** Pago dividido: tarjeta / transferencia → monto. Vacío = un solo método. */
                split: Map<String, Double>) -> Unit,
    /**
     * Cuentas a las que se puede fiar. `null` = esta cajera no puede vender a crédito (o el
     * carrito trae una cuenta del restaurante) y la opción no aparece.
     */
    creditAccounts: List<com.bakery.erp.model.CreditAccount>? = null,
    onConfirmCredit: (CreditCharge, com.bakery.erp.model.FelInvoiceRequest?) -> Unit = { _, _ -> },
    /**
     * Colaboradores con derecho a descuento. Se ofrece solo si [employeeDiscount] > 0, o sea
     * si algún producto del carrito lleva % de colaborador.
     */
    discountAccounts: List<com.bakery.erp.model.CreditAccount> = emptyList(),
    /** Lo que se descontaría con efectivo: el % de cada producto sobre su línea. */
    employeeDiscount: Double = 0.0,
    onDiscountChange: (com.bakery.erp.model.CreditAccount?) -> Unit = {},
    /** Null = esta sucursal no factura y la sección no aparece. */
    invoiceOptions: InvoiceOptions? = null,
    /**
     * Anticipos de clientes con saldo, de cualquier sucursal. Null = la opción no aparece. El
     * elegido se avisa por [onDepositChange] (igual que el descuento) y reduce lo que se cobra.
     */
    deposits: List<com.bakery.erp.model.CustomerDeposit>? = null,
    onDepositChange: (com.bakery.erp.model.CustomerDeposit?) -> Unit = {},
    /** Mínimo y recargo de la tarjeta (de la empresa). Por defecto, ninguno. */
    cardRules: com.bakery.erp.model.CardPaymentRules = com.bakery.erp.model.CardPaymentRules(),
    /** Método marcado al abrir: el que trae la cuenta del restaurante, para que solo se confirme. */
    initialMethod: String = SalePaymentMethod.CASH
) {
    val invoiceForm = remember { InvoiceFormState(invoiceOptions?.defaultOn == true) }
    var discountAccount by remember { mutableStateOf<com.bakery.erp.model.CreditAccount?>(null) }
    var discountPickerOpen by remember { mutableStateOf(false) }
    val baseTotal = total
    var method by remember { mutableStateOf(initialMethod) }
    // Pago dividido: se teclea lo de tarjeta/transferencia y el efectivo es lo que falta.
    var splitMode by remember { mutableStateOf(false) }
    var cardText by remember { mutableStateOf("") }
    var transferText by remember { mutableStateOf("") }
    val isCash = method == SalePaymentMethod.CASH
    val isCredit = method == SalePaymentMethod.CREDIT
    // Sin descuento de colaborador en un pago dividido: premia pagar TODO de contado.
    val discountAmount = if (isCash && !splitMode && discountAccount != null) employeeDiscount else 0.0
    var deposit by remember { mutableStateOf<com.bakery.erp.model.CustomerDeposit?>(null) }
    var depositPickerOpen by remember { mutableStateOf(false) }
    // Lo que vale la venta (va en la factura) y lo que se cobra hoy: el anticipo ya se pagó en
    // otra caja u otro día. No se combina con fiado — ahí no entra dinero que descontar.
    val saleTotal = baseTotal - discountAmount
    val depositApplied = if (!isCredit) minOf(deposit?.remaining ?: 0.0, saleTotal).coerceAtLeast(0.0) else 0.0
    @Suppress("NAME_SHADOWING") val total = saleTotal - depositApplied
    val coveredByDeposit = depositApplied > 0.004 && total <= 0.004
    var credit by remember { mutableStateOf<CreditCharge?>(null) }
    val creditReady = credit != null && (credit?.account?.requiresPerson != true || credit?.personName?.isNotBlank() == true)
    var tenderedText by remember { mutableStateOf("") }
    var bills by remember { mutableStateOf(emptyMap<Int, Int>()) }
    val tendered = tenderedText.toDoubleOrNull() ?: 0.0
    val splitAmounts = mapOf(
        SalePaymentMethod.CARD to (cardText.toDoubleOrNull() ?: 0.0),
        SalePaymentMethod.TRANSFER to (transferText.toDoubleOrNull() ?: 0.0)
    )
    val splitActive = splitMode && !coveredByDeposit
    val splitProblem = if (splitActive) com.bakery.erp.model.splitPaymentProblem(total, splitAmounts) else null
    // Lo que se paga en billetes: el total, o en un pago dividido lo que falta.
    val cashTarget = if (splitActive) (com.bakery.erp.model.splitPaymentParts(total, splitAmounts)[SalePaymentMethod.CASH] ?: 0.0)
                     else total
    val needsCash = isCash && cashTarget > 0.004
    // Lo que pasa por la terminal: el total con Tarjeta, o lo tecleado en un pago dividido.
    val cardBase = when {
        coveredByDeposit -> 0.0
        splitActive -> splitAmounts[SalePaymentMethod.CARD] ?: 0.0
        method == SalePaymentMethod.CARD -> total
        else -> 0.0
    }
    val cardMinimumProblem = cardRules.minimumProblem(cardBase)
    // La cajera no lo puede quitar: se suma solo (decisión del dueño).
    val cardSurcharge = if (cardMinimumProblem == null) cardRules.surchargeFor(cardBase) else 0.0
    val chargeTotal = total + cardSurcharge
    val change = if (needsCash) (tendered - cashTarget).coerceAtLeast(0.0) else 0.0
    val isCashShort = needsCash && tenderedText.isNotEmpty() && tendered < cashTarget - 0.004

    val methods = SalePaymentMethod.checkout + listOfNotNull(SalePaymentMethod.CREDIT.takeIf { creditAccounts != null })
    // Lo facturable (pan + restaurante no facturado en ComandaYa), con el descuento de colaborador.
    // El recargo va en la venta de pan, que siempre es facturable: cuenta para el tope de CF.
    val invoicedTotal = ((invoiceOptions?.invoiceableTotal ?: 0.0) - discountAmount).coerceAtLeast(0.0) + cardSurcharge
    val invoice = if (invoiceOptions != null) invoiceForm.request() else null
    val invoiceReady = invoiceOptions == null || invoiceForm.problem(invoicedTotal) == null
    val canConfirm = invoiceReady && cardMinimumProblem == null && (coveredByDeposit || when {
        splitActive -> splitProblem == null && (cashTarget <= 0.004 || tendered >= cashTarget - 0.004)
        else -> null
    } ?: when (method) {
        SalePaymentMethod.CASH -> tendered >= total - 0.004 && total > 0
        SalePaymentMethod.CREDIT -> creditReady && total > 0
        else -> total > 0
    })
    val showInvoice = invoiceOptions != null && invoiceOptions.invoiceableTotal > 0.0
    val hasExtras = (deposits != null && !isCredit) ||
        (isCash && !splitMode && discountAccounts.isNotEmpty() && employeeDiscount > 0.0)

    fun confirm() {
        val c = credit
        if (isCredit && c != null) onConfirmCredit(c, invoice)
        else if (splitActive) onConfirm(
            SalePaymentMethod.CASH,
            if (needsCash) tendered else 0.0,
            if (needsCash) bills else emptyMap(),
            invoice,
            splitAmounts.filterValues { it > 0.004 }
        )
        else onConfirm(method, if (isCash) tendered else total, if (isCash) bills else emptyMap(), invoice, emptyMap())
    }

    // ── Piezas: las mismas en uno o dos paneles ───────────────────────────────

    val header: @Composable () -> Unit = {
        Box(
            Modifier.fillMaxWidth().background(BrandColors.Primary).padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Column {
                Text("Total a cobrar", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                AnimatedContent(targetState = chargeTotal, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "total") { t ->
                    Text("Q ${t.fmtMoney(2)}", color = Color.White, fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.displaySmall)
                }
                // Lo que ya bajó el total, a la vista: de dónde sale el número de arriba.
                if (depositApplied > 0.004 || discountAmount > 0 || cardSurcharge > 0.004) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        HeaderChip("Venta Q ${baseTotal.fmtMoney(2)}")
                        if (discountAmount > 0) HeaderChip("Colaborador −Q ${discountAmount.fmtMoney(2)}")
                        if (depositApplied > 0.004) HeaderChip("Anticipo ${deposit?.folio.orEmpty()} −Q ${depositApplied.fmtMoney(2)}")
                        if (cardSurcharge > 0.004) HeaderChip("Recargo tarjeta +Q ${cardSurcharge.fmtMoney(2)}")
                    }
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Default.Close, "Cancelar", tint = Color.White)
            }
        }
    }

    val methodsRow: @Composable () -> Unit = {
        if (splitMode) {
            Surface(shape = RoundedCornerShape(14.dp), color = BrandColors.PrimaryContainer,
                border = BorderStroke(2.dp, BrandColors.Primary), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CallSplit, null, tint = BrandColors.Primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Pago dividido", fontWeight = FontWeight.Bold, color = BrandColors.Primary)
                        Text("Lo que no va con tarjeta o transferencia se paga en efectivo",
                            style = MaterialTheme.typography.bodySmall, color = BrandColors.TextSlate)
                    }
                    TextButton(onClick = {
                        splitMode = false; cardText = ""; transferText = ""
                        tenderedText = ""; bills = emptyMap()
                    }) { Text("Un solo método") }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                methods.forEach { m ->
                    MethodCard(label = SalePaymentMethod.label(m), icon = methodIcon(m), selected = method == m,
                        onClick = { method = m }, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    // Sin fiado combinado, y no hace falta si el anticipo ya cubre todo. Un pedido de PedidosYa
    // lo paga entero la app: no se divide.
    val canSplit = !isCredit && !coveredByDeposit && method != SalePaymentMethod.PEDIDOS_YA
    fun startSplit() {
        splitMode = true; method = SalePaymentMethod.CASH
        tenderedText = ""; bills = emptyMap()
        // Dividir quita el descuento de colaborador: avisarle a la pantalla.
        if (discountAccount != null) { discountAccount = null; onDiscountChange(null) }
    }

    // Cómo paga: lo que cambia con el método elegido.
    val paymentBody: @Composable () -> Unit = {
        if (coveredByDeposit) {
            InfoNote("El anticipo cubre todo: no hay nada que cobrar.")
        } else when {
            isCredit && creditAccounts != null -> {
                InfoNote("No entra dinero: se carga a la cuenta y se cobra cuando venga a pagar.")
                CreditChargeSelector(accounts = creditAccounts, selection = credit, onChange = { credit = it })
            }
            isCash -> {
                if (splitActive) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SplitAmountField("Tarjeta", cardText, Modifier.weight(1f)) { cardText = it }
                        SplitAmountField("Transferencia", transferText, Modifier.weight(1f)) { transferText = it }
                    }
                    if (splitProblem != null && (cardText.isNotEmpty() || transferText.isNotEmpty())) {
                        Text(splitProblem, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (cardMinimumProblem != null) {
                        Text(cardMinimumProblem, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    } else if (cardSurcharge > 0.004) {
                        InfoNote("Pasá Q ${(cardBase + cardSurcharge).fmtMoney(2)} en la terminal " +
                            "(incluye recargo de Q ${cardSurcharge.fmtMoney(2)}).")
                    }
                    // Con efectivo, lo que falta ya va en la etiqueta de "Le dieron".
                    if (!needsCash) SplitCashRow(cashTarget)
                }
                if (needsCash) {
                OutlinedTextField(
                    value = tenderedText,
                    onValueChange = {
                        tenderedText = it.filter { c -> c.isDigit() || c == '.' }
                        // Tecleado a mano: los billetes marcados ya no dicen cuánto dio.
                        bills = emptyMap()
                    },
                    prefix = { Text("Q ") },
                    label = {
                        Text(if (splitActive) "Le dieron (efectivo Q ${cashTarget.fmtMoney(2)})" else "Le dieron")
                    },
                    trailingIcon = if (canSplit && !splitMode) ({
                        TextButton(onClick = { startSplit() }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                            Icon(Icons.Default.CallSplit, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Dividir cuenta")
                        }
                    }) else null,
                    singleLine = true,
                    isError = isCashShort,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                // Montos típicos: lo exacto y los billetes redondos que alcanzan.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAmount("Exacto", selected = tenderedText.isNotEmpty() && tendered == cashTarget && bills.isEmpty()) {
                        tenderedText = cashTarget.fmt(2); bills = emptyMap()
                    }
                    suggestedAmounts(cashTarget).forEach { amount ->
                        QuickAmount("Q ${amount}", selected = tenderedText.isNotEmpty() && tendered == amount.toDouble()) {
                            tenderedText = amount.toDouble().fmt(2)
                            // Si el monto ES un billete, se sabe con qué pagaron.
                            bills = if (amount in BILL_DENOMINATIONS) mapOf(amount to 1) else emptyMap()
                        }
                    }
                }
                Text("Billetes recibidos", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BILL_DENOMINATIONS.forEach { v ->
                        BillButton(value = v, count = bills[v] ?: 0) {
                            val current = tenderedText.toDoubleOrNull() ?: 0.0
                            // Si ya había un monto tecleado sin billetes, los billetes no alcanzan
                            // a describirlo: se deja de llevar la cuenta.
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
                    missing = (cashTarget - tendered).coerceAtLeast(0.0),
                    change = change
                )
                }
            }
            else -> {
                if (method == SalePaymentMethod.CARD && cardMinimumProblem != null) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("$cardMinimumProblem: cobrá en efectivo o dividí la cuenta.",
                            modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                } else InfoNote(
                    when {
                        method == SalePaymentMethod.CARD && cardSurcharge > 0.004 ->
                            "Pasá Q ${chargeTotal.fmtMoney(2)} en la terminal (incluye recargo de " +
                                "Q ${cardSurcharge.fmtMoney(2)}: ${cardRules.surchargeDescription()})."
                        method == SalePaymentMethod.CARD -> "Pasá la tarjeta y confirmá cuando el voucher salga aprobado."
                        method == SalePaymentMethod.PEDIDOS_YA ->
                            "Pedido de PedidosYa: no entra dinero a la gaveta, PedidosYa lo liquida. " +
                                "Queda aparte en el cuadre y en los reportes."
                        else -> "Confirmá que la transferencia ya se acreditó antes de cobrar."
                    }
                )
                if (canSplit) {
                    TextButton(onClick = { startSplit() }) {
                        Icon(Icons.Default.CallSplit, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Dividir pago: parte con ${SalePaymentMethod.label(method).lowercase()}, parte en efectivo")
                    }
                }
            }
        }
    }

    // Del cliente: anticipo y descuento de colaborador.
    val extras: @Composable () -> Unit = {
        if (deposits != null && !isCredit) {
            val d = deposit
            if (d == null) {
                ExtraButton("Aplicar anticipo de cliente", Icons.Default.PersonOutline) { depositPickerOpen = true }
            } else {
                AppliedExtra(
                    title = "Anticipo ${d.folio} · ${d.customerName}",
                    detail = if (d.remaining > depositApplied + 0.004)
                        "Se aplican Q ${depositApplied.fmtMoney(2)}; le quedan Q ${(d.remaining - depositApplied).fmtMoney(2)}"
                    else "Se aplican Q ${depositApplied.fmtMoney(2)}",
                    onRemove = { deposit = null; onDepositChange(null) }
                )
            }
        }
        if (isCash && !splitMode && discountAccounts.isNotEmpty() && employeeDiscount > 0.0) {
            val d = discountAccount
            if (d == null) {
                ExtraButton("¿Es un colaborador? Descuento de Q ${employeeDiscount.fmtMoney(2)}", Icons.Default.PersonOutline) {
                    discountPickerOpen = true
                }
            } else {
                AppliedExtra(
                    title = "Descuento para ${d.name}",
                    detail = "−Q ${discountAmount.fmtMoney(2)}",
                    onRemove = { discountAccount = null; onDiscountChange(null) }
                )
            }
        }
    }

    val confirmButton: @Composable (Modifier) -> Unit = { m ->
        Button(
            onClick = { confirm() },
            enabled = canConfirm,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary),
            modifier = m.height(58.dp)
        ) {
            Text(
                when {
                    coveredByDeposit -> "Entregar (pagado con anticipo)"
                    isCredit -> "Cargar Q ${total.fmtMoney(2)} a la cuenta"
                    splitActive -> "Cobrar Q ${chargeTotal.fmtMoney(2)} dividido"
                    else -> "Cobrar Q ${chargeTotal.fmtMoney(2)}"
                },
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
            )
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Tablet horizontal: dos paneles. La factura dejó de quedar al fondo de una columna
            // angosta — ahora está arriba del panel del cliente, visible sin desplazarse.
            val wide = maxWidth >= 840.dp
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = if (wide) Modifier.fillMaxWidth(0.94f).widthIn(max = 1180.dp).fillMaxHeight(0.94f).imePadding()
                else Modifier.fillMaxWidth(0.94f).widthIn(max = 560.dp).heightIn(max = 760.dp).imePadding()
            ) {
                Column {
                    header()
                    if (wide) {
                        Row(Modifier.weight(1f)) {
                            // ── Izquierda: cómo paga ──
                            Column(
                                Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState())
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                SectionLabel("Forma de pago")
                                methodsRow()
                                paymentBody()
                            }
                            // ── Derecha: del cliente, y el botón de cobrar ──
                            Column(
                                Modifier.weight(1f).fillMaxHeight().background(BrandColors.Ivory)
                            ) {
                                Column(
                                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (showInvoice) {
                                        SectionLabel("Factura")
                                        InvoiceSection(invoiceForm, invoiceOptions!!, invoicedTotal)
                                    }
                                    if (hasExtras) {
                                        SectionLabel("Cliente")
                                        extras()
                                    }
                                    if (!showInvoice && !hasExtras) {
                                        InfoNote("Sin factura ni anticipos para esta venta.")
                                    }
                                }
                                HorizontalDivider()
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                                    Spacer(Modifier.width(8.dp))
                                    confirmButton(Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        Column(
                            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            methodsRow()
                            // La factura sube: se decide antes de ponerse a contar billetes.
                            if (showInvoice) InvoiceSection(invoiceForm, invoiceOptions!!, invoicedTotal)
                            extras()
                            paymentBody()
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onDismiss) { Text("Cancelar") }
                            Spacer(Modifier.width(8.dp))
                            confirmButton(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    if (depositPickerOpen && deposits != null) {
        DepositPickerDialog(
            deposits = deposits,
            onDismiss = { depositPickerOpen = false },
            onSelect = { deposit = it; onDepositChange(it); depositPickerOpen = false }
        )
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

/** Monto de tarjeta o transferencia en un pago dividido. */
@Composable
private fun SplitAmountField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }) },
        prefix = { Text("Q ") },
        label = { Text(label) },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

/** Lo que queda por pagar en efectivo: se calcula, no se teclea. */
@Composable
private fun SplitCashRow(cash: Double) {
    Surface(shape = RoundedCornerShape(12.dp), color = BrandColors.Ivory,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Payments, null, tint = BrandColors.Primary)
            Spacer(Modifier.width(10.dp))
            Text(if (cash > 0.004) "En efectivo" else "Sin efectivo: lo tecleado cubre todo",
                color = BrandColors.TextSlate, modifier = Modifier.weight(1f))
            if (cash > 0.004) {
                Text("Q ${cash.fmtMoney(2)}", fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleLarge, color = BrandColors.Primary)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
        color = BrandColors.MutedSlate)
}

@Composable
private fun HeaderChip(text: String) {
    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.18f)) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** Acción opcional del cliente (anticipo, descuento): una fila tocable, no un botón que grite. */
@Composable
private fun ExtraButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = BrandColors.Primary)
            Spacer(Modifier.width(10.dp))
            Text(text, color = BrandColors.TextSlate, modifier = Modifier.weight(1f))
            Icon(Icons.Default.Add, null, tint = BrandColors.MutedSlate)
        }
    }
}

@Composable
private fun AppliedExtra(title: String, detail: String, onRemove: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = BrandColors.AccentSoft, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, null, tint = BrandColors.Primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = BrandColors.Primary)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
            }
            TextButton(onClick = onRemove) { Text("Quitar") }
        }
    }
}

private fun methodIcon(method: String): ImageVector = when (method) {
    SalePaymentMethod.CASH -> Icons.Default.Payments
    SalePaymentMethod.CARD -> Icons.Default.CreditCard
    SalePaymentMethod.TRANSFER -> Icons.Default.AccountBalance
    SalePaymentMethod.PEDIDOS_YA -> Icons.Default.DeliveryDining
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
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
