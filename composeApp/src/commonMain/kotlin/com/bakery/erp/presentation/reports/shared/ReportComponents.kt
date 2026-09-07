package com.bakery.erp.presentation.reports.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.ui.theme.BrandColors

// Alias publicos de la marca: los tres ReportScreen los importan por nombre.
// Apuntan a BrandColors para no re-declarar el hex (ver ui/theme/AppTheme.kt).
val ReportPrimary = BrandColors.Primary
val ReportAccent  = BrandColors.Accent
val ReportMuted   = Color(0xFF607585)
val ReportSlate   = BrandColors.TextSlate
val ReportSurface = BrandColors.Ivory

enum class ReportPeriod(val label: String) {
    DAY("Día"),
    WEEK("Semana"),
    MONTH("Mes")
}

@Composable
fun ReportPeriodSelector(
    selected: ReportPeriod,
    onChange: (ReportPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ReportPeriod.values().forEach { p ->
            FilterChip(
                selected = selected == p,
                onClick = { onChange(p) },
                label = { Text(p.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ReportPrimary,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
fun PeriodNavigator(
    label: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCenterClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Anterior", tint = ReportPrimary)
        }
        val centerMod = Modifier.weight(1f).let {
            if (onCenterClick != null) it.clickable { onCenterClick() } else it
        }
        Text(
            text = label,
            modifier = centerMod,
            fontWeight = FontWeight.SemiBold,
            color = ReportSlate,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Siguiente", tint = ReportPrimary)
        }
    }
}

@Composable
fun BranchFilterRow(
    branches: List<Pair<String, String>>, // (id, name)
    selectedBranchId: String?,             // null = "todas"
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (branches.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AssistChip(
            onClick = { onSelect(null) },
            label = { Text("Todas") },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (selectedBranchId == null) ReportPrimary.copy(alpha = 0.12f)
                                 else Color.Transparent,
                labelColor = if (selectedBranchId == null) ReportPrimary else ReportMuted
            )
        )
        branches.forEach { (id, name) ->
            val selected = id == selectedBranchId
            AssistChip(
                onClick = { onSelect(id) },
                label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (selected) ReportPrimary.copy(alpha = 0.12f)
                                     else Color.Transparent,
                    labelColor = if (selected) ReportPrimary else ReportMuted
                )
            )
        }
    }
}

@Composable
fun KpiTile(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color = ReportPrimary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, Color(0xFFEDE7E1))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = ReportSlate)
            Text(label, style = MaterialTheme.typography.labelSmall, color = ReportMuted)
        }
    }
}

@Composable
fun ReportSection(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = ReportSlate)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = ReportMuted)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * Gráfica de barras verticales. Cada par es (label, value). Altura total fija; las barras
 * se escalan al valor máximo. Si todos los valores son 0, muestra "Sin datos".
 */
@Composable
fun VerticalBars(
    items: List<Pair<String, Double>>,
    barColor: Color = ReportPrimary,
    modifier: Modifier = Modifier,
    barWidth: androidx.compose.ui.unit.Dp = 24.dp
) {
    val maxVal = items.maxOfOrNull { it.second } ?: 0.0
    if (items.isEmpty() || maxVal <= 0) {
        EmptyDataNote(modifier)
        return
    }
    Row(
        modifier = modifier.fillMaxWidth().height(140.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.Bottom
    ) {
        items.forEach { (label, v) ->
            val frac = (v / maxVal).toFloat().coerceAtLeast(0.04f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .width(barWidth)
                        .fillMaxHeight(frac)
                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                        .background(barColor.copy(alpha = 0.75f))
                )
                Spacer(Modifier.height(6.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = ReportMuted, maxLines = 1)
            }
        }
    }
}

/**
 * Pares de barras (planeado vs producido) por etiqueta — para reporte de producción.
 */
@Composable
fun PairedBars(
    items: List<Triple<String, Double, Double>>, // label, leftValue, rightValue
    leftColor: Color = ReportPrimary,
    rightColor: Color = ReportAccent,
    leftLegend: String = "Planeado",
    rightLegend: String = "Producido",
    modifier: Modifier = Modifier
) {
    val maxVal = items.maxOfOrNull { maxOf(it.second, it.third) } ?: 0.0
    if (items.isEmpty() || maxVal <= 0) {
        EmptyDataNote(modifier)
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendDot(leftLegend, leftColor)
            LegendDot(rightLegend, rightColor)
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(140.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.Bottom
        ) {
            items.forEach { (label, l, r) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Bar(value = l, max = maxVal, color = leftColor)
                        Bar(value = r, max = maxVal, color = rightColor)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = ReportMuted, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Bar(value: Double, max: Double, color: Color) {
    val frac = if (max > 0) (value / max).toFloat().coerceAtLeast(0.04f) else 0.04f
    Box(
        modifier = Modifier
            .width(12.dp)
            .fillMaxHeight(frac)
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .background(color)
    )
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = ReportMuted)
    }
}

@Composable
fun HorizontalBars(
    items: List<Pair<String, Double>>,
    barColor: Color = ReportPrimary,
    valueFormatter: (Double) -> String = { it.toString() },
    modifier: Modifier = Modifier
) {
    val maxVal = items.maxOfOrNull { it.second } ?: 0.0
    if (items.isEmpty() || maxVal <= 0) {
        EmptyDataNote(modifier)
        return
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, v) ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = ReportSlate, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(valueFormatter(v), style = MaterialTheme.typography.labelSmall, color = ReportMuted)
                }
                val frac = (v / maxVal).toFloat().coerceAtLeast(0.02f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFFEDE7E1))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(frac)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(barColor)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyDataNote(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Sin datos para los filtros seleccionados",
            style = MaterialTheme.typography.bodySmall,
            color = ReportMuted
        )
    }
}
