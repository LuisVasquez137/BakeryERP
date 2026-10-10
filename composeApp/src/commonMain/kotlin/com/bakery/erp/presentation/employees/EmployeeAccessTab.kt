package com.bakery.erp.presentation.employees

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.EmployeePinStatus
import com.bakery.erp.model.User
import com.bakery.erp.model.UserRole
import com.bakery.erp.presentation.users.PermissionChecklist
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipTone
import com.bakery.erp.util.fmtDate

/**
 * Pestaña "Acceso": el PIN con el que esta persona se identifica en los equipos de las
 * sucursales, y lo que puede hacer con él. Solo OWNER/ADMIN cambian algo (las reglas y la
 * Cloud Function lo exigen igual); los demás lo ven de solo lectura.
 */
@Composable
fun EmployeeAccessTab(
    employeeId: String,
    companyId: String,
    currentUser: User?,
    viewModel: EmployeeAccessViewModel,
    onMessage: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val canManage = currentUser?.role == UserRole.OWNER || currentUser?.role == UserRole.ADMIN
    var showPinDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(employeeId, companyId) {
        if (companyId.isNotEmpty()) viewModel.load(companyId, employeeId)
    }
    LaunchedEffect(state.message) { state.message?.let { onMessage(it); viewModel.clearMessage() } }
    LaunchedEffect(state.error) {
        // Con el diálogo abierto el error se muestra adentro; si no, como aviso.
        if (!showPinDialog) state.error?.let { onMessage(it); viewModel.clearError() }
    }
    LaunchedEffect(state.pinSaved) {
        if (state.pinSaved) { showPinDialog = false; viewModel.clearPinSaved() }
    }

    val employee = state.employee?.takeIf { it.id == employeeId }
    if (employee == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    var selected by remember(employee.permissions) { mutableStateOf(employee.permissions.toSet()) }
    val dirty = selected != employee.permissions.toSet()

    AdaptiveContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AccessCard("PIN") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (employee.pinStatus) {
                        EmployeePinStatus.NONE -> BrandChip("Sin PIN", ChipTone.NEUTRAL)
                        EmployeePinStatus.TEMPORARY -> BrandChip("PIN temporal", ChipTone.ATTENTION)
                        EmployeePinStatus.SET -> BrandChip("PIN listo", ChipTone.SUCCESS)
                    }
                    if (employee.pinUpdatedAt > 0) {
                        Text(
                            "desde ${employee.pinUpdatedAt.fmtDate()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandColors.MutedSlate
                        )
                    }
                }
                Text(
                    when (employee.pinStatus) {
                        EmployeePinStatus.NONE ->
                            "Sin PIN no puede identificarse en los equipos de las sucursales."
                        EmployeePinStatus.TEMPORARY ->
                            "Lo puso otra persona. La primera vez que lo use, la app le pedirá escoger uno propio."
                        EmployeePinStatus.SET ->
                            "Lo escogió la persona: solo ella lo conoce. Si lo olvida, restablécelo aquí."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.MutedSlate
                )
                if (canManage) {
                    OutlinedButton(onClick = { viewModel.clearError(); showPinDialog = true }) {
                        Icon(Icons.Default.Key, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (employee.pinStatus == EmployeePinStatus.NONE) "Asignar PIN" else "Restablecer PIN")
                    }
                }
            }

            AccessCard("Qué puede hacer con su PIN") {
                Text(
                    "En cada equipo podrá hacer lo que tenga marcado aquí Y permita la cuenta de ese " +
                        "equipo. Así una cajera con permiso de cobrar no cobra desde la tablet de la planta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.MutedSlate
                )
                if (!canManage) {
                    Text(
                        "Solo el dueño o un administrador cambia estos permisos.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandColors.Primary
                    )
                }
                PermissionChecklist(selected = selected, onChange = { selected = it }, enabled = canManage)
                if (canManage) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { viewModel.savePermissions(companyId, employeeId, selected) },
                        enabled = dirty && !state.isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isSaving && !showPinDialog) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.Save, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Guardar permisos", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showPinDialog) {
        SetPinDialog(
            employeeName = employee.name,
            isSaving = state.isSaving,
            error = state.error,
            onDismiss = { showPinDialog = false; viewModel.clearError() },
            onSave = { pin, confirm, typedByEmployee ->
                viewModel.setPin(companyId, employeeId, pin, confirm, typedByEmployee)
            }
        )
    }
}

/** También lo usa el alta del colaborador (EmployeeFormScreen), que guarda el PIN hasta tener la ficha. */
@Composable
internal fun SetPinDialog(
    employeeName: String,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (pin: String, confirm: String, typedByEmployee: Boolean) -> Unit
) {
    // remember y no rememberSaveable: un PIN no debe quedar guardado en el estado de la
    // actividad. Al rotar se vuelve a escribir.
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var typedByEmployee by remember { mutableStateOf(false) }
    val digitsOnly = { s: String -> s.filter { it.isDigit() }.take(6) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("PIN de $employeeName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "De 4 a 6 números. No sirven repetidos (1111) ni en serie (1234).",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.MutedSlate
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = digitsOnly(it) },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = digitsOnly(it) },
                    label = { Text("Repetir PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { typedByEmployee = !typedByEmployee },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = typedByEmployee, onCheckedChange = { typedByEmployee = it })
                    Column(Modifier.weight(1f)) {
                        Text("Lo escribió el colaborador", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "Si nadie más lo vio, no tendrá que cambiarlo. Si lo escribiste tú, " +
                                "queda temporal y lo cambiará la primera vez que lo use.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandColors.MutedSlate
                        )
                    }
                }
                if (error != null) {
                    HorizontalDivider()
                    Text(error, color = BrandColors.Danger, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(pin, confirm, typedByEmployee) }, enabled = !isSaving && pin.length >= 4) {
                if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Guardar PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar") }
        }
    )
}

@Composable
private fun AccessCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = BrandColors.Primary)
            HorizontalDivider(color = BrandColors.Primary.copy(alpha = 0.12f))
            content()
        }
    }
}
