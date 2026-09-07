package com.bakery.erp.presentation.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    viewModel: SaleHistoryViewModel
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
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
                item {
                    Text("Líneas", fontWeight = FontWeight.SemiBold,
                         modifier = Modifier.padding(top = 4.dp))
                }
                items(lines, key = { it.id }) { line -> SaleLineRow(line, canSeeValue) }
            }
        }
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
            Text("Método: ${SalePaymentMethod.label(sale.paymentMethod)}",
                 style = MaterialTheme.typography.bodySmall)
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
                if (sale.paymentMethod == SalePaymentMethod.CASH) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Recibido"); Text("Q ${sale.tenderedAmount.fmtMoney(2)}")
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
