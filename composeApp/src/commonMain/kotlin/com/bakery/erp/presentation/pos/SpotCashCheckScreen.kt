package com.bakery.erp.presentation.pos

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.expectedCashNow
import com.bakery.erp.model.CashDenominations
import com.bakery.erp.model.SpotCashCheckResolution
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.pos.components.DenominationCounter
import com.bakery.erp.presentation.pos.components.DenominationCountsSaver
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.util.fmtMoney
import com.bakery.erp.ui.theme.BrandColors.Primary as DeepSeaBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotCashCheckScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: SpotCashCheckViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val user = authState.user
    val snackbar = remember { SnackbarHostState() }
    var counts by rememberSaveable(stateSaver = DenominationCountsSaver) {
        mutableStateOf<Map<String, Int>>(emptyMap())
    }
    var notes by rememberSaveable { mutableStateOf("") }
    var resolution by rememberSaveable { mutableStateOf(SpotCashCheckResolution.NONE) }
    var showConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(companyId, sessionId) {
        if (companyId.isNotEmpty()) viewModel.loadActiveSession(companyId, sessionId)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.savedCheckId) {
        state.savedCheckId?.let { viewModel.clearSaved(); onSaved() }
    }

    val session = state.activeSession
    val expected = session?.expectedCashNow ?: 0.0
    val counted = counts.entries.sumOf { (code, qty) ->
        (CashDenominations.byCode(code)?.value ?: 0.0) * qty
    }
    val diff = counted - expected
    val hasCount = counts.values.any { it > 0 }

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Cuadre sorpresa", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepSeaBlue,
                    titleContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = WarmIvory
    ) { padding ->
        if (session == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DeepSeaBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("${session.branchName} · ${session.registerName}",
                            color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Cajero: ${session.cashierName}",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(Modifier.padding(vertical = 6.dp),
                            color = Color.White.copy(alpha = 0.3f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Efectivo esperado ahora", color = Color.White.copy(alpha = 0.85f))
                            Text("Q ${expected.fmtMoney(2)}", color = Color.White,
                                fontWeight = FontWeight.Bold)
                        }
                        Text("Ventas: Q ${session.totalSales.fmtMoney(2)} · " +
                            "# ${session.saleCount}",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }

                Text("Cuenta el efectivo presente físicamente",
                    fontWeight = FontWeight.SemiBold)
                DenominationCounter(counts = counts, onChange = { counts = it })

                if (hasCount) {
                    val color = when {
                        diff > 0.01 -> Color(0xFF1B5E20)
                        diff < -0.01 -> Color(0xFFB00020)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Diferencia", fontWeight = FontWeight.SemiBold, color = color)
                                Text("Q ${diff.fmtMoney(2)}", fontWeight = FontWeight.Bold, color = color)
                            }
                            Text(
                                when {
                                    diff > 0.01 -> "Sobra efectivo respecto al esperado."
                                    diff < -0.01 -> "Falta efectivo respecto al esperado."
                                    else -> "Cuadre exacto."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text("Resolución del cuadre", fontWeight = FontWeight.SemiBold)
                ResolutionPicker(resolution, onSelect = { resolution = it })

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas *") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    isError = notes.isBlank()
                )

                Button(
                    onClick = { showConfirm = true },
                    enabled = hasCount && notes.isNotBlank() && !state.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Guardar cuadre", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showConfirm && user != null && session != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("¿Guardar cuadre?") },
            text = {
                val tail = when (resolution) {
                    SpotCashCheckResolution.FORCE_ADJUST ->
                        " · Se ajustará el efectivo esperado de la sesión por Q ${diff.fmtMoney(2)}."
                    SpotCashCheckResolution.REQUIRES_REVIEW ->
                        " · Quedará marcado para revisión posterior."
                    else -> ""
                }
                Text("La sesión sigue ABIERTA. El cajero no recibirá alerta visible.$tail")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    viewModel.submitSpotCheck(
                        companyId = companyId,
                        session = session,
                        countedCash = counted,
                        denominations = counts,
                        resolution = resolution,
                        notes = notes,
                        currentUser = user
                    )
                }) { Text("Sí, guardar") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ResolutionPicker(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            SpotCashCheckResolution.NONE,
            SpotCashCheckResolution.FORCE_ADJUST,
            SpotCashCheckResolution.REQUIRES_REVIEW
        ).forEach { code ->
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()) {
                RadioButton(selected = selected == code, onClick = { onSelect(code) })
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(SpotCashCheckResolution.label(code),
                        fontWeight = FontWeight.SemiBold)
                    Text(
                        when (code) {
                            SpotCashCheckResolution.NONE ->
                                "Registra el cuadre como auditoría. No toca la sesión."
                            SpotCashCheckResolution.FORCE_ADJUST ->
                                "Crea un movimiento que alinea el efectivo esperado con lo contado."
                            SpotCashCheckResolution.REQUIRES_REVIEW ->
                                "Marca el cuadre para revisión posterior."
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
