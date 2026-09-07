package com.bakery.erp.presentation.purchases

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.PurchaseDisplayStatus
import com.bakery.erp.model.PurchaseFilters
import com.bakery.erp.model.PurchaseKpis
import com.bakery.erp.model.PurchasePeriod
import com.bakery.erp.model.PurchaseSort
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipEmphasis
import com.bakery.erp.ui.theme.ChipTone
import com.bakery.erp.ui.theme.chipColors
import com.bakery.erp.util.fmtMoney

/**
 * Color del chip de estado. "Vencida" es lo único en rojo: en una lista donde casi todo está
 * pendiente de pago, pintar de rojo también lo pendiente haría que el rojo dejara de avisar.
 */
internal fun purchaseStatusTone(status: PurchaseDisplayStatus): ChipTone = when (status) {
    PurchaseDisplayStatus.PAID -> ChipTone.SUCCESS
    PurchaseDisplayStatus.OVERDUE -> ChipTone.DANGER
    PurchaseDisplayStatus.PARTIALLY_PAID -> ChipTone.ATTENTION
    PurchaseDisplayStatus.PENDING_RECEIPT -> ChipTone.INFO
    PurchaseDisplayStatus.PENDING_PAYMENT -> ChipTone.NEUTRAL
}

internal fun purchaseStatusColors(status: PurchaseDisplayStatus): Pair<Color, Color> =
    chipColors(purchaseStatusTone(status), ChipEmphasis.SOFT)

@Composable
internal fun PurchaseStatusPill(
    status: PurchaseDisplayStatus,
    modifier: Modifier = Modifier,
    short: Boolean = false
) {
    BrandChip(
        text = if (short) status.shortLabel else status.label,
        tone = purchaseStatusTone(status),
        modifier = modifier
    )
}

// ── KPIs ─────────────────────────────────────────────────────────────────────

/**
 * Los cuatro números que se van a mirar antes que la lista: cuánto compré, cuánto debo,
 * cuánto está vencido y cuánto falta por recibir. Se calculan sobre el período y los filtros
 * ya aplicados — si filtro un proveedor, los KPIs son de ese proveedor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PurchaseKpiRow(
    kpis: PurchaseKpis,
    canSeeAmounts: Boolean,
    onOverdueClick: () -> Unit,
    onPendingReceiptClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        KpiTile(
            label = "Facturas",
            value = kpis.invoiceCount.toString(),
            accent = BrandColors.Primary
        )
        if (canSeeAmounts) {
            KpiTile(
                label = "Comprado",
                value = "Q${kpis.total.fmtMoney()}",
                accent = BrandColors.Primary
            )
            KpiTile(
                label = "Por pagar",
                value = "Q${kpis.outstanding.fmtMoney()}",
                accent = BrandColors.AccentDeep
            )
            KpiTile(
                label = if (kpis.overdueCount > 0) "Vencido · ${kpis.overdueCount}" else "Vencido",
                value = "Q${kpis.overdue.fmtMoney()}",
                accent = if (kpis.overdue > 0) Color(0xFFB3261E) else BrandColors.MutedSlate,
                onClick = onOverdueClick.takeIf { kpis.overdueCount > 0 }
            )
        }
        if (kpis.pendingReceiptCount > 0) {
            KpiTile(
                label = "Por recibir",
                value = kpis.pendingReceiptCount.toString(),
                accent = BrandColors.Primary,
                onClick = onPendingReceiptClick
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KpiTile(
    label: String,
    value: String,
    accent: Color,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier.heightIn(min = 52.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1
            )
        }
    }
}

// ── Fila de chips: período + estados ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PurchaseQuickFilters(
    filters: PurchaseFilters,
    onPeriodSelected: (PurchasePeriod, Long?, Long?) -> Unit,
    onToggleStatus: (PurchaseDisplayStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    var periodMenuOpen by remember { mutableStateOf(false) }
    var showCustomRange by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            AssistChip(
                onClick = { periodMenuOpen = true },
                label = { Text(filters.periodLabel()) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = BrandColors.PrimaryContainer,
                    labelColor = BrandColors.Primary,
                    trailingIconContentColor = BrandColors.Primary
                )
            )
            DropdownMenu(expanded = periodMenuOpen, onDismissRequest = { periodMenuOpen = false }) {
                PurchasePeriod.entries.forEach { period ->
                    DropdownMenuItem(
                        text = { Text(period.label) },
                        trailingIcon = {
                            if (filters.period == period) Icon(Icons.Default.Check, contentDescription = null)
                        },
                        onClick = {
                            periodMenuOpen = false
                            if (period == PurchasePeriod.CUSTOM) showCustomRange = true
                            else onPeriodSelected(period, null, null)
                        }
                    )
                }
            }
        }

        Box(Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant))

        PurchaseDisplayStatus.entries.forEach { status ->
            val selected = status in filters.statuses
            val (bg, fg) = purchaseStatusColors(status)
            FilterChip(
                selected = selected,
                onClick = { onToggleStatus(status) },
                label = { Text(status.shortLabel) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = bg,
                    selectedLabelColor = fg
                )
            )
        }
    }

    if (showCustomRange) {
        PurchaseDateRangeDialog(
            initialFrom = filters.customFrom,
            initialUntil = filters.customUntil,
            onDismiss = { showCustomRange = false },
            onConfirm = { from, until ->
                showCustomRange = false
                onPeriodSelected(PurchasePeriod.CUSTOM, from, until)
            }
        )
    }
}

internal fun PurchaseFilters.periodLabel(): String = when {
    period != PurchasePeriod.CUSTOM -> period.label
    customFrom != null && customUntil != null ->
        "${formatPurchaseDate(customFrom)} – ${formatPurchaseDate(customUntil)}"
    else -> PurchasePeriod.CUSTOM.label
}

/**
 * Rango personalizado en dos pasos con un solo `DatePicker`. Se prefirió esto al
 * `DateRangePicker` de Material porque en teléfono ocupa la pantalla entera y el usuario
 * pierde de vista qué estaba filtrando.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseDateRangeDialog(
    initialFrom: Long?,
    initialUntil: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit
) {
    var pickingUntil by remember { mutableStateOf(false) }
    var from by remember { mutableStateOf(initialFrom) }
    val fromState = rememberDatePickerState(initialSelectedDateMillis = initialFrom)
    val untilState = rememberDatePickerState(initialSelectedDateMillis = initialUntil)

    if (!pickingUntil) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    from = fromState.selectedDateMillis
                    pickingUntil = true
                }) { Text("Siguiente") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
        ) {
            Text(
                "Desde",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp)
            )
            DatePicker(state = fromState)
        }
    } else {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    val f = from ?: return@TextButton
                    val u = untilState.selectedDateMillis ?: f
                    // Si eligió al revés se ordena solo, en vez de devolver una lista vacía.
                    onConfirm(minOf(f, u), maxOf(f, u))
                }) { Text("Aplicar") }
            },
            dismissButton = { TextButton(onClick = { pickingUntil = false }) { Text("Atrás") } }
        ) {
            Text(
                "Hasta",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp)
            )
            DatePicker(state = untilState)
        }
    }
}

// ── Hoja de filtros avanzados ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PurchaseFilterSheet(
    filters: PurchaseFilters,
    branches: List<Pair<String, String>>,
    suppliers: List<Pair<String, String>>,
    onToggleBranch: (String) -> Unit,
    onToggleSupplier: (String) -> Unit,
    onSortSelected: (PurchaseSort) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filtros", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClear, enabled = filters.activeCount > 0) { Text("Limpiar") }
            }

            if (branches.size > 1) {
                FilterSectionTitle("Sucursal")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    branches.forEach { (id, name) ->
                        FilterChip(
                            selected = id in filters.branchIds,
                            onClick = { onToggleBranch(id) },
                            label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
                HorizontalDivider()
            }

            if (suppliers.isNotEmpty()) {
                FilterSectionTitle("Proveedor")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suppliers.forEach { (id, name) ->
                        FilterChip(
                            selected = id in filters.supplierIds,
                            onClick = { onToggleSupplier(id) },
                            label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
                HorizontalDivider()
            }

            FilterSectionTitle("Ordenar por")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PurchaseSort.entries.forEach { sort ->
                    FilterChip(
                        selected = filters.sort == sort,
                        onClick = { onSortSelected(sort) },
                        label = { Text(sort.label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = BrandColors.Primary
    )
}

@Composable
internal fun FilterIconWithBadge(count: Int, content: @Composable () -> Unit) {
    if (count > 0) {
        BadgedBox(badge = { Badge { Text(count.toString()) } }) { content() }
    } else {
        content()
    }
}

