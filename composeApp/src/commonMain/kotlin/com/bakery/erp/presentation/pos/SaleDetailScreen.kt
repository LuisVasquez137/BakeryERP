package com.bakery.erp.presentation.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Permission
import com.bakery.erp.model.Sale
import com.bakery.erp.model.SaleLine
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.model.effectivePaymentBreakdown
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.util.fmtMoney

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleDetailScreen(
    saleId: String,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: SaleHistoryViewModel,
    /** Para elegir la térmica la primera vez, igual que en el cobro. */
    bluetoothPrinterManager: com.bakery.erp.domain.BluetoothPrinterManager? = null
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val canSeeValue = authState.user?.hasPermission(Permission.VIEW_SALES_VALUE) == true
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(companyId, saleId) {
        if (companyId.isNotEmpty()) viewModel.loadSaleDetail(companyId, saleId)
    }
    DisposableEffect(saleId) { onDispose { viewModel.clearSelected() } }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.notice) {
        state.notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() }
    }
    // Anular una factura es corregir un documento fiscal ya emitido: mismo permiso que el
    // servidor exige, así el botón no aparece para quien igual recibiría un "sin permiso".
    val canFixInvoice = authState.user?.hasPermission(Permission.VOID_SALE) == true
    var voidOpen by remember { mutableStateOf(false) }
    var reissueOpen by remember { mutableStateOf(false) }
    var invoiceLaterOpen by remember { mutableStateOf(false) }
    var btPickerOpen by remember { mutableStateOf(false) }
    val currentSale = state.selectedSale
    val branchHasNit = state.branches.firstOrNull { it.id == currentSale?.branchId }?.felProfileId?.isNotBlank() == true
    // Facturar después es el mismo acto que facturar al cobrar: pide el permiso de cobrar.
    val canInvoiceLater = currentSale != null &&
        authState.user?.hasPermission(Permission.OPERATE_POS) == true &&
        com.bakery.erp.model.felCanInvoiceLater(currentSale, branchHasNit)
    fun print() {
        val s = state.selectedSale ?: return
        val manager = bluetoothPrinterManager
        // Primera vez en este aparato: elegir la térmica, y al configurarla se imprime.
        if (manager != null && manager.isPlatformSupported && manager.getSavedConfig() == null) btPickerOpen = true
        else viewModel.printSale(companyId, s)
    }

    val sale = state.selectedSale
    val lines = state.selectedSaleLines

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle de venta", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    if (viewModel.canPrint && state.selectedSale != null) {
                        IconButton(onClick = { print() }, enabled = !state.isPrinting) {
                            if (state.isPrinting) CircularProgressIndicator(
                                Modifier.size(20.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            else Icon(
                                Icons.Default.Print,
                                // Con factura sale la representación gráfica completa, QR incluido.
                                if (state.selectedSale?.invoiceStatus == com.bakery.erp.model.FelInvoiceStatus.CERTIFICADO)
                                    "Reimprimir factura" else "Reimprimir ticket"
                            )
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
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (sale == null || state.isDetailLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { SaleHeaderCard(sale, canSeeValue) }
                if (canInvoiceLater) item {
                    Button(
                        onClick = { invoiceLaterOpen = true },
                        enabled = !state.isInvoiceBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Facturar esta venta") }
                }
                if (canFixInvoice) {
                    when (sale.invoiceStatus) {
                        com.bakery.erp.model.FelInvoiceStatus.CERTIFICADO -> item {
                            OutlinedButton(
                                onClick = { voidOpen = true },
                                enabled = !state.isInvoiceBusy,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Anular factura") }
                        }
                        com.bakery.erp.model.FelInvoiceStatus.ANULADO,
                        com.bakery.erp.model.FelInvoiceStatus.RECHAZADO -> item {
                            OutlinedButton(
                                onClick = { reissueOpen = true },
                                enabled = !state.isInvoiceBusy,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Facturar de nuevo") }
                        }
                        else -> Unit
                    }
                }
                item {
                    Text("Líneas", fontWeight = FontWeight.SemiBold,
                         modifier = Modifier.padding(top = 4.dp))
                }
                items(lines, key = { it.id }) { line -> SaleLineRow(line, canSeeValue) }
            }
        }
        com.bakery.erp.ui.LoadingOverlay(
            visible = state.isInvoiceBusy,
            message = "Hablando con la SAT…"
        )
    }

    val lookup: suspend (String) -> Result<String?> = { nit -> viewModel.lookupNit(companyId, sale?.branchId.orEmpty(), nit) }
    if (voidOpen && sale != null) {
        VoidInvoiceDialog(
            sale = sale,
            lookupNit = lookup,
            onDismiss = { voidOpen = false },
            onConfirm = { reason, reissueTo ->
                voidOpen = false
                viewModel.voidInvoice(companyId, sale, reason, reissueTo)
            }
        )
    }
    if (invoiceLaterOpen && sale != null) {
        ReissueInvoiceDialog(
            sale = sale,
            lookupNit = lookup,
            onDismiss = { invoiceLaterOpen = false },
            onConfirm = { to ->
                invoiceLaterOpen = false
                viewModel.reissueInvoice(companyId, sale, to)
            },
            title = "Facturar esta venta",
            message = "Se emite la factura de esta venta con la fecha de hoy. El cobro no cambia."
        )
    }
    if (btPickerOpen && bluetoothPrinterManager != null) {
        BluetoothPrinterPickerDialog(
            manager = bluetoothPrinterManager,
            onConfigured = {
                btPickerOpen = false
                state.selectedSale?.let { viewModel.printSale(companyId, it) }
            },
            onDismiss = { btPickerOpen = false }
        )
    }
    if (reissueOpen && sale != null) {
        ReissueInvoiceDialog(
            sale = sale,
            lookupNit = lookup,
            onDismiss = { reissueOpen = false },
            onConfirm = { to ->
                reissueOpen = false
                viewModel.reissueInvoice(companyId, sale, to)
            }
        )
    }
}

@Composable
private fun SaleHeaderCard(sale: Sale, canSeeValue: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${sale.branchName} · ${sale.registerName}", fontWeight = FontWeight.SemiBold)
            Text("Cajero: ${sale.cashierName}",
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Método: ${SalePaymentMethod.label(sale.paymentMethod)}" +
                    // Pago dividido: cuánto entró por cada método.
                    if (sale.paymentMethod == SalePaymentMethod.MIXED)
                        " · " + com.bakery.erp.model.paymentPartsLabel(sale.effectivePaymentBreakdown) else "",
                 style = MaterialTheme.typography.bodySmall)
            if (sale.resellerName.isNotBlank()) {
                Text("Revendedor: ${sale.resellerName}" +
                        if (sale.resellerPriceListName.isNotBlank()) " · lista ${sale.resellerPriceListName}" else "",
                     style = MaterialTheme.typography.bodySmall,
                     color = BrandColors.Primary)
            }
            if (sale.invoiceStatus.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                SaleInvoiceChip(sale)
                if (sale.invoiceReceptorName.isNotBlank()) {
                    Text(
                        "A nombre de ${sale.invoiceReceptorName}" +
                            if (sale.invoiceReceptorId.isNotBlank() && sale.invoiceReceptorId != "CF") " · NIT ${sale.invoiceReceptorId}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (canSeeValue) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal"); Text("Q ${sale.subtotal.fmtMoney(2)}")
                }
                if (sale.discount > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Descuento"); Text("- Q ${sale.discount.fmtMoney(2)}")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", fontWeight = FontWeight.SemiBold)
                    Text("Q ${sale.total.fmtMoney(2)}", fontWeight = FontWeight.Bold)
                }
                // En un pago dividido, lo recibido y el vuelto son de la parte en efectivo.
                if (sale.paymentMethod == SalePaymentMethod.CASH ||
                    (sale.paymentMethod == SalePaymentMethod.MIXED && sale.tenderedAmount > 0.004)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (sale.paymentMethod == SalePaymentMethod.MIXED) "Efectivo recibido" else "Recibido")
                        Text("Q ${sale.tenderedAmount.fmtMoney(2)}")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Vuelto"); Text("Q ${sale.changeAmount.fmtMoney(2)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun SaleLineRow(line: SaleLine, canSeeValue: Boolean) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(line.productName, fontWeight = FontWeight.SemiBold)
                Text("${line.quantity.fmtMoney(0)} × Q ${line.unitPrice.fmtMoney(2)}",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!line.stockDeducted) {
                    Text("Express — pendiente de asignación al cierre",
                         style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.tertiary)
                }
            }
            if (canSeeValue) {
                Text("Q ${line.subtotal.fmtMoney(2)}", fontWeight = FontWeight.Bold)
            }
        }
    }
}
