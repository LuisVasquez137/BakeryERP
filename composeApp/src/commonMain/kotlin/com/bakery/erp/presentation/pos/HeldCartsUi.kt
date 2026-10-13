package com.bakery.erp.presentation.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.HeldCart
import com.bakery.erp.model.MAX_HELD_CARTS
import com.bakery.erp.model.itemsSummary
import com.bakery.erp.model.minutesWaiting
import com.bakery.erp.model.total
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmtMoney

/**
 * Fila de cuentas en espera arriba del catálogo. Un toque retoma la cuenta (si hay otro carrito a
 * medias, ése pasa a espera); la ✕ la descarta, con confirmación porque queda en la bitácora.
 * No pinta nada si no hay cuentas en espera.
 */
@Composable
internal fun HeldCartsBar(
    held: List<HeldCart>,
    busy: Boolean,
    onResume: (HeldCart) -> Unit,
    onDiscard: (HeldCart) -> Unit,
    modifier: Modifier = Modifier
) {
    if (held.isEmpty()) return
    // Los minutos de espera avanzan solos: cada 30 s basta para una ficha que dice "3 min".
    var now by remember { mutableStateOf(kotlin.time.Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(held.size) {
        while (true) {
            now = kotlin.time.Clock.System.now().toEpochMilliseconds()
            kotlinx.coroutines.delay(30_000)
        }
    }
    Surface(color = BrandColors.AccentSoft, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.PauseCircle, null, tint = BrandColors.Primary, modifier = Modifier.size(20.dp))
            Text("En espera", fontWeight = FontWeight.SemiBold, color = BrandColors.Primary,
                style = MaterialTheme.typography.labelLarge)
            held.forEach { h ->
                Surface(
                    onClick = { if (!busy) onResume(h) },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, BrandColors.Primary.copy(alpha = 0.6f))
                ) {
                    Row(Modifier.padding(start = 12.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(h.label, fontWeight = FontWeight.SemiBold, color = BrandColors.Primary,
                                style = MaterialTheme.typography.labelLarge, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
                            Text("Q ${h.total.fmtMoney(2)} · ${h.minutesWaiting(now)} min",
                                style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate)
                        }
                        IconButton(onClick = { if (!busy) onDiscard(h) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Close, "Descartar ${h.label}", tint = BrandColors.MutedSlate,
                                modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Nombre opcional de la cuenta: con la fila esperando, vacío también sirve ("Cuenta 2"). */
@Composable
internal fun HoldCartDialog(
    suggested: String,
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Poner en espera") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("El carrito se guarda para atender al siguiente cliente. Lo retomás tocando su ficha.",
                    style = MaterialTheme.typography.bodySmall, color = BrandColors.TextSlate)
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 30) name = it },
                    label = { Text("¿Quién es? (opcional)") },
                    placeholder = { Text(suggested) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(name) }),
                    modifier = Modifier.fillMaxWidth()
                )
                if (count > 0) {
                    Text("Ya hay $count de $MAX_HELD_CARTS en espera.", style = MaterialTheme.typography.labelSmall,
                        color = BrandColors.MutedSlate)
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(name) }) { Text("Poner en espera") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Descartar queda en la bitácora de cajas: se dice antes de hacerlo. */
@Composable
internal fun DiscardHeldCartDialog(held: HeldCart, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Descartar «${held.label}»?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Q ${held.total.fmtMoney(2)} · ${held.itemsSummary()}", style = MaterialTheme.typography.bodyMedium)
                Text("No se cobra nada. Queda registrado en la bitácora de cajas quién la descartó y por cuánto.",
                    style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Descartar", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Volver") } }
    )
}

/** Con cuentas en espera no se cierra el turno: primero se cobran o se descartan. */
@Composable
internal fun CloseBlockedByHeldDialog(held: List<HeldCart>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hay ${held.size} cuenta(s) en espera") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                held.forEach { h ->
                    Text("• ${h.label} · Q ${h.total.fmtMoney(2)}", style = MaterialTheme.typography.bodyMedium)
                }
                Text("Cobralas o descartalas antes de cerrar la caja.",
                    style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Entendido") } }
    )
}
