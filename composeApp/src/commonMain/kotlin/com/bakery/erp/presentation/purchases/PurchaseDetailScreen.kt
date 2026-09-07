package com.bakery.erp.presentation.purchases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Permission
import com.bakery.erp.model.Purchase
import com.bakery.erp.model.PurchaseDisplayStatus
import com.bakery.erp.model.PurchaseLine
import com.bakery.erp.model.PurchasePayment
import com.bakery.erp.model.PurchasePaymentMethod
import com.bakery.erp.model.PurchaseStatus
import com.bakery.erp.model.User
import com.bakery.erp.model.balanceDue
import com.bakery.erp.model.captureDifference
import com.bakery.erp.model.daysUntilDue
import com.bakery.erp.model.displayStatus
import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.itemName
import com.bakery.erp.model.lineTotal
import com.bakery.erp.model.purchaseTotal
import com.bakery.erp.model.subtotal
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.ProductImage
import com.bakery.erp.ui.ZoomableImageDialog
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtTrim
import com.bakery.erp.util.fmtMoney
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseDetailScreen(
    purchaseId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: PurchaseViewModel
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val currentUser = authState.user

    LaunchedEffect(purchaseId, companyId) {
        if (companyId.isNotEmpty()) viewModel.loadPurchaseDetail(companyId, purchaseId)
    }
    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) { viewModel.clearDeleted(); onNavigateBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle de Compra", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (currentUser?.hasPermission(Permission.EDIT_PURCHASES) == true) {
                        IconButton(onClick = { onNavigateToEdit(purchaseId) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar compra")
                        }
                    }
                    if (currentUser?.hasPermission(Permission.DELETE_PURCHASES) == true) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar compra")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isDetailLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.selectedPurchase != null -> PurchaseDetailContent(
                    purchase = state.selectedPurchase!!,
                    lines = state.selectedPurchaseLines,
                    payments = state.selectedPurchasePayments,
                    currentUser = currentUser,
                    isProcessing = state.isProcessing,
                    onStatusChange = null,
                    onReceive = {
                        currentUser?.let { viewModel.receivePurchase(companyId, purchaseId, it) }
                    },
                    onAddPayment = { amount, date, method, reference, note ->
                        currentUser?.let {
                            viewModel.addPayment(companyId, purchaseId, amount, date, method, reference, note, it)
                        }
                    },
                    onDeletePayment = { paymentId -> viewModel.deletePayment(companyId, purchaseId, paymentId) },
                    onDueDateChange = { due -> viewModel.updateDueDate(companyId, purchaseId, due) },
                    onSettleInFull = { date, method, reference ->
                        currentUser?.let {
                            viewModel.settleInFull(companyId, purchaseId, date, method, reference, it)
                        }
                    }
                )
                else -> Text(
                    "No se encontró la compra",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    if (showDeleteConfirm) {
        val purchase = state.selectedPurchase
        val moved = purchase != null && purchase.status != PurchaseStatus.PENDING_RECEIPT
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("¿Eliminar esta compra?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Se borran la factura, sus líneas y sus abonos. No se puede deshacer.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    // Decir exactamente qué se revierte: borrar una compra recibida mueve
                    // inventario, y eso no puede ser una sorpresa.
                    Text(
                        text = if (moved) {
                            "Como ya había ingresado al inventario, también se descuenta lo " +
                                "que metió y queda un movimiento de corrección en el Kardex."
                        } else {
                            "Todavía no había ingresado al inventario, así que el stock no cambia."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (purchase != null && purchase.paidAmount > 0.01) {
                        Text(
                            "Ojo: esta factura tiene Q${purchase.paidAmount.fmtMoney()} en abonos " +
                                "registrados que también se borrarán.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFB3261E)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        currentUser?.let { viewModel.deletePurchase(companyId, purchaseId, it) }
                    },
                    enabled = !state.isProcessing
                ) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun PurchaseDetailContent(
    purchase: Purchase,
    lines: List<PurchaseLine>,
    payments: List<PurchasePayment> = emptyList(),
    currentUser: User? = null,
    isProcessing: Boolean = false,
    onStatusChange: ((String) -> Unit)? = null,
    onReceive: (() -> Unit)? = null,
    onAddPayment: ((Double, Long, String, String, String?) -> Unit)? = null,
    onDeletePayment: ((String) -> Unit)? = null,
    onDueDateChange: ((Long) -> Unit)? = null,
    onSettleInFull: ((Long, String, String) -> Unit)? = null,
    /** Solo lo manda el panel lateral de la lista; la pantalla completa edita desde su TopAppBar. */
    onEdit: (() -> Unit)? = null
) {
    val now = remember(purchase.id) { Clock.System.now().toEpochMilliseconds() }
    val total = if (purchase.totalAmount > 0.0) purchase.totalAmount else lines.purchaseTotal()
    val status = purchase.copy(totalAmount = total).displayStatus(now)
    val balance = (total - purchase.paidAmount).coerceAtLeast(0.0)
    val canPay = currentUser?.hasPermission(Permission.MANAGE_PURCHASE_PAYMENTS) == true
    val canReceive = currentUser?.hasPermission(Permission.RECEIVE_STOCK) == true

    var showPaymentDialog by remember { mutableStateOf(false) }
    var showSettleDialog by remember { mutableStateOf(false) }
    var showDuePicker by remember { mutableStateOf(false) }
    var paymentToDelete by remember { mutableStateOf<PurchasePayment?>(null) }
    var showFullPhoto by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onEdit != null && currentUser?.hasPermission(Permission.EDIT_PURCHASES) == true) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Editar compra")
                }
            }
        }

        // ── Header chips: estado + factura + sucursal ────────────────────────
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            PurchaseStatusPill(status)
            if (!purchase.invoiceNumber.isNullOrEmpty()) {
                InfoChip("Factura: ${purchase.invoiceNumber}", MaterialTheme.colorScheme.primaryContainer)
            }
            InfoChip(purchase.branchName.ifEmpty { "Sin sucursal" }, MaterialTheme.colorScheme.secondaryContainer)
        }

        // ── Pendiente de recibir: el stock todavía no entró ──────────────────
        if (purchase.status == PurchaseStatus.PENDING_RECEIPT) {
            Card(
                colors = CardDefaults.cardColors(containerColor = BrandColors.PrimaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Esta compra todavía no entró al inventario",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = BrandColors.Primary
                    )
                    Text(
                        "El stock y el Kardex se actualizan hasta que marques la mercadería como recibida.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandColors.Primary
                    )
                    if (onReceive != null && canReceive) {
                        Button(
                            onClick = onReceive,
                            enabled = !isProcessing,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Marcar como recibida e ingresar al stock") }
                    }
                }
            }
        }

        // ── Resumen económico ────────────────────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            "TOTAL DE LA FACTURA",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Q${total.fmtMoney()}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = BrandColors.Primary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (balance > 0.01) "SALDO" else "SALDADA",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Q${balance.fmtMoney()}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (balance > 0.01) BrandColors.AccentDeep else Color(0xFF1B5E20)
                        )
                    }
                }

                if (purchase.taxAmount > 0.01) {
                    Text(
                        "Base Q${purchase.subtotal.fmtMoney()} · IVA " +
                            "${purchase.taxRatePercent.fmtTrim(0)}% Q${purchase.taxAmount.fmtMoney()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Solo se muestra cuando NO cuadró: dejar constancia del descuadre sirve para
                // encontrarlo después; repetir el total cuando coincide es ruido.
                purchase.captureDifference()?.takeIf { kotlin.math.abs(it) > 0.01 }?.let { diff ->
                    Text(
                        "La factura dice Q${purchase.declaredTotal.fmtMoney()} — " +
                            (if (diff > 0) "se capturó Q${diff.fmtMoney()} de más" 
                             else "faltan Q${(-diff).fmtMoney()} por capturar"),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFB3261E)
                    )
                }

                if (purchase.paidAmount > 0.01 && total > 0.0) {
                    LinearProgressIndicator(
                        progress = { (purchase.paidAmount / total).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Abonado Q${purchase.paidAmount.fmtMoney()} de Q${total.fmtMoney()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "VENCIMIENTO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val dueDays = purchase.daysUntilDue(now)
                        Text(
                            text = when {
                                purchase.dueDate <= 0L -> "Contado — sin crédito"
                                else -> formatPurchaseDate(purchase.dueDate) + when {
                                    balance <= 0.01 -> ""
                                    dueDays == null -> ""
                                    dueDays < 0 -> "  ·  vencida hace ${-dueDays} días"
                                    dueDays == 0 -> "  ·  vence hoy"
                                    else -> "  ·  en $dueDays días"
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (status == PurchaseDisplayStatus.OVERDUE) Color(0xFFB3261E)
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (onDueDateChange != null && canPay) {
                        IconButton(onClick = { showDuePicker = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Cambiar vencimiento")
                        }
                    }
                }

                if (canPay && balance > 0.01 && purchase.status != PurchaseStatus.PENDING_RECEIPT) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Pagar la factura entera es el caso normal; el abono parcial es la
                        // excepción. Por eso el primero es el botón lleno y el otro va outline.
                        if (onSettleInFull != null) {
                            Button(
                                onClick = { showSettleDialog = true },
                                enabled = !isProcessing,
                                modifier = Modifier.weight(1f)
                            ) { Text("Saldar Q${balance.fmtMoney()}") }
                        }
                        if (onAddPayment != null) {
                            OutlinedButton(
                                onClick = { showPaymentDialog = true },
                                enabled = !isProcessing,
                                modifier = Modifier.weight(1f)
                            ) { Text("Abonar") }
                        }
                    }
                }
            }
        }

        // ── Estado manual (solo donde se pasa el callback) ───────────────────
        if (onStatusChange != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "ESTADO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            PurchaseStatus.PENDING_RECEIPT to "Pendiente",
                            PurchaseStatus.RECEIVED_PENDING_PAYMENT to "Pte. pago",
                            PurchaseStatus.PAID to "Pagado"
                        ).forEach { (s, label) ->
                            if (purchase.status == s) {
                                Button(
                                    onClick = {},
                                    modifier = Modifier.weight(1f),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) { Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                            } else {
                                OutlinedButton(
                                    onClick = { onStatusChange(s) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }
        }

        // ── Info general ─────────────────────────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailRow("Proveedor", purchase.supplierName.ifEmpty { "Sin proveedor" })
                DetailRow("Registró", purchase.receivedByName.ifEmpty { "No registrado" })
                DetailRow("Fecha", formatPurchaseDate(purchase.date))
                if (purchase.receivedAt > 0L && purchase.receivedAt != purchase.date) {
                    DetailRow("Recibida", formatPurchaseDate(purchase.receivedAt))
                }
                if (purchase.paymentTermsDays > 0) {
                    DetailRow("Crédito", "${purchase.paymentTermsDays} días")
                }
                if (!purchase.notes.isNullOrEmpty()) {
                    DetailRow("Observaciones", purchase.notes)
                }
            }
        }

        if (purchase.invoicePhotoUrl.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "FACTURA FÍSICA",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Tocá la foto para verla en grande y hacer zoom",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    ProductImage(
                        imageUrl = purchase.invoicePhotoUrl,
                        contentDescription = "Foto de la factura",
                        modifier = Modifier.fillMaxWidth().height(280.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showFullPhoto = true },
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        // ── Abonos ───────────────────────────────────────────────────────────
        if (payments.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = "ABONOS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    payments.forEachIndexed { index, payment ->
                        PaymentRow(
                            payment = payment,
                            onDelete = if (canPay && onDeletePayment != null) {
                                { paymentToDelete = payment }
                            } else null
                        )
                        if (index < payments.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }

        // ── Líneas ───────────────────────────────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DETALLE DE LA FACTURA",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            text = "${lines.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                lines.forEachIndexed { index, line ->
                    PurchaseLineRow(line)
                    if (index < lines.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showFullPhoto && purchase.invoicePhotoUrl.isNotEmpty()) {
        ZoomableImageDialog(
            imageUrl = purchase.invoicePhotoUrl,
            title = purchase.invoiceNumber?.let { "Factura $it" } ?: "Factura",
            onDismiss = { showFullPhoto = false }
        )
    }

    if (showSettleDialog && onSettleInFull != null) {
        SettleInFullDialog(
            balance = balance,
            supplierName = purchase.supplierName,
            onDismiss = { showSettleDialog = false },
            onConfirm = { date, method, reference ->
                showSettleDialog = false
                onSettleInFull(date, method, reference)
            }
        )
    }

    if (showPaymentDialog && onAddPayment != null) {
        AddPaymentDialog(
            balance = balance,
            onDismiss = { showPaymentDialog = false },
            onConfirm = { amount, date, method, reference, note ->
                showPaymentDialog = false
                onAddPayment(amount, date, method, reference, note)
            }
        )
    }

    if (showDuePicker && onDueDateChange != null) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = purchase.dueDate.takeIf { it > 0L } ?: purchase.date
        )
        DatePickerDialog(
            onDismissRequest = { showDuePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let(onDueDateChange)
                    showDuePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showDuePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = dpState) }
    }

    paymentToDelete?.let { payment ->
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            title = { Text("¿Eliminar el abono?") },
            text = {
                Text(
                    "Se quitará el abono de Q${payment.amount.fmtMoney()} del " +
                        "${formatPurchaseDate(payment.date)} y el saldo volverá a subir."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePayment?.invoke(payment.id)
                    paymentToDelete = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { paymentToDelete = null }) { Text("Cancelar") } }
        )
    }
}

// ── Diálogo de abono ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddPaymentDialog(
    balance: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double, Long, String, String, String?) -> Unit
) {
    // Se prellena con el saldo completo porque el caso habitual es pagar la factura entera;
    // el abono parcial es la excepción y se escribe encima.
    var amountText by remember { mutableStateOf(balance.fmt(2)) }
    var method by remember { mutableStateOf(PurchasePaymentMethod.CASH) }
    var reference by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val amount = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
    val exceedsBalance = amount > balance + 0.01
    val amountValid = amount > 0.0 && !exceedsBalance
    val referenceMissing = PurchasePaymentMethod.requiresReference(method) && reference.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar abono") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Saldo pendiente: Q${balance.fmtMoney()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Monto (Q)") },
                    isError = amountText.isNotBlank() && !amountValid,
                    supportingText = {
                        Text(
                            when {
                                amountText.isBlank() -> "Máximo Q${balance.fmtMoney()}"
                                exceedsBalance -> "No puede pasar del saldo: máximo Q${balance.fmtMoney()}"
                                amount <= 0.0 -> "El monto debe ser mayor a cero"
                                else -> "Máximo Q${balance.fmtMoney()}"
                            }
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (balance > 0.0 && amount != balance) {
                    TextButton(onClick = { amountText = balance.fmt(2) }) {
                        Text(if (exceedsBalance) "Ajustar al saldo" else "Pagar todo el saldo")
                    }
                }

                Text("Forma de pago", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PurchasePaymentMethod.all.forEach { m ->
                        FilterChip(
                            selected = method == m,
                            onClick = { method = m },
                            label = { Text(PurchasePaymentMethod.label(m)) }
                        )
                    }
                }

                if (PurchasePaymentMethod.requiresReference(method)) {
                    OutlinedTextField(
                        value = reference,
                        onValueChange = { reference = it },
                        label = { Text(if (method == PurchasePaymentMethod.CHECK) "No. de cheque" else "No. de boleta / referencia") },
                        isError = referenceMissing,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Fecha del pago: ${formatPurchaseDate(date)}")
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Nota (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(amount, date, method, reference, note.ifBlank { null }) },
                enabled = amountValid && !referenceMissing
            ) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )

    if (showDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    date = dpState.selectedDateMillis ?: date
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = dpState) }
    }
}

/**
 * Saldar la factura entera. Pide forma de pago y fecha —no el monto, que es el saldo— porque
 * "¿ya le pagué a Molinos?" casi nunca se contesta con un número sino con "sí, el cheque 1042
 * del martes". Registrar el abono en vez de solo marcar PAGADA es además lo que mantiene
 * coherente el saldo: `paidAmount` es la suma de los abonos, y un estado sin abono detrás se
 * revertiría en cuanto algo tocara la factura.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SettleInFullDialog(
    balance: Double,
    supplierName: String,
    onDismiss: () -> Unit,
    onConfirm: (Long, String, String) -> Unit
) {
    var method by remember { mutableStateOf(PurchasePaymentMethod.CASH) }
    var reference by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val referenceMissing = PurchasePaymentMethod.requiresReference(method) && reference.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saldar la cuenta") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Se registra el pago completo de Q${balance.fmtMoney()} a $supplierName y la " +
                        "factura queda saldada.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("Forma de pago", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PurchasePaymentMethod.all.forEach { m ->
                        FilterChip(
                            selected = method == m,
                            onClick = { method = m },
                            label = { Text(PurchasePaymentMethod.label(m)) }
                        )
                    }
                }
                if (PurchasePaymentMethod.requiresReference(method)) {
                    OutlinedTextField(
                        value = reference,
                        onValueChange = { reference = it },
                        label = {
                            Text(if (method == PurchasePaymentMethod.CHECK) "No. de cheque"
                                 else "No. de boleta / referencia")
                        },
                        isError = referenceMissing,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Fecha del pago: ${formatPurchaseDate(date)}")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(date, method, reference) },
                enabled = !referenceMissing
            ) { Text("Saldar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )

    if (showDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    date = dpState.selectedDateMillis ?: date
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = dpState) }
    }
}

// ── Piezas ───────────────────────────────────────────────────────────────────

@Composable
private fun InfoChip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PaymentRow(payment: PurchasePayment, onDelete: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Q${payment.amount.fmtMoney()}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                buildString {
                    append(formatPurchaseDate(payment.date))
                    append(" · ")
                    append(PurchasePaymentMethod.label(payment.method))
                    if (payment.reference.isNotBlank()) append(" ${payment.reference}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!payment.notes.isNullOrEmpty()) {
                Text(
                    payment.notes,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar abono",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(110.dp)
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PurchaseLineRow(line: PurchaseLine) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(line.itemName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (line.packageDescription.isNotEmpty()) {
            Text(line.packageDescription, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            val qtyText = if (line.packagesReceived > 0 && line.packageDescription.isNotEmpty()) {
                "${formatPurchaseQty(line.packagesReceived)} × ${line.packageDescription}"
            } else {
                "${formatPurchaseQty(line.quantityReceived)} ${line.ingredientUnit}"
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(qtyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (line.expiryDate > 0L) {
                    Text(
                        "Vence ${formatPurchaseDate(line.expiryDate)}" +
                            (if (line.lotCode.isNotBlank()) " · lote ${line.lotCode}" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandColors.AccentDeep
                    )
                }
            }
            val lineTotal = line.lineTotal
            if (lineTotal > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    if (line.packageCost > 0) {
                        Text(
                            text = "Q ${line.packageCost.fmt(2)} / u.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Text(
                        text = "Q ${lineTotal.fmt(2)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

internal fun formatPurchaseQty(value: Double): String =
    if (value == value.toLong().toDouble()) "${value.toLong()}" else value.fmt(2)
