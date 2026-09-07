package com.bakery.erp.presentation.employees

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bakery.erp.model.JobPosition
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobPositionFormScreen(
    positionId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: JobPositionViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val isNew = positionId == null

    LaunchedEffect(positionId, companyId) {
        if (positionId != null && companyId.isNotEmpty()) {
            viewModel.loadDetail(companyId, positionId)
        }
    }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.clearSaved(); onNavigateBack() }
    }
    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) { viewModel.clearDeleted(); onNavigateBack() }
    }

    if (!isNew && state.isDetailLoading) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Editar Puesto", fontWeight = FontWeight.SemiBold) },
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
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val existing = state.selectedPosition?.takeIf { it.id == positionId }
    JobPositionFormContent(
        existing = existing,
        isNew = isNew,
        companyId = companyId,
        isLoading = state.isLoading,
        error = state.error,
        onNavigateBack = onNavigateBack,
        onSave = { pos -> viewModel.savePosition(companyId, pos) },
        onDelete = if (!isNew) { { viewModel.deletePosition(companyId, positionId!!) } } else null
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JobPositionFormContent(
    existing: JobPosition?,
    isNew: Boolean,
    companyId: String,
    isLoading: Boolean,
    error: String?,
    onNavigateBack: () -> Unit,
    onSave: (JobPosition) -> Unit,
    onDelete: (() -> Unit)?
) {
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var department by rememberSaveable { mutableStateOf(existing?.department ?: "") }
    var responsibilities by remember { mutableStateOf(existing?.responsibilities ?: emptyList<String>()) }
    var requirements by remember { mutableStateOf(existing?.requirements ?: emptyList<String>()) }
    var minSalaryStr by rememberSaveable { mutableStateOf(if ((existing?.minSalary ?: 0.0) > 0) existing!!.minSalary.toString() else "") }
    var maxSalaryStr by rememberSaveable { mutableStateOf(if ((existing?.maxSalary ?: 0.0) > 0) existing!!.maxSalary.toString() else "") }
    var active by rememberSaveable { mutableStateOf(existing?.active ?: true) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Desactivar puesto") },
            text = { Text("¿Desactivar el puesto \"${existing?.title}\"? Los colaboradores asignados no se modificarán.") },
            confirmButton = {
                TextButton(onClick = { onDelete?.invoke(); showDeleteDialog = false }) {
                    Text("Desactivar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isNew) "Nuevo Puesto" else "Editar Puesto",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    if (!isNew && onDelete != null) {
                        TextButton(onClick = { showDeleteDialog = true }) {
                            Text("Desactivar", color = Color.White)
                        }
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

                // ── Identificación ────────────────────────────────────────────
                PosFormSection("Identificación")

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título del puesto *") },
                    placeholder = { Text("Ej: Encargado Turno PM") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )

                OutlinedTextField(
                    value = department,
                    onValueChange = { department = it },
                    label = { Text("Departamento") },
                    placeholder = { Text("Ej: Producción, Ventas, Administración") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )

                // ── Rango salarial ────────────────────────────────────────────
                PosFormSection("Rango Salarial Referencial")

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = minSalaryStr,
                        onValueChange = { minSalaryStr = it },
                        label = { Text("Mínimo (Q)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        prefix = { Text("Q") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = maxSalaryStr,
                        onValueChange = { maxSalaryStr = it },
                        label = { Text("Máximo (Q)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        prefix = { Text("Q") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                // ── Responsabilidades ─────────────────────────────────────────
                PosFormSection("Responsabilidades")

                responsibilities.forEachIndexed { index, item ->
                    EditableListItem(
                        number = index + 1,
                        value = item,
                        onValueChange = { new ->
                            responsibilities = responsibilities.toMutableList().also { it[index] = new }
                        },
                        onDelete = {
                            responsibilities = responsibilities.toMutableList().also { it.removeAt(index) }
                        },
                        placeholder = "Responsabilidad ${index + 1}"
                    )
                }
                TextButton(
                    onClick = { responsibilities = responsibilities + "" },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Text("  Agregar responsabilidad")
                }

                // ── Requisitos ────────────────────────────────────────────────
                PosFormSection("Requisitos")

                requirements.forEachIndexed { index, item ->
                    EditableListItem(
                        number = index + 1,
                        value = item,
                        onValueChange = { new ->
                            requirements = requirements.toMutableList().also { it[index] = new }
                        },
                        onDelete = {
                            requirements = requirements.toMutableList().also { it.removeAt(index) }
                        },
                        placeholder = "Requisito ${index + 1}"
                    )
                }
                TextButton(
                    onClick = { requirements = requirements + "" },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Text("  Agregar requisito")
                }

                // ── Estado ────────────────────────────────────────────────────
                PosFormSection("Estado")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Puesto activo", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (active) "Los empleados pueden ser asignados a este puesto"
                            else "Puesto inactivo — no aparece en el selector",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = active, onCheckedChange = { active = it })
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (title.isBlank()) return@Button
                        val pos = JobPosition(
                            id = existing?.id ?: "",
                            title = title.trim(),
                            department = department.trim(),
                            responsibilities = responsibilities.map { it.trim() }.filter { it.isNotBlank() },
                            requirements = requirements.map { it.trim() }.filter { it.isNotBlank() },
                            minSalary = minSalaryStr.toDoubleOrNull() ?: 0.0,
                            maxSalary = maxSalaryStr.toDoubleOrNull() ?: 0.0,
                            active = active,
                            createdAt = existing?.createdAt ?: 0L
                        )
                        onSave(pos)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    enabled = title.isNotBlank() && !isLoading
                ) {
                    if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                    else Text("Guardar puesto", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun EditableListItem(
    number: Int,
    value: String,
    onValueChange: (String) -> Unit,
    onDelete: () -> Unit,
    placeholder: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "$number.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.outline) },
            singleLine = false,
            maxLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, "Eliminar", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PosFormSection(title: String) {
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
