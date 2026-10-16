package com.bakery.erp.presentation.dispatch

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.production.ProductionViewModel
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.theme.BrandColors

/**
 * Despacho: solo el empaque de la planta, con la matriz de siempre (producto por producto
 * para todas las sucursales del turno) y su "Cerrar turno".
 *
 * Antes tenía cinco pestañas (Por empacar, Listas, En tránsito, Pendiente conteo,
 * Recibidas) y entraban también las sucursales a firmar y contar. Todo eso es ahora el
 * tablero de Reparto, que contesta la pregunta que cada uno trae —"¿cómo va mi pan?"—
 * en vez de obligar a saber en qué estado está una nota para encontrarla. El ícono de
 * camión de la barra lleva a ese tablero.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchNoteListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBoard: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: DispatchViewModel,
    productionViewModel: ProductionViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                BrandTopAppBar(
                    title = { Text("Despacho", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToBoard) {
                            Icon(Icons.Default.LocalShipping, contentDescription = "Ver el reparto del día")
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = BrandColors.Ivory
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (user != null) PackingMatrixContent(companyId, user, viewModel, productionViewModel)
            }
        }
        LoadingOverlay(visible = state.isLoading)
    }
}
