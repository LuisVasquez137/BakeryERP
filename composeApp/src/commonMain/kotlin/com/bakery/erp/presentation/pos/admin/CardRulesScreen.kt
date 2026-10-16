package com.bakery.erp.presentation.pos.admin

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CardPaymentRules
import com.bakery.erp.model.CardSurchargeMode
import com.bakery.erp.model.CardSurchargeType
import com.bakery.erp.model.cardRulesProblem
import com.bakery.erp.model.minimumProblem
import com.bakery.erp.model.surchargeDescription
import com.bakery.erp.model.surchargeFor
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtMoney

/**
 * Mínimo y recargo del pago con tarjeta, para todas las sucursales. Por defecto no hay ninguno:
 * cada panadería decide si cobra recargo, siempre o por debajo de un monto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardRulesScreen(
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CardRulesViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(companyId) { if (companyId.isNotEmpty()) viewModel.load(companyId) }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() } }
    LaunchedEffect(state.savedAt) { if (state.savedAt > 0) snackbar.showSnackbar("Reglas guardadas. Ya aplican en todas las cajas.") }

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Pago con tarjeta", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
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
            val rules = state.rules
            if (rules == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                CardRulesForm(
                    initial = rules,
                    onSave = { viewModel.save(companyId, it, authState.user?.displayName.orEmpty()) }
                )
            }
        }
    }
    LoadingOverlay(visible = state.isSaving, message = "Guardando…")
}

@Composable
private fun CardRulesForm(initial: CardPaymentRules, onSave: (CardPaymentRules) -> Unit) {
    var minimumText by rememberSaveable { mutableStateOf(initial.minimumAmount.textOrEmpty()) }
    var mode by rememberSaveable { mutableStateOf(initial.surchargeMode) }
    var thresholdText by rememberSaveable { mutableStateOf(initial.surchargeThreshold.textOrEmpty()) }
    var type by rememberSaveable { mutableStateOf(initial.surchargeType) }
    var valueText by rememberSaveable { mutableStateOf(initial.surchargeValue.textOrEmpty()) }

    val draft = CardPaymentRules(
        minimumAmount = minimumText.toMoney(),
        surchargeMode = mode,
        surchargeThreshold = thresholdText.toMoney(),
        surchargeType = type,
        surchargeValue = valueText.toMoney()
    )
    val problem = cardRulesProblem(draft)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(color = BrandColors.PrimaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                "Aplica en todas las sucursales. El recargo se suma solo al cobrar con tarjeta y la " +
                    "cajera no lo puede quitar. En el ticket sale como renglón; en la factura no se " +
                    "describe: se reparte entre los productos para que sume lo cobrado.",
                modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall,
                color = BrandColors.TextSlate
            )
        }

        Section("Monto mínimo con tarjeta") {
            MoneyField(minimumText, { minimumText = it }, "Mínimo", "Vacío o 0 = se acepta cualquier monto")
        }

        Section("Recargo") {
            ModeOption("Nunca", mode == CardSurchargeMode.NEVER) { mode = CardSurchargeMode.NEVER }
            ModeOption("Siempre que paguen con tarjeta", mode == CardSurchargeMode.ALWAYS) { mode = CardSurchargeMode.ALWAYS }
            ModeOption("Solo si es menos de un monto", mode == CardSurchargeMode.BELOW) { mode = CardSurchargeMode.BELOW }
            if (mode == CardSurchargeMode.BELOW) {
                MoneyField(thresholdText, { thresholdText = it }, "Menos de",
                    "Desde este monto ya no se cobra recargo")
            }
            if (mode != CardSurchargeMode.NEVER) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == CardSurchargeType.PERCENT, onClick = { type = CardSurchargeType.PERCENT },
                        label = { Text("Porcentaje (%)") })
                    FilterChip(selected = type == CardSurchargeType.FIXED, onClick = { type = CardSurchargeType.FIXED },
                        label = { Text("Monto fijo (Q)") })
                }
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(if (type == CardSurchargeType.PERCENT) "Porcentaje" else "Monto") },
                    prefix = if (type == CardSurchargeType.FIXED) ({ Text("Q ") }) else null,
                    suffix = if (type == CardSurchargeType.PERCENT) ({ Text("%") }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Ejemplos con la regla tal como está escrita: se entiende mejor que la descripción.
        if (problem == null) {
            Section("Así se cobraría") {
                Text(draft.surchargeDescription(), fontWeight = FontWeight.SemiBold, color = BrandColors.Primary)
                listOf(10.0, 30.0, 75.0, 150.0).forEach { amount ->
                    val min = draft.minimumProblem(amount)
                    val extra = draft.surchargeFor(amount)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Venta de Q ${amount.fmtMoney(2)}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            when {
                                min != null -> "No se acepta tarjeta"
                                extra > 0.004 -> "Q ${(amount + extra).fmtMoney(2)} (+Q ${extra.fmtMoney(2)})"
                                else -> "Q ${amount.fmtMoney(2)}, sin recargo"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (min != null) MaterialTheme.colorScheme.error else BrandColors.TextSlate
                        )
                    }
                }
            }
        } else {
            Text(problem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = { onSave(draft) },
            enabled = problem == null,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Guardar", fontWeight = FontWeight.Bold) }
        if (initial.updatedAt > 0 && initial.updatedByName.isNotBlank()) {
            Text("Último cambio: ${initial.updatedByName}", style = MaterialTheme.typography.bodySmall,
                color = BrandColors.MutedSlate)
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun ModeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(label)
    }
}

@Composable
private fun MoneyField(value: String, onChange: (String) -> Unit, label: String, help: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }) },
        label = { Text(label) },
        prefix = { Text("Q ") },
        supportingText = { Text(help) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun Double.textOrEmpty(): String = if (this > 0.0) fmt(2).trimEnd('0').trimEnd('.') else ""
private fun String.toMoney(): Double = trim().toDoubleOrNull() ?: 0.0
