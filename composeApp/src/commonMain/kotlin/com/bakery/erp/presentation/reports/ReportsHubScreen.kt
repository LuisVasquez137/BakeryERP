package com.bakery.erp.presentation.reports

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Permission
import com.bakery.erp.model.User
import com.bakery.erp.model.hasPermission
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.theme.BrandColors

/** Cada reporte formal de la app. La navegación traduce la llave a su ruta. */
enum class ReportKey { SALES, PRODUCTION, BRANCH_SUPPLY, FINANCIAL, BRANCH_RESULT, PRODUCT_COST, STANDARD_COST, DELIVERY_COST, CASH_DAY }

private data class ReportLink(
    val key: ReportKey,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val group: String,
    val permission: Permission
)

private const val GROUP_OPERATION = "Ventas y operación"
private const val GROUP_COSTS = "Costos y resultados"

private val ALL_REPORTS = listOf(
    ReportLink(ReportKey.SALES, "Ventas", "Por día, método de pago, cajero y producto",
        Icons.AutoMirrored.Filled.TrendingUp, GROUP_OPERATION, Permission.VIEW_SALES_REPORT),
    ReportLink(ReportKey.CASH_DAY, "Resumen de cajas", "Por día y sucursal: ventas, retiros, lo que quedó y diferencias",
        Icons.Default.PointOfSale, GROUP_OPERATION, Permission.MANAGE_DAILY_CASH_CLOSE),
    ReportLink(ReportKey.PRODUCTION, "Producción", "Planeado vs. producido, eficiencia y mermas",
        Icons.Default.Factory, GROUP_OPERATION, Permission.VIEW_PRODUCTION_REPORT),
    ReportLink(ReportKey.BRANCH_SUPPLY, "Pedido vs. recibido", "Lo que pidió cada sucursal y lo que le llegó",
        Icons.Default.LocalShipping, GROUP_OPERATION, Permission.VIEW_BRANCH_SUPPLY_REPORT),
    ReportLink(ReportKey.FINANCIAL, "Financiero", "Estado del mes: ingresos, costos y gastos",
        Icons.Default.PointOfSale, GROUP_COSTS, Permission.VIEW_FINANCIAL_REPORT),
    ReportLink(ReportKey.BRANCH_RESULT, "Por sucursal", "Lo que gana cada sucursal",
        Icons.Default.Storefront, GROUP_COSTS, Permission.VIEW_FINANCIAL_REPORT),
    ReportLink(ReportKey.PRODUCT_COST, "Costo por producto", "Receta e insumos de cada pan",
        Icons.Default.Calculate, GROUP_COSTS, Permission.VIEW_FINANCIAL_REPORT),
    ReportLink(ReportKey.STANDARD_COST, "Costo estándar", "Costo de planta congelado por mes",
        Icons.Default.Factory, GROUP_COSTS, Permission.VIEW_FINANCIAL_REPORT),
    ReportLink(ReportKey.DELIVERY_COST, "Costo de reparto", "Pilotos, vehículos y viajes",
        Icons.Default.LocalShipping, GROUP_COSTS, Permission.VIEW_FINANCIAL_REPORT),
)

/** ¿Ve al menos un reporte? Es lo que decide si el módulo "Reportes" aparece en el Home. */
fun User.canSeeAnyReport(): Boolean = ALL_REPORTS.any { hasPermission(it.permission) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsHubScreen(
    user: User?,
    onNavigateBack: () -> Unit,
    onOpenReport: (ReportKey) -> Unit
) {
    val visible = if (user == null) emptyList() else ALL_REPORTS.filter { user.hasPermission(it.permission) }
    val groups = visible.groupBy { it.group }

    Scaffold(
        containerColor = BrandColors.Ivory,
        topBar = {
            BrandTopAppBar(
                title = { Text("Reportes", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandColors.Primary)
            )
        }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (visible.isEmpty()) {
                    item {
                        Text(
                            "No tienes acceso a ningún reporte.",
                            color = BrandColors.TextSlate,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                groups.forEach { (group, reports) ->
                    item(key = "hdr_$group") {
                        Text(
                            group,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = BrandColors.TextSlate,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    items(reports, key = { it.key.name }) { report ->
                        ReportRow(report, onClick = { onOpenReport(report.key) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportRow(report: ReportLink, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, BrandColors.Primary.copy(alpha = 0.14f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(BrandColors.PrimaryContainer, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(report.icon, null, tint = BrandColors.Primary, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    report.title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    color = BrandColors.TextSlate
                )
                Text(
                    report.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.MutedSlate
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = BrandColors.MutedSlate)
        }
    }
}
