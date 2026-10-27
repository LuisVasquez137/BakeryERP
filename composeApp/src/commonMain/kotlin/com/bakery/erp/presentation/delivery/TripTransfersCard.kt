package com.bakery.erp.presentation.delivery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.StockTransfer
import com.bakery.erp.model.StockTransferStatus
import com.bakery.erp.model.User
import com.bakery.erp.model.isPickupFor
import com.bakery.erp.model.linesSummary
import com.bakery.erp.presentation.transfers.canReceive
import com.bakery.erp.presentation.transfers.canRequestTransfers
import com.bakery.erp.presentation.transfers.canSendRequest
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipEmphasis
import com.bakery.erp.ui.theme.ChipTone

/**
 * Pedidos entre sucursales que van en este viaje del camión (pan molde, pan tostado). Lo que no
 * sale de la planta dice "Recoger en Calvario": el repartidor pasa por ahí antes de repartir.
 * Ver "Pedido de la sucursal" en `model/StockTransfer.kt`.
 *
 * [onOpen] null = solo se lee (la hoja del repartidor: él no abre traslados).
 */
@Composable
internal fun TripTransfersCard(
    transfers: List<StockTransfer>,
    carrierPlantId: String,
    user: User?,
    supervision: Boolean,
    onOpen: ((transferId: String) -> Unit)?
) {
    // Primero lo que hay que recoger (es la parada extra), después por destino.
    val ordered = transfers.sortedWith(
        compareByDescending<StockTransfer> { it.isPickupFor(carrierPlantId) }
            .thenBy { it.originBranchName.lowercase() }
            .thenBy { it.destinationBranchName.lowercase() }
    )
    val canOpen = onOpen != null && user?.canRequestTransfers() == true
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.SyncAlt, null, tint = BrandColors.Primary, modifier = Modifier.size(18.dp))
                Text(
                    "  Pedidos entre sucursales (${transfers.size})",
                    color = BrandColors.Primary, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            ordered.forEachIndexed { i, t ->
                if (i > 0) HorizontalDivider(color = BrandColors.Sand, modifier = Modifier.padding(horizontal = 14.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (canOpen) Modifier.clickable { onOpen?.invoke(t.id) } else Modifier)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (t.isPickupFor(carrierPlantId)) "Recoger en ${t.originBranchName} → ${t.destinationBranchName}"
                            else "${t.originBranchName} → ${t.destinationBranchName}",
                            fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            t.linesSummary(), color = BrandColors.MutedSlate,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                    TransferStateChip(t, user, supervision)
                }
            }
        }
    }
}

@Composable
private fun TransferStateChip(t: StockTransfer, user: User?, supervision: Boolean) {
    // Igual que las tarjetas del tablero: solo late lo que te toca, y al dueño nada.
    val mine = !supervision && user != null && (user.canSendRequest(t) || user.canReceive(t))
    val emphasis = if (mine) ChipEmphasis.SOLID else ChipEmphasis.SOFT
    when (t.status) {
        StockTransferStatus.REQUESTED ->
            BrandChip(if (mine) "Por mandar" else "Sin mandar", ChipTone.ATTENTION, emphasis)
        StockTransferStatus.IN_TRANSIT ->
            BrandChip(if (mine) "Por recibir" else "Enviado", if (mine) ChipTone.ATTENTION else ChipTone.PROGRESS, emphasis)
        StockTransferStatus.RECEIVED ->
            BrandChip(if (t.shortages.isNotEmpty()) "Llegó incompleto" else "Recibido",
                if (t.shortages.isNotEmpty()) ChipTone.DANGER else ChipTone.SUCCESS)
        else -> BrandChip(StockTransferStatus.label(t.status), ChipTone.NEUTRAL)
    }
}
