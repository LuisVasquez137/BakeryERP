package com.bakery.erp.presentation.users

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Permission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.branches.BranchViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserPermissionsScreen(
    userId: String,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: UserViewModel,
    branchViewModel: BranchViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val branchState by branchViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    val user = state.userList.find { it.id == userId }
    val branches = branchState.branchList.filter { it.active }

    var selectedBranchIds by remember(user) {
        mutableStateOf(user?.branchIds?.toSet() ?: emptySet())
    }
    var selectedPermissions by remember(user) {
        mutableStateOf(user?.permissions?.toSet() ?: emptySet())
    }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty() && branchState.branchList.isEmpty()) {
            branchViewModel.loadBranches(companyId)
        }
    }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.clearSaved(); onNavigateBack() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        user?.displayName?.ifEmpty { user.email } ?: "Permisos",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
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
            when {
                user == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Usuario no encontrado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ── Info del usuario ──────────────────────────────────
                        PermSection("Usuario")
                        Text(user.displayName.ifEmpty { user.email }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RoleBadge(user.role)
                        }

                        // ── Sucursales ────────────────────────────────────────
                        PermDivider()
                        PermSection("Sucursales asignadas")
                        Text(
                            "Selecciona las sucursales a las que tendrá acceso. Si no seleccionas ninguna, no podrá ver nada.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            branches.forEach { branch ->
                                FilterChip(
                                    selected = branch.id in selectedBranchIds,
                                    onClick = {
                                        selectedBranchIds = if (branch.id in selectedBranchIds)
                                            selectedBranchIds - branch.id
                                        else
                                            selectedBranchIds + branch.id
                                    },
                                    label = { Text(branch.name) },
                                    leadingIcon = if (branch.id in selectedBranchIds) ({
                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                    }) else null
                                )
                            }
                            if (branches.isEmpty()) {
                                Text("No hay sucursales activas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        // ── Permisos por grupo ────────────────────────────────
                        PermDivider()
                        PermSection("Permisos")

                        val groups = Permission.entries.groupBy { it.group }
                        groups.forEach { (groupName, perms) ->
                            // Estado del "maestro" del módulo: On (todos), Off (ninguno) o
                            // Indeterminate (algunos). Un tap habilita/deshabilita el grupo completo.
                            // El maestro del grupo NO toca los permisos sensibles (ver
                            // dinero): se marcan uno por uno, con intención. Antes
                            // "Habilitar todos" en Distribución le daba a cualquiera que
                            // empaca el valor de cada nota de despacho, de regalo.
                            val groupPermNames = perms.filterNot { it.isSensitive }.map { it.name }
                            val selectedInGroup = groupPermNames.count { it in selectedPermissions }
                            val groupTriState = when (selectedInGroup) {
                                0 -> ToggleableState.Off
                                groupPermNames.size -> ToggleableState.On
                                else -> ToggleableState.Indeterminate
                            }
                            val toggleGroup = {
                                selectedPermissions = if (selectedInGroup == groupPermNames.size)
                                    selectedPermissions - groupPermNames.toSet()
                                else
                                    selectedPermissions + groupPermNames.toSet()
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { toggleGroup() }
                                    .padding(top = 8.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = groupName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "Habilitar todos",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TriStateCheckbox(
                                    state = groupTriState,
                                    onClick = { toggleGroup() }
                                )
                            }
                            perms.forEach { perm ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedPermissions = if (perm.name in selectedPermissions)
                                                selectedPermissions - perm.name
                                            else
                                                selectedPermissions + perm.name
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = perm.name in selectedPermissions,
                                        onCheckedChange = { checked ->
                                            selectedPermissions = if (checked)
                                                selectedPermissions + perm.name
                                            else
                                                selectedPermissions - perm.name
                                        }
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(perm.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Text(
                                            perm.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        // Se avisa en la fila, no solo en la lógica: quien
                                        // reparte permisos tiene que ver que éste muestra
                                        // dinero antes de marcarlo.
                                        if (perm.isSensitive) {
                                            Text(
                                                "Muestra dinero · queda fuera de \"Habilitar todos\"",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFFB26A00)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        PermDivider()
                        Spacer(Modifier.padding(4.dp))

                        Button(
                            onClick = {
                                viewModel.updateUserAccess(
                                    orgId = companyId,
                                    userId = userId,
                                    branchIds = selectedBranchIds.toList(),
                                    permissions = selectedPermissions.toList()
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Guardar y aprobar acceso", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PermSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun PermDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
}
