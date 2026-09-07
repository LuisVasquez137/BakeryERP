package com.bakery.erp.presentation.reseller

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Product
import com.bakery.erp.presentation.auth.AuthViewModel
import androidx.compose.foundation.layout.widthIn
import com.bakery.erp.ui.TimePickerDialog
import com.bakery.erp.ui.formatHHmm
import com.bakery.erp.ui.parseHHmm
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors

private val MutedText = Color(0xFF6E6E6E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResellerQuickOrderScreen(
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ResellerViewModel,
    /** Si no es null, modo edición: carga el slot del revendedor para esa fecha+hora. */
    editDateMillis: Long? = null,
    editTime: String? = null
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val userId = authState.user?.id ?: ""
    val userName = authState.user?.displayName ?: ""
    val snackbarHostState = remember { SnackbarHostState() }
    val isEditMode = editDateMillis != null && editTime != null

    LaunchedEffect(companyId, userId) {
        if (state.customer == null && companyId.isNotEmpty()) {
            viewModel.loadForUser(companyId, userId)
        }
    }
    LaunchedEffect(isEditMode, companyId, userId, editDateMillis, editTime) {
        if (isEditMode && companyId.isNotEmpty()) {
            viewModel.loadOrdersForSlice(companyId, userId, editDateMillis!!, editTime!!)
        } else {
            viewModel.clearEditSlice()
        }
    }
    LaunchedEffect(state.isOrderSent) {
        if (state.isOrderSent) {
            // Navegamos PRIMERO (sincrónico). El snackbar no se mostraría aquí porque
            // el composable se va a destruir; mejor que el revendedor vea el pedido en
            // ResellerHome (lista de "Tus pedidos recientes") como feedback visual.
            onNavigateBack()
            viewModel.clearOrderSent()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    // Fecha default: mañana al inicio del día en TZ local (o el editDateMillis si edición).
    val tomorrowMillis = remember {
        val tz = TimeZone.currentSystemDefault()
        val today = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
            .toLocalDateTime(tz).date
        LocalDate(today.year, today.monthNumber, today.dayOfMonth)
            .atStartOfDayIn(tz).toEpochMilliseconds() + 24L * 60 * 60 * 1000
    }
    var deliveryDate by rememberSaveable {
        mutableStateOf(editDateMillis ?: tomorrowMillis)
    }
    var deliveryTime by rememberSaveable {
        mutableStateOf(editTime ?: (state.customer?.preferredPickupTime ?: ""))
    }
    // Inicializar deliveryTime cuando el customer carga (solo en modo creación).
    LaunchedEffect(state.customer?.preferredPickupTime) {
        if (!isEditMode && deliveryTime.isEmpty() &&
            !state.customer?.preferredPickupTime.isNullOrEmpty()
        ) {
            deliveryTime = state.customer!!.preferredPickupTime
        }
    }
    var notes by rememberSaveable { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Mapa productId → cantidad (string para input)
    val qtyText = remember { mutableStateMapOf<String, String>() }

    // En modo edición: pre-llenar cantidades al cargarse el slice.
    LaunchedEffect(state.editSliceQuantities) {
        if (isEditMode && state.editSliceQuantities.isNotEmpty()) {
            state.editSliceQuantities.forEach { (productId, qty) ->
                if (qtyText[productId].isNullOrEmpty()) {
                    qtyText[productId] = trimTrailingZeros(qty)
                }
            }
            // Tomar notes del primer pedido del slice.
            state.editSliceOrders.firstOrNull()?.notes?.let {
                if (notes.isEmpty()) notes = it
            }
        }
    }

    val totalAmount = state.products.sumOf { p ->
        val qty = qtyText[p.id]?.toDoubleOrNull() ?: 0.0
        val price = state.prices[p.id]?.unitPrice ?: 0.0
        qty * price
    }
    val totalLines = state.products.count {
        (qtyText[it.id]?.toDoubleOrNull() ?: 0.0) > 0.0
    }
    val canSubmit = totalLines > 0 && !state.isLoading && deliveryTime.isNotEmpty()

    Scaffold(
        containerColor = WarmIvory,
        topBar = {
            TopAppBar(
                title = { Text("Nuevo pedido", fontWeight = FontWeight.SemiBold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, null,
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryBlue)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        // No usamos AdaptiveContainer porque su inner Box no propaga altura completa,
        // lo que hacía que weight(1f) en el LazyColumn no funcionara y el footer
        // perdiera su anclaje al fondo. Hacemos centrado horizontal manual.
        Box(
            modifier = Modifier.padding(padding).fillMaxSize().imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (state.isLoading && state.customer == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 800.dp)
                ) {
                    // ── Cabecera: fecha y hora ────────────────────────────
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PickerField(
                                modifier = Modifier.weight(1f),
                                label = "Fecha",
                                value = formatDateLong(deliveryDate),
                                icon = Icons.Default.CalendarToday,
                                onClick = { showDatePicker = true }
                            )
                            PickerField(
                                modifier = Modifier.weight(1f),
                                label = "Hora",
                                value = deliveryTime.ifEmpty { "Toca para elegir" },
                                icon = Icons.Default.AccessTime,
                                onClick = { showTimePicker = true }
                            )
                        }
                    }

                    // ── Lista de productos + total + botón "Enviar" — todo dentro del scroll
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.products, key = { it.id }) { product ->
                            ProductRow(
                                product = product,
                                unitPrice = state.prices[product.id]?.unitPrice ?: 0.0,
                                quantityText = qtyText[product.id] ?: "",
                                onQtyChange = { txt ->
                                    val cleaned = txt.filter { c -> c.isDigit() || c == '.' }
                                    if (cleaned.isEmpty()) qtyText.remove(product.id)
                                    else qtyText[product.id] = cleaned
                                }
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text("Notas para la encargada (opcional)") },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "$totalLines producto(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedText
                                )
                                Text(
                                    "Total Q ${formatMoney(totalAmount)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = BrandColors.AccentDeep,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        item {
                            Button(
                                onClick = {
                                    val qtyMap = qtyText.mapValues { it.value.toDoubleOrNull() ?: 0.0 }
                                        .filter { it.value > 0.0 }
                                    viewModel.submitOrder(
                                        companyId = companyId,
                                        quantities = qtyMap,
                                        deliveryDate = deliveryDate,
                                        deliveryTime = deliveryTime,
                                        notes = notes.trim(),
                                        userId = userId,
                                        userName = userName,
                                        overrideExistingOrderIds = state.editSliceOrders.map { it.id }
                                    )
                                },
                                enabled = canSubmit,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandColors.AccentDeep),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (state.isLoading) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Send, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (isEditMode) "Guardar cambios" else "Enviar pedido",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = deliveryDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { deliveryDate = it }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = dpState)
        }
    }

    if (showTimePicker) {
        val initial = parseHHmm(deliveryTime) ?: (7 to 0)
        TimePickerDialog(
            initialHour = initial.first,
            initialMinute = initial.second,
            title = "Hora de retiro",
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m ->
                deliveryTime = formatHHmm(h, m)
                showTimePicker = false
            }
        )
    }

    if (state.duplicateOrderWarning.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.clearDuplicateWarning() },
            title = { Text("Pedido duplicado") },
            text = { Text(state.duplicateOrderWarning) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearDuplicateWarning()
                    showTimePicker = true
                }) { Text("Cambiar hora") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearDuplicateWarning() }) { Text("Entendido") }
            }
        )
    }
}

@Composable
private fun PickerField(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .background(PrimaryBlue.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MutedText)
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = PrimaryBlue,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun ProductRow(
    product: Product,
    unitPrice: Double,
    quantityText: String,
    onQtyChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PrimaryBlue,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${product.unit} · Q ${formatMoney(unitPrice)} c/u" +
                        if (!product.isInHouseProduced) " · reventa" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText
                )
            }
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(
                value = quantityText,
                onValueChange = onQtyChange,
                placeholder = { Text("0", color = MutedText) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(80.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BurntOrange,
                    cursorColor = BurntOrange
                )
            )
        }
    }
}

private fun trimTrailingZeros(v: Double): String {
    val l = v.toLong()
    return if (v == l.toDouble()) "$l" else v.toString().trimEnd('0').trimEnd('.')
}

private fun formatDateLong(millis: Long): String {
    if (millis <= 0L) return "—"
    val tz = TimeZone.currentSystemDefault()
    val dt: LocalDateTime = Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz)
    val today = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
        .toLocalDateTime(tz).date
    val daysDiff = dt.date.toEpochDays() - today.toEpochDays()
    val labelExtra = when (daysDiff) {
        0L -> " (hoy)"
        1L -> " (mañana)"
        else -> ""
    }
    val m = dt.monthNumber.toString().padStart(2, '0')
    val d = dt.dayOfMonth.toString().padStart(2, '0')
    return "$d/$m/${dt.year}$labelExtra"
}
