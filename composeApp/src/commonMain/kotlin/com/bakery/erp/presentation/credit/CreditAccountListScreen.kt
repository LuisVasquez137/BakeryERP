package com.bakery.erp.presentation.credit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CreditAccount
import com.bakery.erp.model.CreditAccountType
import com.bakery.erp.model.Permission
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmtDate
import com.bakery.erp.util.fmtMoney

/**
 * Cuentas por cobrar: quién debe y cuánto. Dos pestañas porque son dos preguntas distintas —
 * "¿qué le cobro al colegio?" y "¿qué le descuento a Mario en la planilla?"— y mezcladas el
 * total deja de significar algo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditAccountListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToForm: () -> Unit,
    onNavigateToAging: () -> Unit = {},
    authViewModel: AuthViewModel,
    viewModel: CreditViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val canManage = authState.user?.hasPermission(Permission.MANAGE_CREDIT_ACCOUNTS) == true
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var onlyWithDebt by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(companyId) { if (companyId.isNotEmpty()) viewModel.load(companyId) }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() } }

    val employeesTab = tab == 1
    val q = search.trim().lowercase()
    val visible = state.accounts
        .filter { it.isEmployee == employeesTab }
        .filter { !onlyWithDebt || it.balance > 0.005 }
        .filter { acc ->
            q.isEmpty() || acc.name.lowercase().contains(q) ||
                acc.persons.any { it.name.lowercase().contains(q) }
        }
        // Lo que más se debe arriba: es lo que hay que ir a cobrar.
        .sortedWith(compareByDescending<CreditAccount> { it.balance }.thenBy { it.name.lowercase() })
    val totalOwed = state.accounts.filter { it.isEmployee == employeesTab }.sumOf { it.balance.coerceAtLeast(0.0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Créditos", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    androidx.compose.material3.TextButton(onClick = onNavigateToAging) {
                        Text("Antigüedad", color = MaterialTheme.colorScheme.onPrimary)
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
        floatingActionButton = {
            if (canManage && !employeesTab) {
                FloatingActionButton(onClick = onNavigateToForm, containerColor = BrandColors.Primary) {
                    Icon(Icons.Default.Add, "Nueva cuenta", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Clientes") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Colaboradores") })
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(colors = CardDefaults.cardColors(containerColor = BrandColors.PrimaryContainer)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (employeesTab) "Deben los colaboradores" else "Deben los clientes",
                                modifier = Modifier.weight(1f),
                                color = BrandColors.Primary
                            )
                            Text("Q ${totalOwed.fmtMoney(2)}", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge, color = BrandColors.Primary)
                        }
                    }
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text(if (employeesTab) "Buscar colaborador" else "Buscar cuenta o persona autorizada") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, "Limpiar") }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FilterChip(
                        selected = onlyWithDebt,
                        onClick = { onlyWithDebt = !onlyWithDebt },
                        label = { Text("Solo los que deben") }
                    )
                }
                if (state.isLoading && state.accounts.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (visible.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            when {
                                employeesTab -> "No hay colaboradores activos que coincidan."
                                state.accounts.none { !it.isEmployee } ->
                                    "Todavía no hay cuentas. Creá una por cada cliente o institución a la que se le da crédito."
                                else -> "Ninguna cuenta coincide."
                            },
                            color = BrandColors.MutedSlate
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visible, key = { it.id }) { acc ->
                            AccountRow(acc, onClick = { onNavigateToDetail(acc.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(account: CreditAccount, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (account.type) {
                    CreditAccountType.ORGANIZATION -> Icons.Default.Business
                    CreditAccountType.EMPLOYEE -> Icons.Default.Badge
                    else -> Icons.Default.Person
                },
                null, tint = BrandColors.Primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(account.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = buildList {
                    add(CreditAccountType.label(account.type))
                    if (account.requiresPerson) add("${account.activePersons.size} autorizados")
                    if (!account.active) add("Inactiva")
                    if (account.lastMovementAt > 0) add("Último mov. ${account.lastMovementAt.fmtDate()}")
                }.joinToString(" · ")
                Text(sub, style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "Q ${account.balance.fmtMoney(2)}",
                fontWeight = FontWeight.Bold,
                color = if (account.balance > 0.005) BrandColors.AccentDeep else BrandColors.MutedSlate
            )
        }
    }
}
