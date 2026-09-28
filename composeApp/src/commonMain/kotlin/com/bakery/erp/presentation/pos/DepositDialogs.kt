package com.bakery.erp.presentation.pos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SelectableDates
import com.bakery.erp.model.depositDeliveryProblem
import com.bakery.erp.model.longDateLabel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bakery.erp.model.CustomerDepositItem
import com.bakery.erp.model.Product
import com.bakery.erp.model.deliveryDayLabel
import com.bakery.erp.model.depositItemsDescription
import com.bakery.erp.model.depositItemsTotal
import com.bakery.erp.ui.TimePickerDialog
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CustomerDeposit
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.model.matches
import com.bakery.erp.model.depositDescription
import com.bakery.erp.model.depositLabel
import kotlinx.datetime.toLocalDateTime
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtMoney

/** Lo que se teclea al recibir un anticipo. */
data class DepositInput(
    val customerName: String,
    val customerPhone: String,
    val description: String,
    val deliveryLabel: String,
    val amount: Double,
    val method: String,
    /** Pedido de cliente al que se liga (pastel, encargo especial). Vacío = anticipo suelto. */
    val orderId: String = "",
    val orderLabel: String = "",
    /** Fecha y hora exactas de entrega (epoch millis): de acá salen los recordatorios. */
    val deliveryAt: Long = 0L,
    /** Productos del catálogo. Vacío si es del restaurante (texto en [description]). */
    val items: List<CustomerDepositItem> = emptyList(),
    val isRestaurant: Boolean = false,
    /** Dónde recoge el cliente. Vacío = la sucursal de esta caja. */
    val pickupBranchId: String = "",
    val pickupBranchName: String = ""
)

/** "12/10 · 10:30 AM": la entrega del pedido tal como se ve en el anticipo. */
internal fun orderDeliveryLabel(order: com.bakery.erp.model.CustomerOrder): String {
    if (order.deliveryDate <= 0L) return order.deliveryTime
    val d = kotlin.time.Instant.fromEpochMilliseconds(order.deliveryDate)
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    val date = "${d.dayOfMonth.toString().padStart(2, '0')}/${d.monthNumber.toString().padStart(2, '0')}"
    return listOf(date, order.deliveryTime).filter { it.isNotBlank() }.joinToString(" · ")
}

/**
 * Recibir un anticipo en la caja abierta: quién, qué (del catálogo, o en texto si es comida del
 * restaurante), cuándo exacto y cuánto. El folio lo pone el sistema y va en el comprobante.
 *
 * Pantalla completa y no un AlertDialog: con el teclado abierto, un diálogo de alto fijo dejaba
 * los campos de abajo tapados. En pantalla ancha (tablet horizontal) va en dos columnas que se
 * desplazan por separado y los botones suben a la barra de arriba: con el teclado ocupando media
 * pantalla, una sola columna a lo ancho dejaba ver apenas el campo que se estaba llenando.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReceiveDepositDialog(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    /** [print] = el usuario eligió "Guardar e imprimir". */
    onConfirm: (input: DepositInput, print: Boolean) -> Unit,
    /** Pedidos con saldo para ligar el anticipo. Vacío = no se ofrece. */
    orders: List<com.bakery.erp.model.CustomerOrder> = emptyList(),
    /** Catálogo para elegir el encargo. */
    products: List<Product> = emptyList(),
    /** Sucursales donde se puede recoger; la de esta caja viene elegida. */
    branches: List<com.bakery.erp.model.Branch> = emptyList(),
    currentBranchId: String = ""
) {
    var order by remember { mutableStateOf<com.bakery.erp.model.CustomerOrder?>(null) }
    var pickupBranchId by remember { mutableStateOf(currentBranchId) }
    var orderPickerOpen by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var isRestaurant by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf(listOf<CustomerDepositItem>()) }
    var restaurantText by remember { mutableStateOf("") }
    var productPickerOpen by remember { mutableStateOf(false) }
    // Fecha (medianoche UTC, como la devuelve el DatePicker) y hora por separado; se juntan al guardar.
    var dateUtcMillis by remember { mutableStateOf<Long?>(null) }
    var time by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var timePickerOpen by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(SalePaymentMethod.CASH) }

    val tz = TimeZone.currentSystemDefault()
    val nowMillis = kotlin.time.Clock.System.now().toEpochMilliseconds()
    val selectedDate = dateUtcMillis?.let { kotlin.time.Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date }
    val deliveryAt: Long = run {
        val date = selectedDate ?: return@run 0L
        val (h, m) = time ?: return@run 0L
        LocalDateTime(date, LocalTime(h, m)).toInstant(tz).toEpochMilliseconds()
    }
    // Solo se reclama cuando ya se eligieron las dos: antes, "falta" lo dice el resumen.
    val deliveryProblem = if (selectedDate != null && time != null) depositDeliveryProblem(deliveryAt, nowMillis) else null
    val amount = amountText.toDoubleOrNull() ?: 0.0
    val itemsTotal = depositItemsTotal(items)
    val hasWhat = if (isRestaurant) restaurantText.isNotBlank() else items.isNotEmpty() || order != null
    val deliveryOk = deliveryAt > 0L && deliveryProblem == null
    val ready = name.isNotBlank() && hasWhat && amount > 0.0 && deliveryOk && !isSaving
    val missing = listOfNotNull(
        "cliente".takeIf { name.isBlank() },
        "qué encargó".takeIf { !hasWhat },
        "fecha y hora de entrega".takeIf { deliveryAt <= 0L },
        "monto".takeIf { amount <= 0.0 }
    )

    fun build() = DepositInput(
        customerName = name.trim(),
        customerPhone = phone.trim(),
        description = when {
            isRestaurant -> restaurantText.trim()
            items.isNotEmpty() -> depositItemsDescription(items)
            else -> order?.depositDescription().orEmpty()
        },
        // Etiqueta fija (no relativa): "Hoy" deja de ser cierto mañana.
        deliveryLabel = kotlin.time.Instant.fromEpochMilliseconds(deliveryAt).toLocalDateTime(tz).let { at ->
            "${longDateLabel(at.date)} ${time12(at.hour, at.minute)}"
        },
        amount = amount,
        method = method,
        orderId = order?.id.orEmpty(),
        orderLabel = order?.let { it.depositLabel(orderDeliveryLabel(it)) }.orEmpty(),
        deliveryAt = deliveryAt,
        items = if (isRestaurant) emptyList() else items,
        isRestaurant = isRestaurant,
        pickupBranchId = pickupBranchId,
        pickupBranchName = branches.firstOrNull { it.id == pickupBranchId }?.name.orEmpty()
    )

    // ── Secciones: las mismas en una o dos columnas ──────────────────────────
    val customerSection: @Composable (Boolean) -> Unit = { wide ->
        FormSection("Cliente") {
            val o = order
            if (o != null) {
                Surface(shape = RoundedCornerShape(10.dp), color = BrandColors.PrimaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Pedido: ${o.depositLabel(orderDeliveryLabel(o))}", fontWeight = FontWeight.SemiBold,
                                color = BrandColors.Primary)
                            Text("Total Q ${o.totalAmount.fmtMoney(2)} · debe Q ${o.balanceAmount.fmtMoney(2)}",
                                style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
                        }
                        TextButton(onClick = { order = null }) { Text("Quitar") }
                    }
                }
            } else if (orders.isNotEmpty()) {
                TextButton(onClick = { orderPickerOpen = true }) { Text("Ligar a un pedido de cliente (pastel, encargo)") }
            }
            // Nombre y teléfono en una línea cuando hay ancho: son dos datos cortos.
            val nameField: @Composable (Modifier) -> Unit = { m ->
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = m)
            }
            val phoneField: @Composable (Modifier) -> Unit = { m ->
                OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() || c == '-' || c == ' ' } },
                    label = { Text("Teléfono (opcional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    modifier = m)
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    nameField(Modifier.weight(1.4f))
                    phoneField(Modifier.weight(1f))
                }
            } else {
                nameField(Modifier.fillMaxWidth())
                phoneField(Modifier.fillMaxWidth())
            }
        }
    }

    val orderSection: @Composable () -> Unit = {
        FormSection("Encargo") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !isRestaurant, onClick = { isRestaurant = false }, label = { Text("Del catálogo") })
                FilterChip(selected = isRestaurant, onClick = { isRestaurant = true }, label = { Text("Del restaurante") })
            }
            if (isRestaurant) {
                // Los platillos viven en ComandaYa: acá no hay catálogo que elegir.
                OutlinedTextField(restaurantText, { restaurantText = it }, label = { Text("Descripción") },
                    placeholder = { Text("Almuerzo para 10: pollo, arroz, refrescos") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth())
            } else {
                items.forEachIndexed { index, item ->
                    DepositItemRow(
                        item = item,
                        onQuantity = { q -> items = items.toMutableList().also { it[index] = item.copy(quantity = q) } },
                        onRemove = { items = items.filterIndexed { i, _ -> i != index } }
                    )
                }
                OutlinedButton(onClick = { productPickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (items.isEmpty()) "Elegir del catálogo" else "Agregar otro producto")
                }
                if (items.isNotEmpty()) {
                    Text("Total del encargo: Q ${itemsTotal.fmtMoney(2)} (precio de hoy)",
                        style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
                }
            }
        }
    }

    val deliverySection: @Composable () -> Unit = {
        FormSection("Entrega") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { datePickerOpen = true }, modifier = Modifier.weight(1.5f)) {
                    Text(selectedDate?.let { longDateLabel(it) } ?: "Elegir fecha", maxLines = 1)
                }
                OutlinedButton(onClick = { timePickerOpen = true }, modifier = Modifier.weight(1f)) {
                    Text(time?.let { (h, m) -> time12(h, m) } ?: "Elegir hora", maxLines = 1)
                }
            }
            // Dónde recoge: por defecto aquí; el cliente puede dejar el anticipo en una
            // sucursal y recoger en otra.
            if (branches.size > 1) {
                Text("Recoge en", style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    branches.forEach { b ->
                        FilterChip(
                            selected = pickupBranchId == b.id,
                            onClick = { pickupBranchId = b.id },
                            label = { Text(if (b.id == currentBranchId) "${b.name} (aquí)" else b.name) }
                        )
                    }
                }
            }
            when {
                deliveryProblem != null ->
                    Text(deliveryProblem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                deliveryAt > 0L ->
                    Text(deliveryDayLabel(deliveryAt, nowMillis, tz), style = MaterialTheme.typography.bodySmall,
                        color = BrandColors.Primary)
            }
        }
    }

    val paymentSection: @Composable () -> Unit = {
        FormSection("Anticipo") {
            OutlinedTextField(amountText, { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Monto") }, prefix = { Text("Q ") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth())
            if (!isRestaurant && itemsTotal > 0.0) {
                TextButton(onClick = { amountText = itemsTotal.fmt(2) }) { Text("Pagó todo: Q ${itemsTotal.fmtMoney(2)}") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SalePaymentMethod.all.forEach { m ->
                    FilterChip(selected = method == m, onClick = { method = m }, label = { Text(SalePaymentMethod.label(m)) })
                }
            }
            if (method != SalePaymentMethod.CASH) {
                Text("Con tarjeta o transferencia no entra efectivo a la gaveta: queda solo en el anticipo.",
                    style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
            }
        }
    }

    val actions: @Composable (onDark: Boolean) -> Unit = { onDark ->
        // Sin impresora (o si el cliente no lo quiere), se guarda igual: el folio queda en
        // "Anticipos pendientes" y se reimprime después.
        OutlinedButton(
            onClick = { onConfirm(build(), false) }, enabled = ready,
            colors = if (onDark) ButtonDefaults.outlinedButtonColors(contentColor = Color.White) else ButtonDefaults.outlinedButtonColors()
        ) { Text("Solo guardar") }
        Button(
            onClick = { onConfirm(build(), true) }, enabled = ready,
            colors = if (onDark) ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = BrandColors.Primary)
            else ButtonDefaults.buttonColors(containerColor = BrandColors.Primary)
        ) { Text(if (isSaving) "Guardando…" else "Guardar e imprimir") }
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = BrandColors.Ivory) {
            BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
                val wide = maxWidth >= 720.dp
                // Nombre y teléfono lado a lado ya en un teléfono grande o tablet vertical.
                val pairFields = maxWidth >= 480.dp
                Column(Modifier.fillMaxSize()) {
                    // ── Barra: título, y en pantalla ancha también los botones ──
                    Row(
                        Modifier.fillMaxWidth().background(BrandColors.Primary).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onDismiss, enabled = !isSaving) { Icon(Icons.Default.Close, "Cancelar", tint = Color.White) }
                        Text("Nuevo anticipo", color = Color.White, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (wide) {
                            if (!ready && missing.isNotEmpty()) {
                                Text("Falta: ${missing.joinToString(", ")}", color = Color.White.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            actions(true)
                        }
                    }

                    if (wide) {
                        // Dos columnas que se desplazan por separado: con el teclado abierto se ve
                        // la columna donde se escribe y la otra sigue a la vista.
                        Row(
                            Modifier.weight(1f).fillMaxWidth().widthIn(max = 1200.dp).padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(
                                Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                customerSection(true)
                                orderSection()
                            }
                            Column(
                                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                deliverySection()
                                paymentSection()
                            }
                        }
                    } else {
                        Column(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            customerSection(pairFields)
                            orderSection()
                            deliverySection()
                            paymentSection()
                            if (!ready && missing.isNotEmpty()) {
                                Text("Falta: ${missing.joinToString(", ")}", style = MaterialTheme.typography.bodySmall,
                                    color = BrandColors.AccentDeep)
                            }
                        }
                        HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                                .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                        ) { actions(false) }
                    }
                }
            }
        }
    }

    if (productPickerOpen) {
        ProductPickerDialog(
            products = products,
            onDismiss = { productPickerOpen = false },
            onPick = { p ->
                val existing = items.indexOfFirst { it.productId == p.id }
                items = if (existing >= 0) items.toMutableList().also { it[existing] = it[existing].copy(quantity = it[existing].quantity + 1) }
                else items + CustomerDepositItem(p.id, p.name, 1.0, p.salePrice)
                productPickerOpen = false
            }
        )
    }

    if (datePickerOpen) {
        // No se ofrecen días pasados: un encargo para ayer no existe.
        val today = kotlin.time.Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(tz).date
        val todayUtc = LocalDateTime(today, LocalTime(0, 0)).toInstant(TimeZone.UTC).toEpochMilliseconds()
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = dateUtcMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
                override fun isSelectableYear(year: Int) = year >= today.year
            }
        )
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = { dateUtcMillis = dpState.selectedDateMillis ?: dateUtcMillis; datePickerOpen = false }) {
                    Text("Aceptar")
                }
            },
            dismissButton = { TextButton(onClick = { datePickerOpen = false }) { Text("Cancelar") } }
        ) { DatePicker(state = dpState) }
    }

    if (timePickerOpen) {
        TimePickerDialog(
            initialHour = time?.first ?: 10,
            initialMinute = time?.second ?: 0,
            title = "Hora de entrega",
            onDismiss = { timePickerOpen = false },
            onConfirm = { h, m -> time = h to m; timePickerOpen = false }
        )
    }

    if (orderPickerOpen) {
        var query by remember { mutableStateOf("") }
        val q = query.trim().lowercase()
        val found = orders.filter { q.isEmpty() || it.customerName.lowercase().contains(q) || it.customerPhone.contains(q) }
        AlertDialog(
            onDismissRequest = { orderPickerOpen = false },
            title = { Text("Ligar a un pedido") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { query = it }, label = { Text("Cliente o teléfono") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(found, key = { it.id }) { o ->
                            Surface(shape = RoundedCornerShape(10.dp), color = BrandColors.Sand,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    // Lo que ya se sabe del pedido no se vuelve a teclear.
                                    order = o
                                    name = o.customerName
                                    phone = o.customerPhone
                                    isRestaurant = false
                                    if (o.lines.isNotEmpty()) {
                                        items = o.lines.map { l -> CustomerDepositItem(l.productId, l.productName, l.requestedQuantity, l.unitPrice) }
                                    }
                                    if (o.deliveryDate > 0L) {
                                        val local = kotlin.time.Instant.fromEpochMilliseconds(o.deliveryDate).toLocalDateTime(tz).date
                                        dateUtcMillis = LocalDateTime(local, LocalTime(0, 0)).toInstant(TimeZone.UTC).toEpochMilliseconds()
                                    }
                                    com.bakery.erp.ui.parseHHmm(o.deliveryTime)?.let { time = it }
                                    o.deliveryBranchId.takeIf { id -> branches.any { it.id == id } }?.let { pickupBranchId = it }
                                    if (amountText.isBlank()) amountText = o.balanceAmount.fmt(2)
                                    orderPickerOpen = false
                                }) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(o.depositLabel(orderDeliveryLabel(o)), fontWeight = FontWeight.SemiBold,
                                        color = BrandColors.TextSlate)
                                    Text("Total Q ${o.totalAmount.fmtMoney(2)} · debe Q ${o.balanceAmount.fmtMoney(2)} · ${com.bakery.erp.model.CustomerOrderStatus.label(o.status)}",
                                        style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { orderPickerOpen = false }) { Text("Cancelar") } }
        )
    }
}

/** "4:30 PM" */
private fun time12(hour: Int, minute: Int): String {
    val h12 = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
    return "$h12:${minute.toString().padStart(2, '0')} ${if (hour < 12) "AM" else "PM"}"
}

/** Tarjeta con título: agrupa los campos para que el formulario se lea por partes. */
@Composable
private fun FormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                color = BrandColors.Primary)
            content()
        }
    }
}

/** Un producto del encargo: cantidad editable con − / +. */
@Composable
private fun DepositItemRow(item: CustomerDepositItem, onQuantity: (Double) -> Unit, onRemove: () -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = BrandColors.Sand) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.productName, fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate)
                Text("Q ${item.subtotal.fmtMoney(2)}", style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
            }
            IconButton(onClick = { if (item.quantity > 1.0) onQuantity(item.quantity - 1) else onRemove() }) {
                Icon(Icons.Default.Remove, "Menos")
            }
            Text(item.quantity.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() },
                fontWeight = FontWeight.Bold)
            IconButton(onClick = { onQuantity(item.quantity + 1) }) { Icon(Icons.Default.Add, "Más") }
        }
    }
}

/** Buscar un producto del catálogo para el encargo. */
@Composable
private fun ProductPickerDialog(products: List<Product>, onDismiss: () -> Unit, onPick: (Product) -> Unit) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val found = products.filter { it.active && (q.isEmpty() || it.name.lowercase().contains(q)) }.sortedBy { it.name }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Producto del encargo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Buscar") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(found, key = { it.id }) { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(p.name, modifier = Modifier.weight(1f), color = BrandColors.TextSlate)
                            Text("Q ${p.salePrice.fmtMoney(2)}", color = BrandColors.MutedSlate)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Elegir el anticipo del cliente al cobrar: se busca por folio, nombre o teléfono. */
@Composable
fun DepositPickerDialog(
    deposits: List<CustomerDeposit>,
    onDismiss: () -> Unit,
    onSelect: (CustomerDeposit) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val found = deposits.filter { it.isOpen && it.matches(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aplicar anticipo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Folio, nombre o teléfono") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (found.isEmpty()) {
                    Text(
                        if (deposits.isEmpty()) "No hay anticipos pendientes." else "Ninguno coincide.",
                        color = BrandColors.MutedSlate
                    )
                }
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(found, key = { it.id }) { d -> DepositRow(d, Modifier.clickable { onSelect(d) }) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/**
 * Anticipos pendientes de aplicar, de todas las sucursales: cuánto dinero de clientes se está
 * guardando y de quién. Desde acá se reimprime el comprobante o se devuelve el anticipo.
 */
@Composable
fun DepositsListDialog(
    deposits: List<CustomerDeposit>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onReprint: (CustomerDeposit) -> Unit,
    onRefund: (CustomerDeposit, String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var refunding by remember { mutableStateOf<CustomerDeposit?>(null) }
    val found = deposits.filter { it.isOpen && it.matches(query) }
    val held = deposits.filter { it.isOpen }.sumOf { it.remaining }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anticipos pendientes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Q ${held.fmtMoney(2)} de clientes por aplicar", fontWeight = FontWeight.SemiBold,
                    color = BrandColors.TextSlate)
                OutlinedTextField(query, { query = it }, label = { Text("Folio, nombre o teléfono") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(found, key = { it.id }) { d ->
                        Column {
                            DepositRow(d, Modifier)
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { onReprint(d) }, enabled = !isBusy) { Text("Reimprimir") }
                                TextButton(onClick = { refunding = d }, enabled = !isBusy) {
                                    Text("Devolver", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )

    refunding?.let { d ->
        var reason by remember(d.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { refunding = null },
            title = { Text("Devolver ${d.folio}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Salen Q ${d.remaining.fmtMoney(2)} en efectivo de esta caja para ${d.customerName}. " +
                            "El anticipo queda cerrado.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(reason, { reason = it }, label = { Text("Motivo") },
                        placeholder = { Text("Canceló el encargo") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = { onRefund(d, reason.trim()); refunding = null },
                    enabled = reason.isNotBlank() && !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Devolver Q ${d.remaining.fmtMoney(2)}") }
            },
            dismissButton = { TextButton(onClick = { refunding = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun DepositRow(d: CustomerDeposit, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(10.dp), color = BrandColors.Sand, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${d.folio} · ${d.customerName}", fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate)
                Text(d.description, style = MaterialTheme.typography.bodySmall, color = BrandColors.TextSlate)
                if (d.customerOrderLabel.isNotBlank()) {
                    Text("Pedido: ${d.customerOrderLabel}", style = MaterialTheme.typography.bodySmall, color = BrandColors.Primary)
                }
                Text(
                    listOfNotNull(
                        // Con fecha exacta, relativa ("Hoy 4:30 PM"): es lo que hay que preparar ya.
                        (if (d.deliveryAt > 0L) deliveryDayLabel(d.deliveryAt, kotlin.time.Clock.System.now().toEpochMilliseconds(), TimeZone.currentSystemDefault())
                        else d.deliveryLabel).takeIf { it.isNotBlank() }?.let { "Entrega: $it" },
                        d.customerPhone.takeIf { it.isNotBlank() },
                        if (d.picksUpElsewhere) "Dejó en ${d.branchName} · recoge en ${d.pickupBranchName}"
                        else d.effectivePickupBranchName.takeIf { it.isNotBlank() }?.let { "Recoge en $it" }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Q ${d.remaining.fmtMoney(2)}", fontWeight = FontWeight.Bold, color = BrandColors.Primary)
                if (d.appliedAmount > 0.004) {
                    Text("de Q ${d.amount.fmtMoney(2)}", style = MaterialTheme.typography.bodySmall,
                        color = BrandColors.MutedSlate)
                }
            }
        }
    }
}
