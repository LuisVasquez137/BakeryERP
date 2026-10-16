package com.bakery.erp.presentation.fel

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Branch
import com.bakery.erp.model.FelEnvironment
import com.bakery.erp.model.FelProfile
import com.bakery.erp.model.formatNit
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipTone

/**
 * Facturación electrónica: qué NIT emite en cada sucursal. Lo configura el dueño una vez;
 * después la cajera solo ve "Facturar" en el cobro.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FelSetupScreen(
    onNavigateBack: () -> Unit,
    onOpenProfile: (profileId: String?) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: FelViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(companyId) { viewModel.load(companyId) }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        containerColor = BrandColors.Ivory,
        topBar = {
            BrandTopAppBar(
                title = { Text("Facturación electrónica", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load(companyId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Recargar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColors.Primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenProfile(null) },
                containerColor = BrandColors.Primary,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Agregar NIT") }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        AdaptiveContainer(Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoading && state.profiles.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = BrandColors.Primary)
                }
                return@AdaptiveContainer
            }
            val unassigned = state.branches.filter { b -> state.profiles.none { it.id == b.felProfileId } }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Cada NIT es una identidad ante la SAT. Las sucursales facturan con el NIT que " +
                            "les asignes, cada una con su código de establecimiento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandColors.MutedSlate
                    )
                }
                if (state.profiles.isEmpty()) {
                    item {
                        Text(
                            "Todavía no hay ningún NIT. Agregá el primero con los datos de tu RTU y las " +
                                "credenciales que te dio Digifact.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BrandColors.TextSlate,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                }
                items(state.profiles, key = { it.id }) { profile ->
                    ProfileCard(profile, state.branchesOf(profile.id)) { onOpenProfile(profile.id) }
                }
                if (unassigned.isNotEmpty() && state.profiles.isNotEmpty()) {
                    item {
                        UnassignedBranchesCard(unassigned)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileCard(profile: FelProfile, branches: List<Branch>, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                profile.nombreComercial.ifBlank { profile.nombreEmisor },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = BrandColors.TextSlate
            )
            Text(
                "NIT ${formatNit(profile.nit)} · ${profile.nombreEmisor}",
                style = MaterialTheme.typography.bodySmall,
                color = BrandColors.MutedSlate
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BrandChip(profile.afiliacionIVA.docType, ChipTone.INFO)
                when {
                    !profile.active -> BrandChip("Desactivado", ChipTone.NEUTRAL)
                    // Sin credenciales el NIT existe pero no puede certificar nada: es lo primero
                    // que tiene que saltar a la vista.
                    !profile.hasCredentials -> BrandChip("Sin credenciales", ChipTone.DANGER)
                    profile.environment == FelEnvironment.PRODUCCION -> BrandChip("Producción", ChipTone.SUCCESS)
                    else -> BrandChip("Pruebas", ChipTone.ATTENTION)
                }
            }
            if (branches.isEmpty()) {
                Text(
                    "Ninguna sucursal factura con este NIT.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.AccentDeep
                )
            } else {
                branches.sortedBy { it.felEstablishmentCode.toIntOrNull() ?: Int.MAX_VALUE }.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Storefront, contentDescription = null, tint = BrandColors.Primary, modifier = Modifier.size(16.dp))
                        Text(
                            "${b.name} · establecimiento ${b.felEstablishmentCode}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BrandColors.TextSlate,
                            modifier = Modifier.weight(1f)
                        )
                        if (b.invoiceByDefault) BrandChip("Factura por defecto", ChipTone.BRAND)
                    }
                }
            }
        }
    }
}

@Composable
private fun UnassignedBranchesCard(branches: List<Branch>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BrandColors.Sand),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("No facturan", fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate)
            Text(
                branches.joinToString(" · ") { it.name },
                style = MaterialTheme.typography.bodyMedium,
                color = BrandColors.TextSlate
            )
            Text(
                "Se asignan desde el NIT con el que van a facturar.",
                style = MaterialTheme.typography.bodySmall,
                color = BrandColors.MutedSlate
            )
        }
    }
}
