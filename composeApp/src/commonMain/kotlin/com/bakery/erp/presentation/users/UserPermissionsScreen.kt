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
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Block
import com.bakery.erp.ui.theme.BrandColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.UserRole
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.branches.BranchViewModel
import com.bakery.erp.ui.AdaptiveContainer

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
    var isDeviceAccount by remember(user) { mutableStateOf(user?.isDeviceAccount ?: false) }
    var confirmDeactivate by remember { mutableStateOf(false) }

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
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
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

                        // ── Cuenta de equipo ──────────────────────────────────
                        // Un OWNER/ADMIN es una persona, nunca el equipo de una sucursal.
                        if (user.role != UserRole.OWNER && user.role != UserRole.ADMIN) {
                            PermDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isDeviceAccount = !isDeviceAccount },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Cuenta de equipo (sucursal)",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "Es la cuenta de la tablet o teléfono de una sucursal, no de una persona. " +
                                            "Quien use el equipo se identifica con su PIN de colaborador, y los " +
                                            "permisos de aquí abajo son lo MÁXIMO que se puede hacer en ese equipo.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Switch(checked = isDeviceAccount, onCheckedChange = { isDeviceAccount = it })
                            }
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
                        PermSection(if (isDeviceAccount) "Lo máximo que se puede hacer en este equipo" else "Permisos")

                        PermissionChecklist(
                            selected = selectedPermissions,
                            onChange = { selectedPermissions = it }
                        )

                        PermDivider()
                        Spacer(Modifier.padding(4.dp))

                        Button(
                            onClick = {
                                viewModel.updateUserAccess(
                                    orgId = companyId,
                                    userId = userId,
                                    branchIds = selectedBranchIds.toList(),
                                    permissions = selectedPermissions.toList(),
                                    isDeviceAccount = isDeviceAccount
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
                                Text(
                                    if (user.isApproved) "Guardar cambios" else "Guardar y aprobar acceso",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // ── Desactivar / reactivar ────────────────────────────
                        // Solo OWNER/ADMIN (las reglas lo exigen igual), nunca a uno mismo ni a
                        // un OWNER: quitarle el acceso al dueño se hace a mano, con cuidado.
                        val me = authState.accountUser
                        val canToggle = user.isApproved && user.role != UserRole.OWNER && me != null &&
                            me.id != user.id && (me.role == UserRole.OWNER || me.role == UserRole.ADMIN)
                        if (canToggle) {
                            PermDivider()
                            PermSection("Acceso a la app")
                            Text(
                                if (user.isActive)
                                    "Al desactivarla, esta cuenta no podrá entrar ni ver datos en ningún equipo. " +
                                        "Su historial (ventas, cajas, compras) queda intacto y se puede reactivar."
                                else
                                    "Esta cuenta está desactivada: no puede entrar a la app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (user.isActive) {
                                OutlinedButton(
                                    onClick = { confirmDeactivate = true },
                                    enabled = !state.isLoading,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandColors.Danger)
                                ) {
                                    Icon(Icons.Default.Block, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Desactivar cuenta", fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.setUserActive(companyId, user.id, true) },
                                    enabled = !state.isLoading,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Reactivar cuenta", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        Spacer(Modifier.padding(16.dp))
                    }
                }
            }
        }
    }

    if (confirmDeactivate && user != null) {
        AlertDialog(
            onDismissRequest = { confirmDeactivate = false },
            title = { Text("¿Desactivar a ${user.displayName.ifEmpty { user.email }}?") },
            text = {
                Text(
                    "No podrá entrar a la app desde ningún teléfono ni computadora. Si ahora mismo " +
                        "la tiene abierta, deja de ver datos al instante. Puedes reactivarla cuando quieras."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeactivate = false
                    viewModel.setUserActive(companyId, user.id, false)
                }) { Text("Desactivar", color = BrandColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDeactivate = false }) { Text("Cancelar") } }
        )
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
