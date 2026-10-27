package com.bakery.erp.presentation.organization

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.UserRole
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.shareText
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors

private val MutedText = Color(0xFF6E6E6E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationSettingsScreen(
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: OrganizationSettingsViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val orgId = authState.user?.orgId ?: ""
    val isOwner = authState.user?.role == UserRole.OWNER
    val snackbarHostState = remember { SnackbarHostState() }
    var showRegenerateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(orgId) { viewModel.loadOrganization(orgId) }
    // Las demás pantallas leen las opciones de producción de la sesión: que vean el cambio ya.
    LaunchedEffect(state.organization) { state.organization?.let(authViewModel::applyOrganization) }
    LaunchedEffect(state.rebuildMessage) {
        state.rebuildMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearRebuildMessage()
        }
    }
    LaunchedEffect(state.justSavedDelivery) {
        if (state.justSavedDelivery) {
            snackbarHostState.showSnackbar("Configuración guardada")
            viewModel.clearJustSavedDelivery()
        }
    }
    LaunchedEffect(state.justRegeneratedCode) {
        if (state.justRegeneratedCode) {
            snackbarHostState.showSnackbar("Código regenerado. Comparte el nuevo.")
            viewModel.clearJustRegeneratedCode()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        containerColor = WarmIvory,
        topBar = {
            BrandTopAppBar(
                title = { Text("Configuración de empresa", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
                state.organization == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Organización no encontrada", color = MutedText)
                }
                else -> {
                    val org = state.organization!!
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // ── Identidad de la empresa ─────────────────────────
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "Identidad",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = PrimaryBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.size(8.dp))
                                LabelValue("Nombre", org.name)
                                LabelValue("Dueño", org.ownerEmail)
                                LabelValue("Plan", org.subscriptionPlan)
                            }
                        }

                        // ── Código de invitación ────────────────────────────
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "Código de invitación",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = PrimaryBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.size(4.dp))
                                Text(
                                    "Compártelo con tus colaboradores y con tus revendedores. Sirve para los dos: en el formulario de registro pueden elegir qué tipo de cuenta crear.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedText
                                )
                                Spacer(Modifier.size(12.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            BurntOrange.copy(alpha = 0.12f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        org.inviteCode.ifEmpty { "—" },
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = BrandColors.AccentDeep,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified
                                    )
                                }
                                Spacer(Modifier.size(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    androidx.compose.material3.Button(
                                        onClick = {
                                            shareText(
                                                text = "Únete a ${org.name} en BakeOS con este código: ${org.inviteCode}",
                                                subject = "Código de invitación"
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = org.inviteCode.isNotEmpty(),
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = BrandColors.AccentDeep
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null)
                                        Spacer(Modifier.size(6.dp))
                                        Text("Compartir")
                                    }
                                    OutlinedButton(
                                        onClick = { showRegenerateDialog = true },
                                        modifier = Modifier.weight(1f),
                                        enabled = !state.isSaving,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Autorenew, contentDescription = null)
                                        Spacer(Modifier.size(6.dp))
                                        Text("Regenerar")
                                    }
                                }
                            }
                        }

                        // ── Flujos de compra que usa la panadería ──────────
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "Flujos de compra",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = PrimaryBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "La materia prima siempre está. Enciende solo lo que tu " +
                                        "panadería realmente compra — cada flujo agrega una " +
                                        "sección a la captura de facturas.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedText
                                )
                                Spacer(Modifier.size(10.dp))
                                PurchaseFlowSetting.entries.forEach { flow ->
                                    val enabled = when (flow) {
                                        PurchaseFlowSetting.RESALE -> org.usesResalePurchases
                                        PurchaseFlowSetting.SUPPLIES -> org.usesSupplyPurchases
                                        PurchaseFlowSetting.SERVICES -> org.usesServicePurchases
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                flow.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = PrimaryBlue,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                if (enabled) flow.on else flow.off,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MutedText
                                            )
                                        }
                                        Switch(
                                            checked = enabled,
                                            onCheckedChange = { viewModel.setPurchaseFlow(orgId, flow, it) },
                                            enabled = !state.isSaving,
                                            colors = SwitchDefaults.colors(checkedTrackColor = BrandColors.Primary)
                                        )
                                    }
                                }
                            }
                        }

                        // ── Formas de trabajar en producción ───────────────
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "Producción",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = PrimaryBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Enciende solo lo que tu panadería hace. Apagado, esas " +
                                        "opciones no aparecen en productos, traslados ni lotes.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedText
                                )
                                Spacer(Modifier.size(10.dp))
                                ProductionOptionSetting.entries.forEach { option ->
                                    val enabled = when (option) {
                                        ProductionOptionSetting.MADE_FOR_STOCK -> org.usesMadeForStock
                                        ProductionOptionSetting.BAKED_AHEAD -> org.usesBakedAhead
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                option.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = PrimaryBlue,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                if (enabled) option.on else option.off,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MutedText
                                            )
                                        }
                                        Switch(
                                            checked = enabled,
                                            onCheckedChange = { viewModel.setProductionOption(orgId, option, it) },
                                            enabled = !state.isSaving,
                                            colors = SwitchDefaults.colors(checkedTrackColor = BrandColors.Primary)
                                        )
                                    }
                                }
                            }
                        }

                        // ── Entregas a domicilio para revendedores ─────────
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "Revendedores",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = PrimaryBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.size(10.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.LocalShipping,
                                        contentDescription = null,
                                        tint = PrimaryBlue.copy(alpha = 0.7f)
                                    )
                                    Spacer(Modifier.size(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Aceptar entregas a domicilio",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = PrimaryBlue,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            if (org.acceptsResellerDelivery)
                                                "Los revendedores pueden elegir entre retirar en sucursal o que les entreguen."
                                            else "Los revendedores solo pueden retirar en sucursal.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MutedText
                                        )
                                    }
                                    Switch(
                                        checked = org.acceptsResellerDelivery,
                                        onCheckedChange = {
                                            viewModel.setAcceptsResellerDelivery(orgId, it)
                                        },
                                        enabled = !state.isSaving,
                                        colors = SwitchDefaults.colors(checkedTrackColor = BrandColors.Primary)
                                    )
                                }
                                if (!org.acceptsResellerDelivery) {
                                    Spacer(Modifier.size(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Storefront,
                                            contentDescription = null,
                                            tint = MutedText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.size(6.dp))
                                        Text(
                                            "Cada revendedor elegirá su sucursal de retiro al registrarse.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MutedText
                                        )
                                    }
                                }
                            }
                        }

                        // ── Mantenimiento (solo dueño) ─────────────────────
                        if (isOwner) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        "Mantenimiento",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = PrimaryBlue,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.size(10.dp))
                                    Text(
                                        "Recalcular los resúmenes diarios de ventas.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PrimaryBlue,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.size(4.dp))
                                    Text(
                                        "Los reportes y la pantalla de inicio leen estos resúmenes en vez de " +
                                            "recorrer venta por venta. Corre esto UNA vez para que aparezca lo " +
                                            "vendido antes de que existieran, y después solo si algún día " +
                                            "quedara con números raros. Es la operación más pesada de la app: " +
                                            "lee cada venta de los últimos 2 años.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MutedText
                                    )
                                    Spacer(Modifier.size(12.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.rebuildSalesRollups(orgId) },
                                        enabled = !state.isRebuildingRollups && orgId.isNotEmpty()
                                    ) {
                                        if (state.isRebuildingRollups) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(Modifier.size(8.dp))
                                            Text("Recalculando…")
                                        } else {
                                            Icon(
                                                Icons.Default.Autorenew,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.size(8.dp))
                                            Text("Recalcular resúmenes de ventas")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRegenerateDialog) {
        AlertDialog(
            onDismissRequest = { showRegenerateDialog = false },
            title = { Text("¿Regenerar código?") },
            text = {
                Text(
                    "El código actual dejará de funcionar inmediatamente. " +
                        "Solo las personas que aún NO se han registrado con el viejo se verán afectadas " +
                        "— los colaboradores y revendedores que ya tienen cuenta siguen funcionando sin cambios. " +
                        "Tendrás que compartir el nuevo código a quienes todavía no se hayan unido."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRegenerateDialog = false
                    viewModel.regenerateInviteCode(orgId)
                }) { Text("Sí, regenerar") }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MutedText)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = PrimaryBlue, fontWeight = FontWeight.Medium)
    }
}
