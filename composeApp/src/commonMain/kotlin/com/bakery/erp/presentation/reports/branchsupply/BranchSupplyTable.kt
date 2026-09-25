package com.bakery.erp.presentation.reports.branchsupply

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.BranchSupplyReport
import com.bakery.erp.model.BranchSupplyRow
import com.bakery.erp.model.DiscrepancyType
import com.bakery.erp.model.SupplyShiftColumn
import com.bakery.erp.model.SupplyShiftQty
import com.bakery.erp.presentation.production.RecipeRef
import com.bakery.erp.presentation.production.groupByMasa
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipTone
import com.bakery.erp.util.fmtTrim

/**
 * Tabla del reporte "Pedido vs. recibido", agrupada por masa. La usan el reporte por
 * período y la sección "Seguimiento" del detalle de un pedido.
 *
 * En ≥720dp es una tabla con una columna por turno; en teléfono, una tarjeta por
 * producto con la misma información en renglones.
 */
@Composable
internal fun BranchSupplyTable(
    report: BranchSupplyReport,
    recipeRefByProductId: Map<String, RecipeRef>,
    showSold: Boolean,
    modifier: Modifier = Modifier
) {
    val groups = groupByMasa(
        items = report.rows,
        recipeIdOf = { r -> if (r.isSpecial) r.specialRecipeId else recipeRefByProductId[r.productId]?.id },
        recipeNameOf = { r -> if (r.isSpecial) r.specialRecipeName else recipeRefByProductId[r.productId]?.name },
        nameOf = { it.productName }
    )
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = maxWidth >= 720.dp
        Column(verticalArrangement = Arrangement.spacedBy(if (wide) 0.dp else 10.dp)) {
            if (wide) WideHeader(report.columns, showSold)
            groups.forEach { group ->
                Text(
                    group.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.Primary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )
                group.items.forEachIndexed { index, row ->
                    if (wide) WideRow(row, report.columns, showSold, striped = index % 2 == 1)
                    else NarrowCard(row, report.columns, showSold)
                }
            }
        }
    }
}

// ── Tabla ancha ──────────────────────────────────────────────────────────────

private const val NAME_WEIGHT = 2.4f
private const val NUM_WEIGHT = 1f

@Composable
private fun WideHeader(columns: List<SupplyShiftColumn>, showSold: Boolean) {
    Row(
        Modifier.fillMaxWidth()
            .background(BrandColors.PrimaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell("Producto", NAME_WEIGHT, TextAlign.Start)
        HeaderCell("Pedido", NUM_WEIGHT)
        columns.forEach { HeaderCell(shiftTitle(it), NUM_WEIGHT) }
        HeaderCell("Enviado", NUM_WEIGHT)
        HeaderCell("Recibido", NUM_WEIGHT)
        HeaderCell("Dif.", NUM_WEIGHT)
        if (showSold) HeaderCell("Vendido", NUM_WEIGHT)
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float, align: TextAlign = TextAlign.End) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        textAlign = align,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = BrandColors.Primary
    )
}

@Composable
private fun WideRow(row: BranchSupplyRow, columns: List<SupplyShiftColumn>, showSold: Boolean, striped: Boolean) {
    Column(
        Modifier.fillMaxWidth()
            .background(if (striped) BrandColors.Ivory else Color.White)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(NAME_WEIGHT)) {
                ProductName(row)
                RowFlags(row)
            }
            NumCell(qty(row.ordered), NUM_WEIGHT, bold = true)
            columns.forEach { col ->
                Column(Modifier.weight(NUM_WEIGHT), horizontalAlignment = Alignment.End) {
                    ShiftCellContent(row.byShift[col.shiftId])
                }
            }
            NumCell(qty(row.sent), NUM_WEIGHT)
            Column(Modifier.weight(NUM_WEIGHT), horizontalAlignment = Alignment.End) {
                ReceivedContent(row)
            }
            Column(Modifier.weight(NUM_WEIGHT), horizontalAlignment = Alignment.End) {
                DiffContent(row)
            }
            if (showSold) NumCell(row.sold?.let(::qty) ?: "—", NUM_WEIGHT)
        }
    }
    HorizontalDivider(color = Color(0xFFEDE7E1))
}

@Composable
private fun RowScope.NumCell(text: String, weight: Float, bold: Boolean = false) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        textAlign = TextAlign.End,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = BrandColors.TextSlate
    )
}

// ── Teléfono ─────────────────────────────────────────────────────────────────

@Composable
private fun NarrowCard(row: BranchSupplyRow, columns: List<SupplyShiftColumn>, showSold: Boolean) {
    Column(
        Modifier.fillMaxWidth()
            .background(BrandColors.Ivory, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ProductName(row)
        RowFlags(row)
        NarrowLine("Pedido") {
            Text(qty(row.ordered), fontWeight = FontWeight.Bold, color = BrandColors.TextSlate)
        }
        columns.forEach { col ->
            NarrowLine(shiftTitle(col)) { ShiftCellContent(row.byShift[col.shiftId]) }
        }
        NarrowLine("Enviado") { Text(qty(row.sent), color = BrandColors.TextSlate) }
        NarrowLine("Recibido") { ReceivedContent(row) }
        if (row.sentCounted > 0) NarrowLine("Diferencia") { DiffContent(row) }
        if (showSold) NarrowLine("Vendido") { Text(row.sold?.let(::qty) ?: "—", color = BrandColors.TextSlate) }
    }
}

@Composable
private fun NarrowLine(label: String, value: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = BrandColors.MutedSlate
        )
        Column(horizontalAlignment = Alignment.End) { value() }
    }
}

// ── Piezas compartidas ───────────────────────────────────────────────────────

@Composable
private fun ProductName(row: BranchSupplyRow) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (row.isSpecial) {
            Icon(
                Icons.Default.Star,
                contentDescription = "Pedido especial",
                tint = BrandColors.AccentDeep,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            row.productName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = BrandColors.TextSlate
        )
    }
}

/** Avisos de la fila: lo que explica por qué los números no cuadran todavía. */
@Composable
private fun RowFlags(row: BranchSupplyRow) {
    val flags = buildList {
        if (row.splitMismatch) add("Los turnos no suman lo pedido" to ChipTone.ATTENTION)
        if (row.notYetPlanned > 0) add("${qty(row.notYetPlanned)} sin lote todavía" to ChipTone.NEUTRAL)
        if (row.cancelled > 0) add("${qty(row.cancelled)} en nota cancelada" to ChipTone.NEUTRAL)
    }
    if (flags.isEmpty()) return
    // Pocos avisos por fila: una columna basta.
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        flags.forEach { (text, tone) -> BrandChip(text, tone) }
    }
}

@Composable
private fun ShiftCellContent(shift: SupplyShiftQty?) {
    if (shift == null || shift.assigned <= 0.0) {
        Text("—", color = BrandColors.MutedSlate)
        return
    }
    Text(qty(shift.assigned), color = BrandColors.TextSlate, style = MaterialTheme.typography.bodyMedium)
    when {
        shift.packingPending ->
            Text("por empacar", style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate)
        shift.packed != shift.assigned ->
            Text(
                "empacado ${qty(shift.packed)}",
                style = MaterialTheme.typography.labelSmall,
                color = BrandColors.AccentDeep
            )
    }
}

@Composable
private fun ReceivedContent(row: BranchSupplyRow) {
    if (row.sentCounted <= 0.0 && row.awaitingCount > 0.0) {
        // Sin contar todavía: un 0 acá se leería como que no llegó nada.
        BrandChip("Sin contar", ChipTone.PROGRESS)
        return
    }
    Text(
        if (row.sentCounted > 0.0 || row.received > 0.0) qty(row.received) else "—",
        color = BrandColors.TextSlate,
        style = MaterialTheme.typography.bodyMedium
    )
    if (row.awaitingCount > 0.0) {
        Text(
            "+${qty(row.awaitingCount)} sin contar",
            style = MaterialTheme.typography.labelSmall,
            color = BrandColors.MutedSlate
        )
    }
}

@Composable
private fun DiffContent(row: BranchSupplyRow) {
    val diff = row.receptionDiff
    if (row.sentCounted <= 0.0 || kotlin.math.abs(diff) < 0.001) {
        Text(if (row.sentCounted > 0.0) "0" else "—", color = BrandColors.MutedSlate)
        return
    }
    Text(
        (if (diff > 0) "+" else "") + qty(diff),
        fontWeight = FontWeight.Bold,
        color = if (diff < 0) MaterialTheme.colorScheme.error else BrandColors.Primary,
        style = MaterialTheme.typography.bodyMedium
    )
    val reasons = row.discrepancyTypes.map { DiscrepancyType.label(it) }.filter { it.isNotEmpty() }
    if (reasons.isNotEmpty()) {
        Text(
            reasons.joinToString(", "),
            style = MaterialTheme.typography.labelSmall,
            color = BrandColors.MutedSlate
        )
    }
}

private fun shiftTitle(col: SupplyShiftColumn) = col.shiftName.ifBlank { "Despacho" }

internal fun qty(value: Double): String = value.fmtTrim(1)
