package com.bakery.erp.presentation.credit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.domain.BluetoothPrinterManager
import com.bakery.erp.domain.DocumentPrinter
import com.bakery.erp.model.CreditAging
import com.bakery.erp.model.creditAgingOf
import com.bakery.erp.model.openChargeList
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.production.DocumentPrintButton
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.CreditAgingRow
import com.bakery.erp.util.creditAgingDocument
import com.bakery.erp.util.fmtMoney
import kotlin.time.Clock

/**
 * Antigüedad de saldos: quién debe y desde hace cuánto. La antigüedad sale de los cargos que
 * siguen sin pagar (los pagos se aplican a lo más viejo primero), así que "31–60" quiere decir
 * que hay compras de hace un mes o dos que de verdad no se han pagado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditAgingReportScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CreditViewModel,
    documentPrinter: DocumentPrinter,
    bluetoothPrinterManager: BluetoothPrinterManager
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val snackbar = remember { SnackbarHostState() }
    // 0 = todas, 1 = clientes, 2 = colaboradores
    var filter by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(companyId) { if (companyId.isNotEmpty()) viewModel.load(companyId) }

    val now = Clock.System.now().toEpochMilliseconds()
    val rows = state.accounts
        .filter { it.balance > 0.005 }
        .filter { when (filter) { 1 -> !it.isEmployee; 2 -> it.isEmployee; else -> true } }
        .map { CreditAgingRow(it, creditAgingOf(it.openChargeList, now)) }
        // Lo más viejo y más grande arriba: es lo que hay que ir a cobrar primero.
        .sortedWith(compareByDescending<CreditAgingRow> { it.aging.over90 + it.aging.days61to90 }
            .thenByDescending { it.aging.overdue30 }.thenByDescending { it.aging.total })
    val total = rows.fold(CreditAging()) { acc, r -> acc + r.aging }
    val title = when (filter) { 1 -> "Antigüedad de saldos — clientes"; 2 -> "Antigüedad de saldos — colaboradores"; else -> "Antigüedad de saldos" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Antigüedad de saldos", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    DocumentPrintButton(
                        documentPrinter = documentPrinter,
                        bluetoothPrinterManager = bluetoothPrinterManager,
                        snackbarHostState = snackbar,
                        contentDescription = "Imprimir reporte",
                        emptyMessage = "Nadie debe nada: no hay qué imprimir.",
                        buildDocument = { rows.takeIf { it.isNotEmpty() }?.let { creditAgingDocument(it, now, title) } }
                    )
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
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Todas", "Clientes", "Colaboradores").forEachIndexed { i, label ->
                        FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label) })
                    }
                }
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = BrandColors.PrimaryContainer)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Total por cobrar", color = BrandColors.Primary, modifier = Modifier.weight(1f))
                            Text("Q ${total.total.fmtMoney(2)}", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge, color = BrandColors.Primary)
                        }
                        AgingBuckets(total)
                    }
                }
                when {
                    state.isLoading && state.accounts.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    rows.isEmpty() ->
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("Nadie debe nada en este grupo.", color = BrandColors.MutedSlate)
                        }
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(rows, key = { it.account.id }) { r ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onNavigateToDetail(r.account.id) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(1.dp)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(r.account.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("Q ${r.aging.total.fmtMoney(2)}", fontWeight = FontWeight.Bold,
                                            color = if (r.aging.overdue30 > 0.004) BrandColors.AccentDeep else BrandColors.Primary)
                                    }
                                    AgingBuckets(r.aging)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgingBuckets(a: CreditAging) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("0–30" to a.upTo30, "31–60" to a.days31to60, "61–90" to a.days61to90, "+90" to a.over90)
            .forEachIndexed { i, (label, v) ->
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate)
                    Text(
                        if (v > 0.004) "Q ${v.fmtMoney(2)}" else "—",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (i > 0 && v > 0.004) FontWeight.Bold else FontWeight.Normal,
                        color = if (i > 0 && v > 0.004) BrandColors.AccentDeep else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
    }
}
