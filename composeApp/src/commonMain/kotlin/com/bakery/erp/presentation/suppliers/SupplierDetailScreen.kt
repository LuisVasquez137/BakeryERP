package com.bakery.erp.presentation.suppliers

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Purchase
import com.bakery.erp.model.PurchaseLineKind
import com.bakery.erp.model.Supplier
import com.bakery.erp.model.SupplierItemHistory
import com.bakery.erp.model.SupplierPurchaseTotals
import com.bakery.erp.model.balanceDue
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtMoney
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierDetailScreen(
    supplierId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (supplierId: String) -> Unit,
    onNavigateToPurchase: (purchaseId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: SupplierViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val supplier = state.supplierList.find { it.id == supplierId }

    // El historial se pide al abrir la ficha: es la mitad de para qué se entra acá. Solo lee
    // ENCABEZADOS, que ya traen total y nombres denormalizados; las cantidades viven en las
    // líneas y se cargan aparte, a pedido.
    LaunchedEffect(companyId, supplierId) {
        viewModel.loadSupplierHistory(companyId, supplierId)
    }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) { viewModel.clearDeleted(); onNavigateBack() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar proveedor") },
            text = {
                Text("¿Estás seguro que deseas eliminar \"${supplier?.company}\"? Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        supplier?.let { viewModel.deleteSupplier(companyId, it.id) }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = {
                    Text(
                        text = supplier?.company ?: "Detalle",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { supplier?.let { onNavigateToEdit(it.id) } }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                supplier == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Proveedor no encontrado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> SupplierDetailContent(
                    supplier = supplier,
                    purchases = state.supplierPurchases,
                    totals = state.supplierTotals,
                    isLoadingHistory = state.isLoadingHistory,
                    items = state.itemHistory,
                    isLoadingItems = state.isLoadingItems,
                    itemsLoaded = state.itemsFor == supplierId && !state.isLoadingItems,
                    onLoadItems = { viewModel.loadSupplierItems(companyId, supplierId) },
                    onOpenPurchase = onNavigateToPurchase
                )
            }
        }
    }
}

@Composable
private fun SupplierDetailContent(
    supplier: Supplier,
    purchases: List<Purchase>,
    totals: SupplierPurchaseTotals,
    isLoadingHistory: Boolean,
    items: List<SupplierItemHistory>,
    isLoadingItems: Boolean,
    itemsLoaded: Boolean,
    onLoadItems: () -> Unit,
    onOpenPurchase: (String) -> Unit
) {
    var showAllPurchases by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = supplier.company,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        StatusBadge(supplier.active)

        Spacer(Modifier.height(12.dp))

        // ── Contacto ─────────────────────────────────────────────────────────
        DetailSection("Contacto")
        if (supplier.salesperson.isNotEmpty()) DetailRow("Vendedor", supplier.salesperson)
        if (supplier.phone.isNotEmpty()) DetailRow("Teléfono", supplier.phone)
        if (!supplier.address.isNullOrEmpty()) DetailRow("Dirección", supplier.address)

        // ── Notas ────────────────────────────────────────────────────────────
        if (!supplier.notes.isNullOrEmpty()) {
            Spacer(Modifier.height(4.dp))
            DetailSection("Notas")
            Text(
                text = supplier.notes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // -- Lo que se le compra ---------------------------------------------
        Spacer(Modifier.height(8.dp))
        DetailSection("Compras")
        when {
            isLoadingHistory -> Text(
                "Buscando sus compras...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            purchases.isEmpty() -> Text(
                "Todavia no hay compras registradas a este proveedor.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> {
                DetailRow("Facturas", "${totals.purchaseCount}")
                DetailRow("Total comprado", "Q ${totals.totalAmount.fmtMoney()}")
                if (totals.balanceDue > 0.01) {
                    DetailRow("Le debo", "Q ${totals.balanceDue.fmtMoney()}")
                }
                if (totals.lastPurchaseDate > 0L) {
                    DetailRow("Ultima compra", formatHistoryDate(totals.lastPurchaseDate))
                }

                Spacer(Modifier.height(8.dp))
                // Que se le compra: es lo que no se podia contestar sin abrir factura por
                // factura. Va detras de un boton porque cuesta una lectura POR FACTURA; el
                // resto de la ficha sale de los encabezados, que ya estaban leidos.
                if (!itemsLoaded && !isLoadingItems) {
                    OutlinedButton(onClick = onLoadItems, modifier = Modifier.fillMaxWidth()) {
                        Text("Ver que le compro y cuanto")
                    }
                }
                if (isLoadingItems) {
                    Text(
                        "Sumando las cantidades de cada factura...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (itemsLoaded) {
                    if (items.isEmpty()) {
                        Text(
                            "Sus facturas no traen renglones con cantidad.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        items.forEach { ItemHistoryRow(it) }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Historial",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                val shown = if (showAllPurchases) purchases else purchases.take(5)
                shown.forEach { p -> PurchaseHistoryRow(p, onClick = { onOpenPurchase(p.id) }) }
                if (purchases.size > shown.size) {
                    TextButton(onClick = { showAllPurchases = true }) {
                        Text("Ver las ${purchases.size} compras")
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** "200 bolsas - 4 compras - ultima a Q 2.00". */
@Composable
private fun ItemHistoryRow(item: SupplierItemHistory) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Q ${item.totalAmount.fmtMoney()}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            buildString {
                // La cantidad solo se muestra si alguien la tecleó. Sin unidad y con un "1"
                // por compra —el gasto anotado sin cantidad— el número no dice nada y se lee
                // como si fueran dos cosas distintas.
                val countedQty = item.unit.isNotEmpty() || item.totalQuantity > item.timesBought
                if (countedQty) {
                    append(item.totalQuantity.fmt(2).trimEnd('0').trimEnd('.'))
                    if (item.unit.isNotEmpty()) append(" ${item.unit}")
                    append(" · ")
                }
                append("${item.timesBought} ")
                append(if (item.timesBought == 1) "compra" else "compras")
                if (item.lastUnitCost > 0) append(" · ultima a Q ${item.lastUnitCost.fmt(2)}")
                if (item.kind == PurchaseLineKind.SERVICE) append(" · sin inventario")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PurchaseHistoryRow(purchase: Purchase, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                formatHistoryDate(purchase.date) +
                    (purchase.invoiceNumber?.takeIf { it.isNotEmpty() }?.let { " · N° $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                purchase.itemNames.take(3).joinToString(", ").ifEmpty { purchase.branchName },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "Q ${purchase.totalAmount.fmtMoney()}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (purchase.balanceDue > 0.01) {
                Text(
                    "debe Q ${purchase.balanceDue.fmtMoney()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandColors.AccentDeep
                )
            }
        }
    }
}

/** "12 sep". Con ano solo cuando no es el corriente: en el ano en curso estorba. */
private fun formatHistoryDate(millis: Long): String {
    if (millis <= 0L) return "-"
    val tz = TimeZone.currentSystemDefault()
    val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz).date
    val today = Clock.System.now().toLocalDateTime(tz).date
    val month = listOf(
        "ene", "feb", "mar", "abr", "may", "jun",
        "jul", "ago", "sep", "oct", "nov", "dic"
    )[date.monthNumber - 1]
    return if (date.year == today.year) "${date.dayOfMonth} $month"
    else "${date.dayOfMonth} $month ${date.year}"
}

@Composable
private fun DetailSection(title: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun StatusBadge(active: Boolean) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = if (active) "Activo" else "Inactivo",
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
