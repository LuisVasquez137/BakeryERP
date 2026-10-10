package com.bakery.erp.presentation.employees

import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.canAccessBranch
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Employee
import com.bakery.erp.model.JobPosition
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.ProductImage
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobPositionDetailScreen(
    positionId: String,
    onNavigateBack: () -> Unit,
    onNavigateToForm: (String) -> Unit,
    authViewModel: AuthViewModel,
    jobPositionViewModel: JobPositionViewModel,
    employeeViewModel: EmployeeViewModel
) {
    val posState by jobPositionViewModel.uiState.collectAsState()
    val empState by employeeViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) {
            if (posState.positions.isEmpty()) jobPositionViewModel.loadPositions(companyId)
            if (empState.employees.isEmpty()) employeeViewModel.loadEmployees(companyId)
        }
    }

    val position = remember(posState.positions, positionId) {
        posState.positions.find { it.id == positionId }
    }
    val positionEmployees = remember(empState.employees, positionId, authState.user) {
        empState.employees.filter {
            it.positionId == positionId && authState.user?.canAccessBranch(it.branchId) == true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        position?.title ?: "Descriptor de Puesto",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    if (position != null) {
                        IconButton(onClick = { onNavigateToForm(positionId) }) {
                            Icon(Icons.Default.Edit, "Editar", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = BrandColors.Ivory
    ) { padding ->
        when {
            posState.isLoading && position == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            position == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.WorkOutline, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(8.dp))
                    Text("No se encontró el puesto", color = MaterialTheme.colorScheme.outline)
                }
            }

            else -> AdaptiveContainer(modifier = Modifier.padding(padding)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── Encabezado ───────────────────────────────────────────
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(1.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            position.title,
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryBlue
                                        )
                                        if (position.department.isNotBlank()) {
                                            Text(
                                                position.department,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                if (authState.user?.hasPermission(com.bakery.erp.model.Permission.VIEW_EMPLOYEE_SALARY) == true &&
                                    (position.minSalary > 0 || position.maxSalary > 0)) {
                                    HorizontalDivider(color = PrimaryBlue.copy(alpha = 0.10f))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("Rango salarial:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            "Q${position.minSalary.toInt()} – Q${position.maxSalary.toInt()}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Responsabilidades ────────────────────────────────────
                    if (position.responsibilities.isNotEmpty()) {
                        item {
                            PositionSection(title = "Responsabilidades", count = position.responsibilities.size) {
                                position.responsibilities.forEachIndexed { index, item ->
                                    BulletRow(index = index + 1, text = item)
                                }
                            }
                        }
                    }

                    // ── Requisitos ───────────────────────────────────────────
                    if (position.requirements.isNotEmpty()) {
                        item {
                            PositionSection(title = "Requisitos", count = position.requirements.size) {
                                position.requirements.forEachIndexed { index, item ->
                                    BulletRow(index = index + 1, text = item)
                                }
                            }
                        }
                    }

                    // ── Colaboradores con este puesto ────────────────────────
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(1.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Group, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                                    Text(
                                        "Colaboradores en este puesto",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PrimaryBlue
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = PrimaryBlue.copy(alpha = 0.10f)
                                    ) {
                                        Text(
                                            "${positionEmployees.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PrimaryBlue,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                HorizontalDivider(color = PrimaryBlue.copy(alpha = 0.10f))

                                if (empState.isLoading) {
                                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(Modifier.size(24.dp))
                                    }
                                } else if (positionEmployees.isEmpty()) {
                                    Text(
                                        "Ningún colaborador tiene este puesto asignado",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                } else {
                                    positionEmployees.forEach { emp ->
                                        EmployeePositionRow(emp)
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PositionSection(title: String, count: Int, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
                Surface(shape = RoundedCornerShape(50), color = PrimaryBlue.copy(alpha = 0.10f)) {
                    Text(
                        "$count",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            HorizontalDivider(color = PrimaryBlue.copy(alpha = 0.10f))
            content()
        }
    }
}

@Composable
private fun BulletRow(index: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = PrimaryBlue.copy(alpha = 0.12f),
            modifier = Modifier.size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "$index",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue,
                    textAlign = TextAlign.Center
                )
            }
        }
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EmployeePositionRow(employee: Employee) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (employee.photoUrl.isNotBlank()) {
            Box(modifier = Modifier.size(36.dp).clip(CircleShape)) {
                ProductImage(imageUrl = employee.photoUrl, contentDescription = null, modifier = Modifier.size(36.dp))
            }
        } else {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(PrimaryBlue.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(22.dp), tint = PrimaryBlue.copy(alpha = 0.5f))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(employee.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (employee.branchName.isNotBlank()) {
                Text(employee.branchName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        if (employee.branchName.isNotBlank()) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    employee.branchName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    maxLines = 1
                )
            }
        }
    }
}
