package com.bakery.erp.presentation.employees

import com.bakery.erp.util.fmt
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bakery.erp.model.PayrollRecord
import com.bakery.erp.model.PayrollStatus
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors

private val IGSS_EMPLOYEE = 0.0483
private val IGSS_EMPLOYER = 0.1267

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayrollFormScreen(
    employeeId: String,
    payrollId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: EmployeeViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val employee = state.selectedEmployee

    LaunchedEffect(state.isPayrollSaved) {
        if (state.isPayrollSaved) { viewModel.clearPayrollSaved(); onNavigateBack() }
    }

    // Find existing payroll record if editing
    val existing = remember(payrollId, state.payrollHistory) {
        payrollId?.let { id -> state.payrollHistory.find { it.id == id } }
    }

    // Form state
    var periodStartStr by rememberSaveable { mutableStateOf(existing?.let { formatEmployeeDate(it.periodStart) } ?: "") }
    var periodEndStr by rememberSaveable { mutableStateOf(existing?.let { formatEmployeeDate(it.periodEnd) } ?: "") }
    var baseSalaryStr by rememberSaveable { mutableStateOf(existing?.baseSalary?.toString() ?: (employee?.baseSalary?.takeIf { it > 0 }?.toString() ?: "")) }
    var overtimePayStr by rememberSaveable { mutableStateOf(existing?.overtimePay?.toString() ?: "0") }
    var tipsStr by rememberSaveable { mutableStateOf(existing?.tips?.toString() ?: "0") }
    var advancesStr by rememberSaveable { mutableStateOf(existing?.advances?.toString() ?: "0") }
    var bonusesStr by rememberSaveable { mutableStateOf(existing?.bonuses?.toString() ?: "0") }
    var otherDeductionsStr by rememberSaveable { mutableStateOf(existing?.otherDeductions?.toString() ?: "0") }
    var vacationDaysStr by rememberSaveable { mutableStateOf(existing?.vacationDays?.toString() ?: "0") }
    var isrStr by rememberSaveable { mutableStateOf(existing?.isr?.toString() ?: "0") }
    var severancePayStr by rememberSaveable { mutableStateOf(existing?.severancePay?.toString() ?: "0") }
    var paymentDateStr by rememberSaveable { mutableStateOf(existing?.let { if (it.paymentDate > 0) formatEmployeeDate(it.paymentDate) else "" } ?: "") }
    var paymentMethod by rememberSaveable { mutableStateOf(existing?.paymentMethod ?: "") }
    var selectedStatus by rememberSaveable { mutableStateOf(existing?.status ?: PayrollStatus.DRAFT) }
    var statusExpanded by remember { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }

    // Auto-calculate IGSS and totals
    val baseSalary by remember { derivedStateOf { baseSalaryStr.toDoubleOrNull() ?: 0.0 } }
    val overtimePay by remember { derivedStateOf { overtimePayStr.toDoubleOrNull() ?: 0.0 } }
    val tips by remember { derivedStateOf { tipsStr.toDoubleOrNull() ?: 0.0 } }
    val advances by remember { derivedStateOf { advancesStr.toDoubleOrNull() ?: 0.0 } }
    val bonuses by remember { derivedStateOf { bonusesStr.toDoubleOrNull() ?: 0.0 } }
    val otherDeductions by remember { derivedStateOf { otherDeductionsStr.toDoubleOrNull() ?: 0.0 } }
    val vacationDays by remember { derivedStateOf { vacationDaysStr.toDoubleOrNull() ?: 0.0 } }
    val isr by remember { derivedStateOf { isrStr.toDoubleOrNull() ?: 0.0 } }
    val severancePay by remember { derivedStateOf { severancePayStr.toDoubleOrNull() ?: 0.0 } }
    val igssEmployee by remember { derivedStateOf { baseSalary * IGSS_EMPLOYEE } }
    val igssEmployer by remember { derivedStateOf { baseSalary * IGSS_EMPLOYER } }
    val dailyRate by remember { derivedStateOf { baseSalary / 30.0 } }
    val vacationPay by remember { derivedStateOf { dailyRate * vacationDays } }
    val totalGross by remember { derivedStateOf { baseSalary + overtimePay + tips + bonuses + vacationPay + severancePay } }
    val totalNet by remember { derivedStateOf { totalGross - advances - igssEmployee - isr - otherDeductions } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (payrollId == null) "Nueva Nómina" else "Editar Nómina", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = BrandColors.Ivory
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // ── Período ───────────────────────────────────────────────────
                PaySection("Período")

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = periodStartStr,
                        onValueChange = { periodStartStr = it },
                        label = { Text("Inicio (dd/mm/aaaa)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = periodEndStr,
                        onValueChange = { periodEndStr = it },
                        label = { Text("Fin (dd/mm/aaaa)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                // ── Ingresos ──────────────────────────────────────────────────
                PaySection("Ingresos")

                PayField("Salario base (Q)", baseSalaryStr) { baseSalaryStr = it }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PayFieldM("Horas extra (Q)", overtimePayStr, { overtimePayStr = it }, Modifier.weight(1f))
                    PayFieldM("Propinas (Q)", tipsStr, { tipsStr = it }, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PayFieldM("Bonificaciones (Q)", bonusesStr, { bonusesStr = it }, Modifier.weight(1f))
                    PayFieldM("Días vacaciones", vacationDaysStr, { vacationDaysStr = it }, Modifier.weight(1f))
                }
                if (vacationDays > 0) {
                    Text(
                        "Pago vacaciones: Q${vacationPay.fmt(2)} (Q${dailyRate.fmt(2)}/día)",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryBlue
                    )
                }

                // ── Deducciones ───────────────────────────────────────────────
                PaySection("Deducciones")

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = igssEmployee.fmt(2),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("IGSS empleado (4.83%)") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = igssEmployer.fmt(2),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("IGSS patronal (12.67%)") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PayFieldM("ISR (Q)", isrStr, { isrStr = it }, Modifier.weight(1f))
                    PayFieldM("Anticipos (Q)", advancesStr, { advancesStr = it }, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PayFieldM("Otras deduc. (Q)", otherDeductionsStr, { otherDeductionsStr = it }, Modifier.weight(1f))
                    PayFieldM("Indemnización (Q)", severancePayStr, { severancePayStr = it }, Modifier.weight(1f))
                }

                // ── Resumen ───────────────────────────────────────────────────
                PaySection("Resumen")

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(Modifier),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PaySummaryRow("Total bruto", "Q${totalGross.fmt(2)}", false)
                        PaySummaryRow("IGSS empleado", "- Q${igssEmployee.fmt(2)}", false)
                        PaySummaryRow("ISR", "- Q${isr.fmt(2)}", false)
                        if (advances > 0) PaySummaryRow("Anticipos", "- Q${advances.fmt(2)}", false)
                        if (otherDeductions > 0) PaySummaryRow("Otras deducciones", "- Q${otherDeductions.fmt(2)}", false)
                        HorizontalDivider()
                        PaySummaryRow("TOTAL NETO", "Q${totalNet.fmt(2)}", true)
                    }
                }

                // ── Pago ──────────────────────────────────────────────────────
                PaySection("Información de Pago")

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = paymentDateStr,
                        onValueChange = { paymentDateStr = it },
                        label = { Text("Fecha pago (dd/mm/aaaa)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = paymentMethod,
                        onValueChange = { paymentMethod = it },
                        label = { Text("Método de pago") },
                        placeholder = { Text("Transferencia, efectivo…") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Status dropdown
                ExposedDropdownMenuBox(expanded = statusExpanded, onExpandedChange = { statusExpanded = it }) {
                    OutlinedTextField(
                        value = when (selectedStatus) {
                            PayrollStatus.DRAFT -> "Borrador"
                            PayrollStatus.APPROVED -> "Aprobado"
                            PayrollStatus.PAID -> "Pagado"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Estado") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(statusExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                        listOf(PayrollStatus.DRAFT to "Borrador", PayrollStatus.APPROVED to "Aprobado", PayrollStatus.PAID to "Pagado").forEach { (status, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { selectedStatus = status; statusExpanded = false })
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        val record = PayrollRecord(
                            id = existing?.id ?: "",
                            employeeId = employeeId,
                            employeeName = employee?.name ?: "",
                            periodStart = parseDateToMillis(periodStartStr),
                            periodEnd = parseDateToMillis(periodEndStr),
                            baseSalary = baseSalary,
                            overtimePay = overtimePay,
                            tips = tips,
                            advances = advances,
                            bonuses = bonuses,
                            igssEmployee = igssEmployee,
                            igssEmployer = igssEmployer,
                            isr = isr,
                            otherDeductions = otherDeductions,
                            vacationDays = vacationDays,
                            vacationPay = vacationPay,
                            severancePay = severancePay,
                            totalGross = totalGross,
                            totalNet = totalNet,
                            paymentDate = parseDateToMillis(paymentDateStr),
                            paymentMethod = paymentMethod.trim(),
                            status = selectedStatus,
                            notes = notes.trim(),
                            createdAt = existing?.createdAt ?: 0L
                        )
                        viewModel.savePayroll(companyId, employeeId, record)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    enabled = baseSalary > 0 && !state.isPayrollLoading
                ) {
                    if (state.isPayrollLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                    else Text("Guardar nómina", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PaySection(title: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
            color = PrimaryBlue,
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp), color = PrimaryBlue.copy(alpha = 0.15f))
    }
}

@Composable
private fun PayField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        prefix = { Text("Q") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
private fun PayFieldM(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
private fun PaySummaryRow(label: String, value: String, isTotal: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = if (isTotal) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            value,
            style = if (isTotal) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal,
            color = if (isTotal) PrimaryBlue else MaterialTheme.colorScheme.onSurface
        )
    }
}
