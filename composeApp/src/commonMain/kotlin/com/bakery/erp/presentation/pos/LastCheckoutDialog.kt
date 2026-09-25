package com.bakery.erp.presentation.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CheckoutRecord
import com.bakery.erp.model.SalePaymentMethod
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmtClockTime
import com.bakery.erp.util.fmtMoney

/**
 * "Último cobro": cuánto se cobró, con qué billetes pagó el cliente y cuánto vuelto se dio.
 *
 * Existe para el reclamo del mostrador ("no me dio bien el vuelto") y para cuando la cajera no
 * se acuerda de qué cobró. Abre en el más reciente; las flechas van a los anteriores, porque
 * el reclamo a veces llega después de que ya se atendió a otro cliente.
 */
@Composable
fun LastCheckoutDialog(
    checkouts: List<CheckoutRecord>,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    var index by remember { mutableIntStateOf(0) }
    val current = checkouts.getOrNull(index)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (index == 0) "Último cobro" else "Cobro anterior (${index + 1})",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (checkouts.size > 1) {
                    IconButton(onClick = { index++ }, enabled = index < checkouts.lastIndex) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Cobro anterior")
                    }
                    IconButton(onClick = { index-- }, enabled = index > 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Cobro más reciente")
                    }
                }
            }
        },
        text = {
            when {
                isLoading && current == null -> Box(
                    Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                current == null -> Text("Todavía no se ha cobrado nada en esta caja.")
                else -> CheckoutDetail(current)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
private fun CheckoutDetail(c: CheckoutRecord) {
    val isCash = c.method == SalePaymentMethod.CASH
    Column(
        Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            listOfNotNull(
                c.at.fmtClockTime().ifEmpty { null },
                SalePaymentMethod.label(c.method),
                c.cashierName.ifEmpty { null }
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = BrandColors.MutedSlate
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = BrandColors.PrimaryContainer),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AmountRow("Cobrado", c.total, emphasize = false)
                if (isCash) {
                    AmountRow("Le dieron", c.tendered, emphasize = false)
                    HorizontalDivider(Modifier.padding(vertical = 2.dp))
                    AmountRow("Vuelto", c.change, emphasize = true)
                }
            }
        }

        if (isCash) {
            Text("Billetes que dio el cliente", fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge)
            if (c.bills.isEmpty()) {
                Text(
                    "No se marcaron: el monto se tecleó a mano. Para que quede el detalle, " +
                        "tocá los billetes al cobrar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.MutedSlate
                )
            } else {
                c.bills.forEach { (value, count) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("$count × Q${value.fmtMoney(0)}", style = MaterialTheme.typography.bodyMedium)
                        Text("Q ${(value * count).fmtMoney(2)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (c.items.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text("Qué se cobró", fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge)
            c.items.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun AmountRow(label: String, amount: Double, emphasize: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal)
        Text(
            "Q ${amount.fmtMoney(2)}",
            fontWeight = FontWeight.Bold,
            style = if (emphasize) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = BrandColors.Primary
        )
    }
}
