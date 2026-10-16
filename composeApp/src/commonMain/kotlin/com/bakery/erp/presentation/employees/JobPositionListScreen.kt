package com.bakery.erp.presentation.employees

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.JobPosition
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobPositionListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToForm: (String?) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: JobPositionViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) viewModel.loadPositions(companyId)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    // Group by department
    val grouped = remember(state.positions) {
        state.positions.groupBy { it.department.ifBlank { "Sin departamento" } }
    }

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Descriptores de Puesto", fontWeight = FontWeight.SemiBold) },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToForm(null) },
                containerColor = PrimaryBlue
            ) { Icon(Icons.Default.Add, "Nuevo puesto", tint = Color.White) }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BrandColors.Ivory
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.positions.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.WorkOutline, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                    Text(
                        "Sin puestos registrados",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Toca + para crear el primer descriptor de puesto",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                grouped.forEach { (department, positions) ->
                    item(key = "header_$department") {
                        DepartmentHeader(department)
                    }
                    items(positions, key = { it.id }) { position ->
                        JobPositionCard(
                            position = position,
                            showSalary = authState.user?.hasPermission(com.bakery.erp.model.Permission.VIEW_EMPLOYEE_SALARY) == true,
                            onClick = { onNavigateToDetail(position.id) }
                        )
                    }
                    item(key = "spacer_$department") { Spacer(Modifier.height(8.dp)) }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
private fun DepartmentHeader(department: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Business, null, modifier = Modifier.size(16.dp), tint = PrimaryBlue)
        Text(
            department.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlue
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = PrimaryBlue.copy(alpha = 0.2f))
    }
}

@Composable
private fun JobPositionCard(position: JobPosition, showSalary: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    position.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (position.responsibilities.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${position.responsibilities.size} responsabilidad(es)  •  ${position.requirements.size} requisito(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showSalary && (position.minSalary > 0.0 || position.maxSalary > 0.0)) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Q${position.minSalary.toInt()} – Q${position.maxSalary.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = if (position.active) BrandColors.PrimaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    if (position.active) "Activo" else "Inactivo",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (position.active) PrimaryBlue else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
