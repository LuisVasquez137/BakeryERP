package com.bakery.erp.presentation.suppliers

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Supplier
import com.bakery.erp.presentation.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SupplierFormScreen(
    supplierId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: SupplierViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    val existing = remember(supplierId, state.supplierList) {
        if (supplierId != null) state.supplierList.find { it.id == supplierId }
            ?: state.selectedSupplier?.takeIf { it.id == supplierId }
        else null
    }

    var company by rememberSaveable { mutableStateOf(existing?.company ?: "") }
    var salesperson by rememberSaveable { mutableStateOf(existing?.salesperson ?: "") }
    var phone by rememberSaveable { mutableStateOf(existing?.phone ?: "") }
    var address by rememberSaveable { mutableStateOf(existing?.address ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var paymentTermsDays by rememberSaveable { mutableStateOf((existing?.paymentTermsDays ?: 0).toString()) }
    var active by rememberSaveable { mutableStateOf(existing?.active ?: true) }

    var companyError by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf(false) }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.clearSaved(); onNavigateBack() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    fun build() = Supplier(
        id = existing?.id ?: "",
        company = company.trim().replaceFirstChar { it.uppercase() },
        salesperson = salesperson.trim().replaceFirstChar { it.uppercase() },
        phone = phone.trim(),
        address = address.trim().ifEmpty { null },
        notes = notes.trim().ifEmpty { null },
        paymentTermsDays = paymentTermsDays.toIntOrNull()?.coerceIn(0, 365) ?: 0,
        active = active
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (supplierId == null) "Nuevo Proveedor" else "Editar Proveedor",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                // ── Empresa ──────────────────────────────────────────────────
                SectionHeader("Empresa")

                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it; companyError = false },
                    label = { Text("Nombre de la empresa *") },
                    placeholder = { Text("Ej: Distribuidora ABC") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = companyError,
                    supportingText = if (companyError) ({ Text("El nombre de la empresa es requerido") }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )

                OutlinedTextField(
                    value = salesperson,
                    onValueChange = { salesperson = it },
                    label = { Text("Vendedor / Contacto") },
                    placeholder = { Text("Ej: Juan Pérez") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Activo", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = active, onCheckedChange = { active = it })
                }

                SectionDivider()

                // ── Contacto ─────────────────────────────────────────────────
                SectionHeader("Contacto")

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; phoneError = false },
                    label = { Text("Teléfono *") },
                    placeholder = { Text("Ej: 5555-1234") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = phoneError,
                    supportingText = if (phoneError) ({ Text("El teléfono es requerido") }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Dirección") },
                    placeholder = { Text("Opcional") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )

                SectionDivider()

                // ── Condiciones de pago ──────────────────────────────────────
                SectionHeader("Condiciones de pago")

                Text(
                    "Cuántos días de crédito da este proveedor. Con esto las compras nacen " +
                        "con fecha de vencimiento y aparecen en \"por pagar\" antes de que se venzan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Contado", 8 to "8 días", 15 to "15 días", 30 to "30 días", 60 to "60 días")
                        .forEach { (days, label) ->
                            FilterChip(
                                selected = paymentTermsDays.toIntOrNull() == days,
                                onClick = { paymentTermsDays = days.toString() },
                                label = { Text(label) }
                            )
                        }
                }

                OutlinedTextField(
                    value = paymentTermsDays,
                    onValueChange = { v -> paymentTermsDays = v.filter { it.isDigit() }.take(3) },
                    label = { Text("Días de crédito") },
                    supportingText = { Text("0 = contado") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                SectionDivider()

                // ── Notas ────────────────────────────────────────────────────
                SectionHeader("Notas")

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(Modifier.padding(8.dp))

                Button(
                    onClick = {
                        var hasError = false
                        if (company.isBlank()) { companyError = true; hasError = true }
                        if (phone.isBlank()) { phoneError = true; hasError = true }
                        if (hasError) return@Button
                        viewModel.saveSupplier(companyId, build())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Guardar Proveedor", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
