package com.bakery.erp.presentation.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.PackagingFilter
import com.bakery.erp.model.Permission
import com.bakery.erp.model.chargedPackaging
import com.bakery.erp.model.filterByPackaging
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.ChipTone
import androidx.compose.runtime.saveable.rememberSaveable
import com.bakery.erp.model.Sale
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.util.fmtMoney
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleHistoryListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (saleId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: SaleHistoryViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val user = authState.user
    val canSeeValue = user?.hasPermission(Permission.VIEW_SALES_VALUE) == true
    val snackbar = remember { SnackbarHostState() }
    var packagingFilter by rememberSaveable { mutableStateOf(PackagingFilter.ALL) }
    // El filtro solo aparece si ese día se cobró algún empaque: en la panadería que no cobra
    // la bolsa no tiene nada que decir.
    val anyPackaging = state.sales.any { it.chargedPackaging }
    val shownSales = if (anyPackaging) state.sales.filterByPackaging(packagingFilter) else state.sales

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty() && user != null) viewModel.load(companyId, user)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de ventas", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                // Selector de sucursal
                if (state.branches.size > 1) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        state.branches.forEach { b ->
                            FilterChip(
                                selected = b.id == state.selectedBranchId,
                                onClick = { viewModel.selectBranch(companyId, b.id) },
                                label = { Text(b.name) }
                            )
                        }
                    }
                }
                // Date chevron
                DateChevronBar(
                    date = state.selectedDate,
                    onPrev = { state.selectedDate?.let { d -> viewModel.selectDate(companyId, d.minusDays(1)) } },
                    onNext = { state.selectedDate?.let { d -> viewModel.selectDate(companyId, d.plusDays(1)) } },
                    onResetToday = {
                        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                        viewModel.selectDate(companyId, today)
                    }
                )
                // KPIs
                KpiBar(sales = state.sales, canSeeValue = canSeeValue)
                if (anyPackaging) {
                    PackagingFilterRow(
                        sales = state.sales,
                        selected = packagingFilter,
                        onSelect = { packagingFilter = it }
                    )
                }
                HorizontalDivider()
                when {
                    state.isLoading && state.sales.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    shownSales.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Receipt, null,
                                     tint = MaterialTheme.colorScheme.outline,
                                     modifier = Modifier.size(64.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(if (state.sales.isEmpty()) "Sin ventas en esta fecha" else "Ninguna venta con este filtro",
                                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    else -> LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(shownSales, key = { it.id }) { sale ->
                            SaleCard(
                                sale = sale,
                                canSeeValue = canSeeValue,
                                onClick = { onNavigateToDetail(sale.id) }
                            )
                        }
                    }
                }
            }
        }
    }
    LoadingOverlay(visible = state.isLoading)
    }
}

@Composable
private fun DateChevronBar(
    date: LocalDate?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onResetToday: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronLeft, "Día anterior") }
        TextButton(onClick = onResetToday) {
            Text(date?.toString() ?: "—", fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, "Día siguiente") }
    }
}

@Composable
private fun KpiBar(sales: List<Sale>, canSeeValue: Boolean) {
    val count = sales.size
    val total = sales.sumOf { it.total }
    val avg = if (count > 0) total / count else 0.0
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        KpiTile("Ventas", "$count", Modifier.weight(1f))
        if (canSeeValue) {
            KpiTile("Total", "Q ${total.fmtMoney(2)}", Modifier.weight(1f))
            KpiTile("Promedio", "Q ${avg.fmtMoney(2)}", Modifier.weight(1f))
        }
    }
}

/** "Con empaque 32 de 45": cuántas ventas de panadería del día cobraron la bolsa. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackagingFilterRow(sales: List<Sale>, selected: PackagingFilter, onSelect: (PackagingFilter) -> Unit) {
    val counts = PackagingFilter.entries.associateWith { sales.filterByPackaging(it).size }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        PackagingFilter.entries.forEach { f ->
            FilterChip(
                selected = f == selected,
                onClick = { onSelect(f) },
                label = { Text("${f.label} (${counts[f] ?: 0})") }
            )
        }
    }
}

@Composable
private fun KpiTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun SaleCard(sale: Sale, canSeeValue: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${sale.lineCount} ítem(s) · ${sale.registerName}",
                     style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("Cajero: ${sale.cashierName}",
                     style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(SalePaymentMethod.label(sale.paymentMethod),
                     style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.outline)
                if (sale.invoiceStatus.isNotBlank() || sale.chargedPackaging) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (sale.invoiceStatus.isNotBlank()) SaleInvoiceChip(sale)
                        if (sale.chargedPackaging) {
                            BrandChip(
                                text = "Empaque ×${sale.packagingUnits.fmtMoney(0)}" +
                                    if (canSeeValue) " · Q ${sale.packagingAmount.fmtMoney(2)}" else "",
                                tone = ChipTone.INFO
                            )
                        }
                    }
                }
            }
            if (canSeeValue) {
                Text("Q ${sale.total.fmtMoney(2)}",
                     fontWeight = FontWeight.Bold,
                     style = MaterialTheme.typography.titleSmall,
                     color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// Helpers de fecha — kotlinx.datetime
private fun LocalDate.minusDays(days: Int): LocalDate {
    var y = year; var m = monthNumber; var d = dayOfMonth - days
    while (d < 1) {
        m -= 1
        if (m < 1) { m = 12; y -= 1 }
        d += daysInMonth(y, m)
    }
    return LocalDate(y, m, d)
}
private fun LocalDate.plusDays(days: Int): LocalDate {
    var y = year; var m = monthNumber; var d = dayOfMonth + days
    while (d > daysInMonth(y, m)) {
        d -= daysInMonth(y, m); m += 1
        if (m > 12) { m = 1; y += 1 }
    }
    return LocalDate(y, m, d)
}
private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    else -> 30
}
