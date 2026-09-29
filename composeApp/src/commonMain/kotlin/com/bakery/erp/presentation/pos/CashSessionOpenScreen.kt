package com.bakery.erp.presentation.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CashDenominations
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.pos.components.DenominationCounter
import com.bakery.erp.presentation.pos.components.DenominationCountsSaver
import com.bakery.erp.ui.AdaptiveContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashSessionOpenScreen(
    registerId: String,
    onNavigateBack: () -> Unit,
    onSessionOpened: (sessionId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CashSessionViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val user = authState.user
    val snackbar = remember { SnackbarHostState() }
    var openingCounts by rememberSaveable(stateSaver = DenominationCountsSaver) {
        mutableStateOf<Map<String, Int>>(emptyMap())
    }

    LaunchedEffect(companyId, registerId) {
        if (companyId.isNotEmpty() && user != null && state.branches.isEmpty()) {
            viewModel.loadBranches(companyId, user)
        }
    }
    val branchIdForExpected = state.registers.firstOrNull { it.id == registerId }?.branchId.orEmpty()
    LaunchedEffect(companyId, branchIdForExpected) {
        if (companyId.isNotEmpty() && branchIdForExpected.isNotEmpty()) {
            viewModel.loadExpectedOpeningDenoms(companyId, branchIdForExpected)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.openedSessionId) {
        state.openedSessionId?.let { id ->
            viewModel.clearOpenedSessionId()
            onSessionOpened(id)
        }
    }

    val register = state.registers.firstOrNull { it.id == registerId }
    val branch = state.branches.firstOrNull { it.id == register?.branchId }
    // Quién abre: de Colaboradores. La tablet tiene un solo usuario para todas las cajeras.
    var openedBy by remember { mutableStateOf<com.bakery.erp.model.Employee?>(null) }
    LaunchedEffect(companyId, branch?.id) {
        if (companyId.isNotEmpty() && branch != null) viewModel.loadEmployees(companyId, branch.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Abrir caja", fontWeight = FontWeight.SemiBold) },
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
        AdaptiveContainer(modifier = Modifier.padding(padding), maxContentWidth = 600.dp) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(branch?.name.orEmpty(), fontWeight = FontWeight.SemiBold)
                        Text(register?.name.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        Text("Usuario: ${user?.displayName.orEmpty()}",
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                EmployeePickerField(
                    label = "¿Quién abre la caja?",
                    employees = state.employees,
                    selected = openedBy,
                    chosen = openedBy != null,
                    onSelect = { openedBy = it }
                )

                if (state.expectedOpeningDenoms.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFFFFF3E0)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Fondo esperado del cierre anterior",
                                fontWeight = FontWeight.SemiBold,
                                color = androidx.compose.ui.graphics.Color(0xFF8B4513))
                            Text("Verifica que estas cantidades coincidan con lo que encuentras en la caja. " +
                                "Si difieren, ajusta el conteo abajo y deja una nota.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Text("Fondo de cambio inicial · cuenta por denominaciones",
                    fontWeight = FontWeight.SemiBold)
                DenominationCounter(
                    counts = openingCounts,
                    onChange = { openingCounts = it },
                    expectedCounts = state.expectedOpeningDenoms.ifEmpty { null }
                )
                Text("Sirve para dar vueltos. Al cerrar la caja se descuenta del efectivo esperado.",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)

                Button(
                    onClick = {
                        val branchRef = branch ?: return@Button
                        val registerRef = register ?: return@Button
                        val userRef = user ?: return@Button
                        val opening = openingCounts.entries.sumOf { (code, qty) ->
                            (CashDenominations.byCode(code)?.value ?: 0.0) * qty
                        }
                        viewModel.openSession(
                            companyId, branchRef, registerRef, userRef, opening, openingCounts,
                            openedBy = openedBy
                        )
                    },
                    enabled = register != null && branch != null && user != null && openedBy != null && !state.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Abrir caja")
                }
            }
        }
    }
}
