package com.bakery.erp.presentation.customerOrders

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CakeOrderDetails
import com.bakery.erp.model.CakeTemperature
import com.bakery.erp.model.CakeType
import com.bakery.erp.model.CustomerOrder
import com.bakery.erp.model.CustomerOrderDeliveryType
import com.bakery.erp.model.CustomerOrderLine
import com.bakery.erp.model.CustomerOrderStatus
import com.bakery.erp.model.CustomerOrderType
import com.bakery.erp.model.ExternalCustomer
import com.bakery.erp.model.Product
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
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
fun CustomerOrderFormScreen(
    orderId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CustomerOrderViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val userId = authState.user?.id ?: ""
    val userName = authState.user?.displayName ?: ""
    val snackbarHostState = remember { SnackbarHostState() }
    val isEdit = !orderId.isNullOrEmpty()

    LaunchedEffect(orderId, companyId) {
        if (companyId.isNotEmpty()) {
            viewModel.loadFormDependencies(companyId)
            if (isEdit) viewModel.selectOrder(companyId, orderId!!)
            else viewModel.selectOrder(companyId, "")
        }
    }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            viewModel.clearSaved()
            onNavigateBack()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        containerColor = WarmIvory,
        topBar = {
            BrandTopAppBar(
                title = {
                    Text(
                        if (isEdit) "Editar pedido" else "Nuevo pedido",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoading && state.products.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            } else if (isEdit && state.isDetailLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            } else {
                // Cuando navegamos de "nuevo pedido" tras haber editado uno, los
                // `rememberSaveable` del FormBody conservan los valores del anterior.
                // Envolver en `key(...)` con el id del pedido (o "new") fuerza un
                // descarte total del subtree y reinicializa todos los campos.
                androidx.compose.runtime.key(orderId ?: "new") {
                // Si NO estamos editando, ignora el `selectedOrder` que puede haber
                // quedado en el VM de un detalle previo. Sin esto, los rememberSaveable
                // del FormBody capturarían los datos del pedido anterior antes de que
                // el LaunchedEffect alcance a limpiar el estado.
                val effectiveExisting = if (isEdit) state.selectedOrder else null
                val effectiveLines = if (isEdit) state.selectedOrderLines else emptyList()
                FormBody(
                    existing = effectiveExisting,
                    existingLines = effectiveLines,
                    customers = state.customers,
                    producingBranches = state.producingBranches,
                    branches = state.branches,
                    products = state.products,
                    shifts = state.shifts,
                    isSaving = state.isLoading,
                    userId = userId,
                    userName = userName,
                    onShiftLoadRequest = { branchId ->
                        viewModel.loadShiftsForBranch(companyId, branchId)
                    },
                    resolveDefaultPriceList = { type, customer ->
                        viewModel.resolveDefaultPriceList(type, customer)
                    },
                    onSave = { order, lines -> viewModel.saveOrder(companyId, order, lines) }
                )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormBody(
    existing: CustomerOrder?,
    existingLines: List<CustomerOrderLine>,
    customers: List<ExternalCustomer>,
    producingBranches: List<com.bakery.erp.model.Branch>,
    branches: List<com.bakery.erp.model.Branch>,
    products: List<Product>,
    shifts: List<com.bakery.erp.model.ProductionShift>,
    isSaving: Boolean,
    userId: String,
    userName: String,
    onShiftLoadRequest: (branchId: String) -> Unit,
    resolveDefaultPriceList: (type: String, customer: ExternalCustomer?) -> com.bakery.erp.model.PriceList?,
    onSave: (CustomerOrder, List<CustomerOrderLine>) -> Unit
) {
    val scroll = rememberScrollState()

    var type by rememberSaveable {
        mutableStateOf(existing?.type ?: CustomerOrderType.RETAIL_SPECIAL)
    }
    var customerId by rememberSaveable { mutableStateOf(existing?.customerId ?: "") }
    var customerName by rememberSaveable { mutableStateOf(existing?.customerName ?: "") }
    var customerPhone by rememberSaveable { mutableStateOf(existing?.customerPhone ?: "") }
    var customerPhoneAlt by rememberSaveable { mutableStateOf(existing?.customerPhoneAlt ?: "") }
    var recipientName by rememberSaveable { mutableStateOf(existing?.recipientName ?: "") }

    var deliveryType by rememberSaveable {
        mutableStateOf(existing?.deliveryType ?: CustomerOrderDeliveryType.PICKUP)
    }
    var deliveryBranchId by rememberSaveable { mutableStateOf(existing?.deliveryBranchId ?: "") }
    var deliveryAddress by rememberSaveable { mutableStateOf(existing?.deliveryAddress ?: "") }
    var deliveryAddressRef by rememberSaveable {
        mutableStateOf(existing?.deliveryAddressReference ?: "")
    }
    var deliveryDateStr by rememberSaveable {
        mutableStateOf(
            existing?.deliveryDate?.takeIf { it > 0L }?.let { formatDateInput(it) } ?: ""
        )
    }
    var deliveryTime by rememberSaveable { mutableStateOf(existing?.deliveryTime ?: "") }

    var producingBranchId by rememberSaveable {
        mutableStateOf(existing?.producingBranchId ?: "")
    }
    var shiftId by rememberSaveable { mutableStateOf(existing?.shiftId ?: "") }

    // Pastel
    var cakeType by rememberSaveable {
        mutableStateOf(existing?.cakeDetails?.cakeType ?: CakeType.FONDANT)
    }
    var cakeTemp by rememberSaveable {
        mutableStateOf(existing?.cakeDetails?.temperature ?: CakeTemperature.AMBIENT)
    }
    var cakePortions by rememberSaveable {
        mutableStateOf((existing?.cakeDetails?.totalPortions ?: 0).toString().takeIf { it != "0" } ?: "")
    }
    var cakeBiscocho by rememberSaveable { mutableStateOf(existing?.cakeDetails?.biscocho ?: "") }
    var cakeFilling by rememberSaveable { mutableStateOf(existing?.cakeDetails?.filling ?: "") }
    var cakeExtras by rememberSaveable { mutableStateOf(existing?.cakeDetails?.extras ?: "") }

    // Líneas como SnapshotStateList
    val lines = remember(existingLines) {
        mutableStateListOf<DraftLine>().apply {
            addAll(existingLines.map { DraftLine.from(it) })
        }
    }

    // Extras monetarios (pasteles)
    var riceSheet by rememberSaveable {
        mutableStateOf(existing?.riceSheetAmount?.takeIf { it > 0 }?.toString() ?: "")
    }
    var fountainRent by rememberSaveable {
        mutableStateOf(existing?.fountainRentAmount?.takeIf { it > 0 }?.toString() ?: "")
    }
    var extrasAmount by rememberSaveable {
        mutableStateOf(existing?.extrasAmount?.takeIf { it > 0 }?.toString() ?: "")
    }
    var depositBase by rememberSaveable {
        mutableStateOf(existing?.depositBaseAmount?.takeIf { it > 0 }?.toString() ?: "")
    }

    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }

    // Auto-cargar shifts cuando cambia producingBranchId
    LaunchedEffect(producingBranchId) {
        onShiftLoadRequest(producingBranchId)
    }

    val subtotal = lines.sumOf { it.unitPrice * it.qty }
    val total = subtotal +
        (riceSheet.toDoubleOrNull() ?: 0.0) +
        (fountainRent.toDoubleOrNull() ?: 0.0) +
        (extrasAmount.toDoubleOrNull() ?: 0.0)
    // Lo abonado ya no se teclea: sale de los pagos registrados en caja (anticipos ligados al
    // pedido). Escribirlo acá era el papel en la app — un número sin dinero detrás en ninguna
    // gaveta. Se conserva lo que el pedido ya traía.
    val advance = existing?.advancePaidAmount ?: 0.0
    val balance = (total - advance).coerceAtLeast(0.0)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Tipo ──────────────────────────────────────────────────────
        // Nota: el tipo RESELLER no aparece aquí a propósito — los pedidos de
        // revendedores SIEMPRE los crea el propio revendedor desde su app
        // (ResellerQuickOrderScreen). La encargada en sucursal solo captura
        // encargos de cliente final (especiales o pasteles).
        SectionTitle("Tipo de pedido")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                CustomerOrderType.RETAIL_SPECIAL,
                CustomerOrderType.CAKE
            ).forEach { opt ->
                FilterChip(
                    selected = type == opt,
                    onClick = { type = opt },
                    label = { Text(CustomerOrderType.label(opt)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryBlue,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        // ── Cliente ───────────────────────────────────────────────────
        SectionTitle("Datos del cliente")
        CustomerPicker(
            customers = customers,
            currentId = customerId,
            onSelect = { c ->
                if (c == null) {
                    customerId = ""
                } else {
                    customerId = c.id
                    customerName = c.name
                    customerPhone = c.phone
                    customerPhoneAlt = c.phoneAlt
                }
            }
        )
        OutlinedTextField(
            value = customerName, onValueChange = { customerName = it },
            label = { Text("Nombre completo *") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = customerPhone, onValueChange = { customerPhone = it },
                label = { Text("Teléfono") },
                singleLine = true, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = customerPhoneAlt, onValueChange = { customerPhoneAlt = it },
                label = { Text("Teléfono adicional") },
                singleLine = true, modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = recipientName, onValueChange = { recipientName = it },
            label = { Text("¿Quién recoge o recibe?") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )

        // ── Entrega ───────────────────────────────────────────────────
        SectionTitle("Entrega")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                CustomerOrderDeliveryType.PICKUP,
                CustomerOrderDeliveryType.DELIVERY
            ).forEach { opt ->
                FilterChip(
                    selected = deliveryType == opt,
                    onClick = { deliveryType = opt },
                    label = { Text(CustomerOrderDeliveryType.label(opt)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryBlue,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
        BranchPicker(
            label = if (deliveryType == CustomerOrderDeliveryType.PICKUP)
                "Sucursal de retiro *" else "Sucursal origen del reparto *",
            branches = branches,
            currentId = deliveryBranchId,
            onSelect = { deliveryBranchId = it }
        )
        if (deliveryType == CustomerOrderDeliveryType.DELIVERY) {
            OutlinedTextField(
                value = deliveryAddress, onValueChange = { deliveryAddress = it },
                label = { Text("Dirección *") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = deliveryAddressRef, onValueChange = { deliveryAddressRef = it },
                label = { Text("Referencia (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = deliveryDateStr, onValueChange = { deliveryDateStr = it },
                label = { Text("Fecha (DD/MM/AAAA) *") },
                singleLine = true, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = deliveryTime, onValueChange = { deliveryTime = it },
                label = { Text("Hora") },
                singleLine = true, modifier = Modifier.weight(1f)
            )
        }

        // ── Producción ────────────────────────────────────────────────
        if (lines.any { it.requiresProduction } || type == CustomerOrderType.CAKE) {
            SectionTitle("Routing a producción")
            BranchPicker(
                label = "Planta productora",
                branches = producingBranches,
                currentId = producingBranchId,
                onSelect = { producingBranchId = it; shiftId = "" }
            )
            if (shifts.isNotEmpty()) {
                ShiftPicker(
                    shifts = shifts,
                    currentId = shiftId,
                    onSelect = { shiftId = it }
                )
            }
        }

        // ── Pastel ────────────────────────────────────────────────────
        if (type == CustomerOrderType.CAKE) {
            SectionTitle("Especificaciones del pastel")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(CakeType.FONDANT, CakeType.CREAM, CakeType.OTHER).forEach { opt ->
                    FilterChip(
                        selected = cakeType == opt,
                        onClick = { cakeType = opt },
                        label = { Text(CakeType.label(opt)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(CakeTemperature.AMBIENT, CakeTemperature.COLD).forEach { opt ->
                    FilterChip(
                        selected = cakeTemp == opt,
                        onClick = { cakeTemp = opt },
                        label = { Text(CakeTemperature.label(opt)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            OutlinedTextField(
                value = cakePortions, onValueChange = { cakePortions = it.filter { c -> c.isDigit() } },
                label = { Text("Total de porciones") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = cakeBiscocho, onValueChange = { cakeBiscocho = it },
                label = { Text("Sabor del biscocho") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = cakeFilling, onValueChange = { cakeFilling = it },
                label = { Text("Relleno") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = cakeExtras, onValueChange = { cakeExtras = it },
                label = { Text("Otros (decoración, mensaje, color, etc.)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── Líneas ────────────────────────────────────────────────────
        SectionTitle("Productos")
        lines.forEachIndexed { index, line ->
            LineEditor(
                line = line,
                products = products,
                onChange = { lines[index] = it },
                onRemove = { lines.removeAt(index) }
            )
        }
        Button(
            onClick = { lines.add(DraftLine()) },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue.copy(alpha = 0.12f), contentColor = PrimaryBlue),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text("Agregar producto")
        }

        // ── Totales ───────────────────────────────────────────────────
        SectionTitle("Totales y pagos")
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal", color = MutedText, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Q ${formatMoney(subtotal)}",
                        color = PrimaryBlue,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (type == CustomerOrderType.CAKE) {
                    Spacer(Modifier.size(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = riceSheet, onValueChange = { riceSheet = it.filterDecimal() },
                            label = { Text("Hoja de arroz") },
                            singleLine = true, modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = fountainRent, onValueChange = { fountainRent = it.filterDecimal() },
                            label = { Text("Alquiler de fuente") },
                            singleLine = true, modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = depositBase, onValueChange = { depositBase = it.filterDecimal() },
                        label = { Text("Depósito de base (reembolsable)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = extrasAmount, onValueChange = { extrasAmount = it.filterDecimal() },
                    label = { Text("Extras") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", color = PrimaryBlue, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Q ${formatMoney(total)}",
                        color = BrandColors.AccentDeep,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.size(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Anticipos cobrados", color = MutedText, style = MaterialTheme.typography.bodySmall)
                    Text("Q ${formatMoney(advance)}", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "El anticipo se recibe en la caja: Punto de Venta → Anticipo de cliente → Ligar a un pedido. " +
                        "Así el dinero queda en una gaveta y el cliente se lleva su comprobante.",
                    color = MutedText, style = MaterialTheme.typography.bodySmall
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Saldo pendiente", color = MutedText, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Q ${formatMoney(balance)}",
                        color = if (balance > 0) BrandColors.AccentDeep else PrimaryBlue,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            label = { Text("Notas internas") },
            modifier = Modifier.fillMaxWidth()
        )

        // ── Guardar ───────────────────────────────────────────────────
        val selectedCustomer = customers.firstOrNull { it.id == customerId }
        val resolvedList = resolveDefaultPriceList(type, selectedCustomer)
        Button(
            onClick = {
                val dateMillis = parseDateInput(deliveryDateStr)
                if (dateMillis <= 0L) return@Button
                val finalLines = lines
                    .filter { it.productId.isNotEmpty() && it.qty > 0 }
                    .map { it.toModel(resolvedList?.id ?: "") }
                if (finalLines.isEmpty()) return@Button

                val now = Clock.System.now().toEpochMilliseconds()
                val deliveryBranchName = branches.firstOrNull { it.id == deliveryBranchId }?.name ?: ""
                val producingBranchName = producingBranches.firstOrNull { it.id == producingBranchId }?.name ?: ""
                val shiftName = shifts.firstOrNull { it.id == shiftId }?.name ?: ""

                val cakeDetails = if (type == CustomerOrderType.CAKE) {
                    CakeOrderDetails(
                        cakeType = cakeType,
                        temperature = cakeTemp,
                        totalPortions = cakePortions.toIntOrNull() ?: 0,
                        biscocho = cakeBiscocho.trim(),
                        filling = cakeFilling.trim(),
                        extras = cakeExtras.trim(),
                        clientSignatureUrl = existing?.cakeDetails?.clientSignatureUrl ?: ""
                    )
                } else null

                val order = (existing ?: CustomerOrder()).copy(
                    type = type,
                    status = existing?.status ?: CustomerOrderStatus.PENDING,
                    customerId = customerId,
                    customerName = customerName.trim(),
                    customerPhone = customerPhone.trim(),
                    customerPhoneAlt = customerPhoneAlt.trim(),
                    recipientName = recipientName.trim().ifEmpty { customerName.trim() },
                    deliveryType = deliveryType,
                    deliveryBranchId = deliveryBranchId,
                    deliveryBranchName = deliveryBranchName,
                    deliveryAddress = deliveryAddress.trim(),
                    deliveryAddressReference = deliveryAddressRef.trim(),
                    deliveryDate = dateMillis,
                    deliveryTime = deliveryTime.trim(),
                    producingBranchId = producingBranchId,
                    producingBranchName = producingBranchName,
                    shiftId = shiftId,
                    shiftName = shiftName,
                    cakeDetails = cakeDetails,
                    priceListId = resolvedList?.id ?: "",
                    priceListName = resolvedList?.name ?: "",
                    subtotalAmount = subtotal,
                    riceSheetAmount = riceSheet.toDoubleOrNull() ?: 0.0,
                    fountainRentAmount = fountainRent.toDoubleOrNull() ?: 0.0,
                    extrasAmount = extrasAmount.toDoubleOrNull() ?: 0.0,
                    depositBaseAmount = depositBase.toDoubleOrNull() ?: 0.0,
                    advancePaidAmount = advance,
                    totalAmount = total,
                    balanceAmount = balance,
                    notes = notes.trim(),
                    createdByUserId = existing?.createdByUserId?.ifEmpty { userId } ?: userId,
                    createdByUserName = existing?.createdByUserName?.ifEmpty { userName } ?: userName,
                    updatedAt = now
                )
                onSave(order, finalLines)
            },
            enabled = !isSaving &&
                customerName.isNotBlank() &&
                deliveryBranchId.isNotEmpty() &&
                deliveryDateStr.isNotBlank() &&
                lines.any { it.productId.isNotEmpty() && it.qty > 0 },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            shape = RoundedCornerShape(10.dp)
        ) {
            if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            else Text(if (existing == null) "Crear pedido" else "Guardar cambios")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerPicker(
    customers: List<ExternalCustomer>,
    currentId: String,
    onSelect: (ExternalCustomer?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val current = customers.firstOrNull { it.id == currentId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = current?.name ?: "Cliente nuevo (walk-in)",
            onValueChange = {},
            readOnly = true,
            label = { Text("Cliente") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Cliente nuevo (walk-in)") },
                onClick = { onSelect(null); expanded = false }
            )
            customers.forEach { c ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(c.name)
                            if (c.businessName.isNotEmpty()) {
                                Text(
                                    c.businessName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedText
                                )
                            }
                        }
                    },
                    onClick = { onSelect(c); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchPicker(
    label: String,
    branches: List<com.bakery.erp.model.Branch>,
    currentId: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val current = branches.firstOrNull { it.id == currentId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = current?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            branches.forEach { b ->
                DropdownMenuItem(
                    text = { Text(b.name) },
                    onClick = { onSelect(b.id); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShiftPicker(
    shifts: List<com.bakery.erp.model.ProductionShift>,
    currentId: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val current = shifts.firstOrNull { it.id == currentId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = current?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Turno") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            shifts.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.name) },
                    onClick = { onSelect(s.id); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LineEditor(
    line: DraftLine,
    products: List<Product>,
    onChange: (DraftLine) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                var expanded by remember { mutableStateOf(false) }
                val product = products.firstOrNull { it.id == line.productId }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = product?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Producto *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        products.forEach { p ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(p.name)
                                        Text(
                                            "${p.unit} · Q ${formatMoney(p.salePrice)}" +
                                                if (!p.isInHouseProduced) " · reventa" else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MutedText
                                        )
                                    }
                                },
                                onClick = {
                                    onChange(
                                        line.copy(
                                            productId = p.id,
                                            productName = p.name,
                                            productUnit = p.unit,
                                            unitPrice = if (line.unitPrice == 0.0) p.salePrice else line.unitPrice,
                                            requiresProduction = p.isInHouseProduced
                                        )
                                    )
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "Quitar", tint = Color.Red.copy(alpha = 0.8f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = if (line.qty == 0.0) "" else trimZero(line.qty),
                    onValueChange = {
                        onChange(line.copy(qty = it.toDoubleOrNull() ?: 0.0))
                    },
                    label = { Text("Cantidad *") },
                    singleLine = true, modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = if (line.unitPrice == 0.0) "" else formatMoney(line.unitPrice),
                    onValueChange = {
                        onChange(line.copy(unitPrice = it.toDoubleOrNull() ?: 0.0))
                    },
                    label = { Text("Precio unit. Q") },
                    singleLine = true, modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = line.customSpec, onValueChange = { onChange(line.copy(customSpec = it)) },
                label = { Text("Especificación (más grande, sin azúcar...)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = PrimaryBlue,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

// ── DraftLine ───────────────────────────────────────────────────────────

private data class DraftLine(
    val id: String = "",
    val productId: String = "",
    val productName: String = "",
    val productUnit: String = "",
    val qty: Double = 0.0,
    val unitPrice: Double = 0.0,
    val customSpec: String = "",
    val requiresProduction: Boolean = true
) {
    fun toModel(priceListSource: String) = CustomerOrderLine(
        id = id,
        productId = productId,
        productName = productName,
        productUnit = productUnit,
        requestedQuantity = qty,
        unitPrice = unitPrice,
        priceSource = if (priceListSource.isEmpty()) "" else "LIST_FACTOR",
        customSpec = customSpec,
        requiresProduction = requiresProduction
    )

    companion object {
        fun from(line: CustomerOrderLine) = DraftLine(
            id = line.id,
            productId = line.productId,
            productName = line.productName,
            productUnit = line.productUnit,
            qty = line.requestedQuantity,
            unitPrice = line.unitPrice,
            customSpec = line.customSpec,
            requiresProduction = line.requiresProduction
        )
    }
}

// ── Helpers de fecha/dinero ─────────────────────────────────────────────

private fun formatDateInput(millis: Long): String {
    val dt = kotlin.time.Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val d = dt.dayOfMonth.toString().padStart(2, '0')
    val m = dt.monthNumber.toString().padStart(2, '0')
    return "$d/$m/${dt.year}"
}

private fun parseDateInput(text: String): Long {
    val parts = text.split("/", "-", ".").mapNotNull { it.trim().toIntOrNull() }
    if (parts.size != 3) return 0L
    val (d, m, y) = parts
    return runCatching {
        LocalDate(y, m, d).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    }.getOrNull() ?: 0L
}

private fun String.filterDecimal(): String {
    val cleaned = this.filter { it.isDigit() || it == '.' }
    val firstDot = cleaned.indexOf('.')
    if (firstDot == -1) return cleaned
    val before = cleaned.substring(0, firstDot + 1)
    val after = cleaned.substring(firstDot + 1).filter { it.isDigit() }
    return before + after
}
