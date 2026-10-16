package com.bakery.erp.presentation.credit

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import com.bakery.erp.util.fmtTrim
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.AuthorizedPerson
import com.bakery.erp.model.CreditAccount
import com.bakery.erp.model.CreditAccountType
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandColors

/**
 * Alta y edición de una cuenta de cliente o institución. Las de colaborador no pasan por acá:
 * salen de Colaboradores.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditAccountFormScreen(
    accountId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CreditViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val snackbar = remember { SnackbarHostState() }
    val isEdit = accountId != null
    val needsLoad = isEdit && state.selected?.id != accountId

    LaunchedEffect(companyId, accountId) {
        if (companyId.isEmpty()) return@LaunchedEffect
        if (accountId != null) { if (needsLoad) viewModel.loadAccount(companyId, accountId) }
        else viewModel.clearSelected()
    }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() } }
    LaunchedEffect(state.isSaved) { if (state.isSaved) { viewModel.clearSaved(); onNavigateBack() } }

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text(if (isEdit) "Editar cuenta" else "Nueva cuenta de crédito", fontWeight = FontWeight.SemiBold) },
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
        if (needsLoad) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            CreditAccountFormContent(
                existing = if (isEdit) state.selected else null,
                isSaving = state.isSaving,
                onSave = { viewModel.saveAccount(companyId, it) }
            )
        }
    }
}

@Composable
private fun CreditAccountFormContent(
    existing: CreditAccount?,
    isSaving: Boolean,
    onSave: (CreditAccount) -> Unit
) {
    var type by rememberSaveable { mutableStateOf(existing?.type ?: CreditAccountType.ORGANIZATION) }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var taxId by rememberSaveable { mutableStateOf(existing?.taxId.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(existing?.phone.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes.orEmpty()) }
    var active by rememberSaveable { mutableStateOf(existing?.active ?: true) }
    var persons by remember { mutableStateOf(existing?.persons ?: emptyList()) }
    var newName by rememberSaveable { mutableStateOf("") }
    var newDoc by rememberSaveable { mutableStateOf("") }
    var newPhone by rememberSaveable { mutableStateOf("") }
    // Índice de la persona que se está editando en la tarjeta de abajo; -1 = agregando una nueva.
    var editingIndex by rememberSaveable { mutableStateOf(-1) }
    var deletingIndex by remember { mutableStateOf(-1) }
    var discountEligible by rememberSaveable { mutableStateOf(existing?.cashDiscountEligible ?: false) }

    val isEmployee = existing?.isEmployee == true
    val isOrg = type == CreditAccountType.ORGANIZATION
    val missingPersons = isOrg && persons.none { it.active }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!isEmployee) {
            Text("¿A quién se le da el crédito?", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CreditViewModel.MANUAL_TYPES.forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = t },
                        label = { Text(CreditAccountType.label(t)) })
                }
            }
            Text(
                if (isOrg) "Una institución (el colegio, una empresa) debe como tal; las personas que " +
                    "autorices abajo pueden llevar mercadería a su nombre."
                else "Una persona que lleva a crédito para sí misma.",
                style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate
            )
        }
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text(if (isOrg) "Nombre de la institución" else "Nombre completo") },
            singleLine = true, readOnly = isEmployee, modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = taxId, onValueChange = { taxId = it }, label = { Text("NIT (opcional)") },
                singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Teléfono") },
                singleLine = true, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notas") },
            minLines = 2, modifier = Modifier.fillMaxWidth())

        if (isOrg) {
            Text("Personas autorizadas", fontWeight = FontWeight.SemiBold)
            Text(
                "Solo ellas pueden llevar mercadería a la cuenta. Si llega alguien que no está en la " +
                    "lista, no se le fía hasta agregarlo aquí.",
                style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate
            )
            persons.forEachIndexed { index, p ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (index == editingIndex) BrandColors.AccentSoft
                                         else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold,
                                color = if (p.active) MaterialTheme.colorScheme.onSurface else BrandColors.MutedSlate)
                            val sub = listOf(p.idDocument.ifBlank { null }?.let { "DPI $it" }, p.phone.ifBlank { null })
                                .filterNotNull().joinToString(" · ")
                            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
                        }
                        // Desactivar la deja en la lista sin poder cargar; eliminar la quita. Las
                        // compras que ya hizo siguen diciendo su nombre: el cargo guarda una copia.
                        Switch(checked = p.active, onCheckedChange = { on ->
                            persons = persons.toMutableList().also { it[index] = p.copy(active = on) }
                        })
                        IconButton(onClick = {
                            editingIndex = index
                            newName = p.name; newDoc = p.idDocument; newPhone = p.phone
                        }) { Icon(Icons.Default.Edit, "Editar persona", tint = BrandColors.Primary) }
                        IconButton(onClick = { deletingIndex = index }) {
                            Icon(Icons.Default.Delete, "Eliminar persona", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = BrandColors.PrimaryContainer),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (editingIndex >= 0) "Editando persona" else "Nueva persona autorizada",
                        fontWeight = FontWeight.SemiBold, color = BrandColors.Primary)
                    OutlinedTextField(value = newName, onValueChange = { newName = it },
                        label = { Text("Nombre de la persona") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = newDoc, onValueChange = { newDoc = it },
                            label = { Text("DPI (opcional)") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = newPhone, onValueChange = { newPhone = it },
                            label = { Text("Teléfono") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val edited = persons.getOrNull(editingIndex)
                                persons = if (edited != null) {
                                    // Mismo id: los cargos viejos la siguen reconociendo.
                                    persons.toMutableList().also {
                                        it[editingIndex] = edited.copy(name = newName.trim(),
                                            idDocument = newDoc.trim(), phone = newPhone.trim())
                                    }
                                } else persons + AuthorizedPerson(name = newName.trim(),
                                    idDocument = newDoc.trim(), phone = newPhone.trim())
                                newName = ""; newDoc = ""; newPhone = ""; editingIndex = -1
                            },
                            enabled = newName.isNotBlank()
                        ) {
                            Icon(if (editingIndex >= 0) Icons.Default.Check else Icons.Default.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (editingIndex >= 0) "Guardar cambios" else "Agregar persona")
                        }
                        if (editingIndex >= 0) {
                            TextButton(onClick = { newName = ""; newDoc = ""; newPhone = ""; editingIndex = -1 }) {
                                Text("Cancelar")
                            }
                        }
                    }
                }
            }
            if (missingPersons) {
                Text("Agregá al menos una persona autorizada.", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall)
            }
        }

        if (isEmployee) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Descuento de colaborador", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Si paga en efectivo en el momento, se le descuenta el % que tenga cada " +
                            "producto (se configura en el producto, pestaña Precios). Lo fiado va a precio completo.",
                        style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = discountEligible, onCheckedChange = { discountEligible = it })
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = active, onCheckedChange = { active = it })
            Spacer(Modifier.width(8.dp))
            Text(if (active) "Activa: se le puede vender a crédito" else "Inactiva: no se le fía, pero se le puede cobrar")
        }

        Button(
            onClick = {
                onSave(
                    (existing ?: CreditAccount()).copy(
                        type = if (isEmployee) CreditAccountType.EMPLOYEE else type,
                        name = name.trim(),
                        taxId = taxId.trim(),
                        phone = phone.trim(),
                        notes = notes.trim(),
                        active = active,
                        // Una persona que presta a su nombre no lleva lista: es ella misma.
                        persons = if (type == CreditAccountType.ORGANIZATION) persons else emptyList(),
                        cashDiscountEligible = isEmployee && discountEligible
                    )
                )
            },
            enabled = !isSaving && name.isNotBlank() && !missingPersons && editingIndex < 0,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (isSaving) "Guardando…" else "Guardar") }
        if (editingIndex >= 0) {
            Text("Terminá de editar la persona antes de guardar.", style = MaterialTheme.typography.labelSmall,
                color = BrandColors.MutedSlate)
        }
    }

    persons.getOrNull(deletingIndex)?.let { p ->
        AlertDialog(
            onDismissRequest = { deletingIndex = -1 },
            title = { Text("¿Eliminar a ${p.name}?") },
            text = {
                Text("Ya no podrá llevar mercadería a esta cuenta. Las compras que ya hizo siguen " +
                    "apareciendo con su nombre en el estado de cuenta. Si solo es temporal, mejor desactivala.")
            },
            confirmButton = {
                TextButton(onClick = {
                    persons = persons.filterIndexed { i, _ -> i != deletingIndex }
                    if (editingIndex == deletingIndex) { editingIndex = -1; newName = ""; newDoc = ""; newPhone = "" }
                    else if (editingIndex > deletingIndex) editingIndex -= 1
                    deletingIndex = -1
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingIndex = -1 }) { Text("Cancelar") } }
        )
    }
}
