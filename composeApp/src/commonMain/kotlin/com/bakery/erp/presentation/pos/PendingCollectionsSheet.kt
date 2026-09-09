package com.bakery.erp.presentation.pos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Sale
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.model.effectivePaymentBreakdown
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipTone
import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtClockTime

/**
 * Cuentas del restaurante que el mesero cobró y todavía no entregó a ninguna caja.
 *
 * **Esto no es un segundo cobro.** El cliente ya pagó en ComandaYa; acá se registra que el
 * efectivo cambió de manos y quedó en ESTA gaveta. Con dos cajas por sucursal y cualquiera
 * capaz de recibir, no hay forma de deducirlo: el único que lo sabe es quien recibe. Por eso
 * el botón dice "Recibí el dinero" y nunca "cobrar" — si dijera cobrar, el personal creería
 * que está cobrando de nuevo.
 *
 * La misma lista la ven todas las cajas de la sucursal, y la fila desaparece sola en cuanto
 * una la recibe: el listener deja de traerla porque ya no está pendiente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingCollectionsSheet(
    pending: List<Sale>,
    isCollecting: Boolean,
    onCollect: (Sale) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirming by remember { mutableStateOf<Sale?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                "Cuentas del restaurante",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = BrandColors.Primary
            )
            Spacer(Modifier.size(4.dp))
            Text(
                if (pending.isEmpty())
                    "No hay cuentas pendientes de entregar a caja."
                else
                    "El cliente ya pagó. Seleccioná la cuenta cuando el mesero te entregue el dinero.",
                style = MaterialTheme.typography.bodySmall,
                color = BrandColors.MutedSlate
            )
            Spacer(Modifier.size(12.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pending, key = { it.id }) { sale ->
                    PendingRow(sale = sale, onClick = { confirming = sale })
                }
            }

            if (pending.isNotEmpty()) {
                Spacer(Modifier.size(12.dp))
                HorizontalDivider()
                Spacer(Modifier.size(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${pending.size} cuenta${if (pending.size == 1) "" else "s"} sin entregar",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandColors.MutedSlate
                    )
                    Text(
                        "Q ${pending.sumOf { it.total }.fmt(2)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandColors.Primary
                    )
                }
            }
        }
    }

    confirming?.let { sale ->
        AlertDialog(
            onDismissRequest = { if (!isCollecting) confirming = null },
            title = { Text("¿Recibiste el dinero?") },
            text = {
                Column {
                    Text(
                        listOfNotNull(
                            sale.externalOrderNumber.takeIf { it.isNotEmpty() }?.let { "Orden #$it" },
                            sale.externalTableLabel.ifEmpty { null },
                            sale.externalOrderAt.fmtClockTime().ifEmpty { null }
                        ).joinToString(" · ").ifEmpty { "Cuenta de restaurante" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (sale.externalUserName.isNotEmpty()) {
                        Text(
                            "Atendió ${sale.externalUserName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BrandColors.MutedSlate
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    sale.effectivePaymentBreakdown.forEach { (method, amount) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(SalePaymentMethod.label(method), style = MaterialTheme.typography.bodyMedium)
                            Text("Q ${amount.fmt(2)}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "Q ${sale.total.fmt(2)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = BrandColors.Primary
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "Esto no le cobra otra vez al cliente: registra que el dinero entró a " +
                            "esta caja y suma al cuadre de este turno.",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandColors.MutedSlate
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onCollect(sale); confirming = null },
                    enabled = !isCollecting,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary)
                ) {
                    if (isCollecting) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text("Sí, recibí el dinero")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }, enabled = !isCollecting) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun PendingRow(sale: Sale, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BrandColors.Sand)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // El número de orden va primero y en negrita: es como el mesero y la
                    // cajera se refieren a la cuenta en voz alta ("la 47"). Sin él hay que
                    // adivinar por mesa y monto.
                    if (sale.externalOrderNumber.isNotEmpty()) {
                        Text(
                            "#${sale.externalOrderNumber}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = BrandColors.Primary
                        )
                        Spacer(Modifier.size(6.dp))
                    }
                    Text(
                        sale.externalTableLabel.ifEmpty { "Cuenta de restaurante" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandColors.TextSlate
                    )
                    val hora = sale.externalOrderAt.fmtClockTime()
                    if (hora.isNotEmpty()) {
                        Spacer(Modifier.size(6.dp))
                        Text(
                            hora,
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandColors.MutedSlate
                        )
                    }
                }
                Spacer(Modifier.size(2.dp))
                Text(
                    // Quién tiene el dinero es la mitad del valor de esta lista: sin el
                    // nombre dice que falta plata pero no a quién preguntarle.
                    if (sale.externalUserName.isNotEmpty()) "Atendió ${sale.externalUserName}"
                    else "Sin mesero registrado",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.MutedSlate
                )
                Spacer(Modifier.size(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    sale.effectivePaymentBreakdown.keys.forEach { method ->
                        BrandChip(
                            text = SalePaymentMethod.label(method),
                            tone = if (method == SalePaymentMethod.CASH) ChipTone.SUCCESS else ChipTone.INFO
                        )
                    }
                }
            }
            Text(
                "Q ${sale.total.fmt(2)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = BrandColors.Primary
            )
        }
    }
}
