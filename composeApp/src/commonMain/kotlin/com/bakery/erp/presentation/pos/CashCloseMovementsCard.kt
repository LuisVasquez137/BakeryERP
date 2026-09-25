package com.bakery.erp.presentation.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CashMovement
import com.bakery.erp.model.CashMovementType
import com.bakery.erp.model.CashSessionDetailItem
import com.bakery.erp.model.Purchase
import com.bakery.erp.model.cashSessionDetailItems
import com.bakery.erp.model.listTitle
import com.bakery.erp.ui.ProductImage
import com.bakery.erp.ui.ZoomableImageDialog
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmtClockTime
import com.bakery.erp.util.fmtMoney

/** Cuántos renglones se ven antes de "Ver todas": el cierre es para contar, no para leer. */
private const val COLLAPSED_ROWS = 4

/**
 * Qué salió (y qué entró) de la gaveta, renglón por renglón, en el cierre.
 *
 * El resumen de arriba dice "Gastos y retiros Q 385" y nada más: quien cuenta el cajón no puede
 * aceptar un faltante que no sabe de dónde viene. Cada renglón es una sola línea —hora, qué,
 * quién, monto— con la foto del comprobante como miniatura que se abre en grande; así una
 * caja con quince gastos sigue cabiendo en una pantalla sin volverse una hoja de cálculo.
 *
 * Los renglones salen de `cashSessionDetailItems`, que pega cada corrección a su gasto: la suma
 * de la lista es exactamente el total del resumen, y un gasto eliminado no aparece.
 */
@Composable
internal fun CashCloseMovementsCard(
    movements: List<CashMovement>,
    purchasesById: Map<String, Purchase>
) {
    val items = remember(movements) { cashSessionDetailItems(movements) }
    if (items.isEmpty()) return
    val outflows = items.filter { it.isOutflow }
    val inflows = items.filterNot { it.isOutflow }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var photo by remember { mutableStateOf<Pair<String, String>?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (outflows.isNotEmpty()) {
                SectionTitle("Salidas de la caja", outflows.size, outflows.sumOf { it.amount }, isOutflow = true)
                val shown = if (expanded) outflows else outflows.take(COLLAPSED_ROWS)
                shown.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MovementRow(item, purchasesById[item.movement.sourcePurchaseId]) { url, title ->
                        photo = url to title
                    }
                }
                if (outflows.size > COLLAPSED_ROWS) {
                    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (expanded) "Ver menos" else "Ver las ${outflows.size} salidas")
                    }
                }
            }
            // Las entradas casi siempre son una o dos (cambio de la caja central): se listan
            // enteras y debajo, porque lo que se viene a revisar al cerrar son las salidas.
            if (inflows.isNotEmpty()) {
                if (outflows.isNotEmpty()) Spacer(Modifier.padding(top = 8.dp))
                SectionTitle("Entradas (no ventas)", inflows.size, inflows.sumOf { it.amount }, isOutflow = false)
                inflows.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MovementRow(item, null) { _, _ -> }
                }
            }
        }
    }

    photo?.let { (url, title) ->
        ZoomableImageDialog(imageUrl = url, title = title, onDismiss = { photo = null })
    }
}

@Composable
private fun SectionTitle(title: String, count: Int, total: Double, isOutflow: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$title · $count",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = BrandColors.Primary,
            modifier = Modifier.weight(1f)
        )
        Text(
            (if (isOutflow) "−Q " else "+Q ") + total.fmtMoney(2),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MovementRow(
    item: CashSessionDetailItem,
    purchase: Purchase?,
    onOpenPhoto: (url: String, title: String) -> Unit
) {
    val m = item.movement
    // Con compra detrás se nombra por lo que se compró (sale de `itemNames`, ya denormalizado);
    // el motivo del movimiento es un texto armado al guardar y no dice más que eso.
    val title = purchase?.listTitle()?.takeIf { it.isNotBlank() }
        ?: m.reason.ifBlank { CashMovementType.listLabel(m) }
    val subtitle = listOfNotNull(
        m.createdAt.fmtClockTime().ifEmpty { null },
        purchase?.supplierName?.takeIf { it.isNotBlank() && !purchase.listTitle().contains(it) },
        m.createdByUserName.ifBlank { null },
        CashMovementType.listLabel(m).takeIf { purchase == null && m.reason.isNotBlank() },
        "corregido".takeIf { item.wasCorrected }
    ).joinToString(" · ")
    val photoUrl = purchase?.invoicePhotoUrl?.takeIf { it.isNotBlank() }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BrandColors.Sand)
                .then(if (photoUrl != null) Modifier.clickable { onOpenPhoto(photoUrl, title) } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (photoUrl != null) {
                ProductImage(imageUrl = photoUrl, contentDescription = "Comprobante", modifier = Modifier.size(40.dp))
            } else {
                Icon(
                    when {
                        m.transferId != null -> Icons.Default.SwapHoriz
                        m.sourcePurchaseId.isNotEmpty() -> Icons.AutoMirrored.Filled.ReceiptLong
                        else -> Icons.Default.Payments
                    },
                    contentDescription = null,
                    tint = BrandColors.MutedSlate,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            (if (item.isOutflow) "−Q " else "+Q ") + item.amount.fmtMoney(2),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (item.isOutflow) MaterialTheme.colorScheme.onSurface else BrandColors.Primary
        )
    }
}
