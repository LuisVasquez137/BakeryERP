package com.bakery.erp.presentation.customerOrders

import com.bakery.erp.ui.BrandTopAppBar

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.ExternalCustomer
import com.bakery.erp.model.ExternalCustomerType
import com.bakery.erp.model.Permission
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalCustomerListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToForm: (customerId: String?) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ExternalCustomerViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val canEdit = authState.user?.hasPermission(Permission.MANAGE_EXTERNAL_CUSTOMERS) == true
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) viewModel.loadCustomers(companyId)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        containerColor = WarmIvory,
        topBar = {
            BrandTopAppBar(
                title = { Text("Clientes externos", fontWeight = FontWeight.SemiBold) },
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
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(
                    onClick = { onNavigateToForm(null) },
                    containerColor = BrandColors.Primary,
                    contentColor = Color.White
                ) { Icon(Icons.Default.Add, contentDescription = "Nuevo cliente") }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
                state.customers.isEmpty() -> EmptyState(canEdit) { onNavigateToForm(null) }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.customers, key = { it.id }) { customer ->
                        CustomerCard(customer) { onNavigateToForm(customer.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(canEdit: Boolean, onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Storefront,
            contentDescription = null,
            tint = PrimaryBlue.copy(alpha = 0.5f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.size(16.dp))
        Text(
            "Aún no hay clientes externos",
            style = MaterialTheme.typography.titleMedium,
            color = PrimaryBlue,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.size(8.dp))
        Text(
            if (canEdit) "Agrega revendedores y clientes recurrentes para gestionar sus pedidos."
            else "Tu administrador aún no ha registrado clientes externos.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF6E6E6E)
        )
    }
}

@Composable
private fun CustomerCard(customer: ExternalCustomer, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(PrimaryBlue.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Storefront, contentDescription = null, tint = PrimaryBlue)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    customer.name.ifEmpty { "Sin nombre" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (customer.businessName.isNotEmpty()) {
                    Text(
                        customer.businessName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6E6E6E),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.size(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (customer.type == ExternalCustomerType.RESELLER)
                                    BurntOrange.copy(alpha = 0.18f)
                                else PrimaryBlue.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            ExternalCustomerType.label(customer.type),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (customer.type == ExternalCustomerType.RESELLER) BrandColors.AccentDeep
                            else PrimaryBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (customer.phone.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            customer.phone,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF6E6E6E)
                        )
                    }
                }
            }
        }
    }
}
