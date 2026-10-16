package com.bakery.erp.presentation.transfers

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Permission
import com.bakery.erp.model.StockTransfer
import com.bakery.erp.model.StockTransferItemKind
import com.bakery.erp.model.StockTransferLine
import com.bakery.erp.model.StockTransferStatus
import com.bakery.erp.model.User
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.quantityLabel
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipTone
import com.bakery.erp.util.fmtDateTime
import com.bakery.erp.util.fmtMoney
import com.bakery.erp.util.fmtTrim

/** Quién puede recibir: el que recibe despachos (o ajusta inventario) en la sucursal destino. */
internal fun User.canReceive(t: StockTransfer): Boolean =
    t.isInTransit && canAccessBranch(t.destinationBranchId) &&
        (hasPermission(Permission.RECEIVE_DISPATCH) || hasPermission(Permission.MANAGE_FINISHED_STOCK))

/** Quién puede cancelar: el que envía traslados desde la sucursal de origen. */
internal fun User.canCancel(t: StockTransfer): Boolean =
    t.isInTransit && canAccessBranch(t.originBranchId) && hasPermission(Permission.SEND_STOCK_TRANSFER)

internal fun statusTone(status: String) = when (status) {
    StockTransferStatus.IN_TRANSIT -> ChipTone.ATTENTION
    StockTransferStatus.RECEIVED -> ChipTone.SUCCESS
    else -> ChipTone.NEUTRAL
}

/**
 * Detalle de un traslado. Si va en camino a una sucursal del usuario, aquí se cuenta lo que llegó
 * (cada línea arranca con lo enviado: solo se corrige lo que no cuadra) y se confirma.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockTransferDetailScreen(
    transferId: String,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: StockTransferViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId.orEmpty()
    val snackbar = remember { SnackbarHostState() }
    val canSeeValue = user?.hasPermission(Permission.VIEW_DISPATCH_VALUE) == true

    LaunchedEffect(companyId, transferId) { if (companyId.isNotEmpty()) viewModel.loadDetail(companyId, transferId) }
    LaunchedEffect(state.notice) { state.notice?.let { viewModel.clearNotice(); snackbar.showSnackbar(it) } }
    LaunchedEffect(state.error) { state.error?.let { viewModel.clearError(); snackbar.showSnackbar(it) } }

    val t = state.selected?.takeIf { it.id == transferId }
    val receiving = t != null && user?.canReceive(t) == true
    // Lo contado, por línea. Arranca en lo enviado.
    var counted by remember(t?.id, t?.status) {
        mutableStateOf<List<String>>(t?.lines?.map { it.quantity.fmtTrim(3) } ?: emptyList())
    }
    var receiveNote by remember(t?.id) { mutableStateOf("") }
    var askCancel by remember { mutableStateOf(false) }
    var confirmReceive by remember { mutableStateOf(false) }
    val countedValues = counted.map { it.replace(',', '.').toDoubleOrNull() }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                BrandTopAppBar(
                    title = { Text(if (t != null) "Traslado ${t.folio}" else "Traslado", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BrandColors.Primary,
                        titleContentColor = BrandColors.Ivory,
                        navigationIconContentColor = BrandColors.Ivory
                    )
                )
            },
            bottomBar = {
                if (t != null && user != null && (receiving || user.canCancel(t))) {
                    Surface(color = BrandColors.Ivory, shadowElevation = 8.dp) {
                        AdaptiveContainer {
                            Row(
                                Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (user.canCancel(t)) OutlinedButton(
                                    onClick = { askCancel = true },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Cancelar envío", color = BrandColors.Danger) }
                                if (receiving) Button(
                                    onClick = { confirmReceive = true },
                                    enabled = countedValues.all { it != null && it >= 0.0 } && !state.isBusy,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary)
                                ) { Text("Confirmar recepción", fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = BrandColors.Ivory
        ) { padding ->
            AdaptiveContainer(modifier = Modifier.padding(padding)) {
                if (t == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (state.isDetailLoading) CircularProgressIndicator()
                        else Text("No se encontró el traslado", color = BrandColors.MutedSlate)
                    }
                    return@AdaptiveContainer
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { HeaderCard(t, canSeeValue) }
                    if (receiving) item {
                        Text(
                            "Cuenta lo que llegó. Cada línea trae lo que mandaron: cambia solo lo que no cuadre.",
                            style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    itemsIndexed(t.lines, key = { i, l -> "$i${l.itemId}" }) { i, line ->
                        LineRow(
                            line = line,
                            canSeeValue = canSeeValue,
                            countedText = if (receiving) counted.getOrElse(i) { "" } else null,
                            onCount = { v ->
                                counted = counted.toMutableList().also { it[i] = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(9) }
                            }
                        )
                    }
                    if (receiving) item {
                        OutlinedTextField(
                            value = receiveNote,
                            onValueChange = { receiveNote = it.take(300) },
                            label = { Text("Nota de recepción (opcional)") },
                            placeholder = { Text("Ej.: venían 2 aplastados") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item { Spacer(Modifier.padding(12.dp)) }
                }
            }
        }
        LoadingOverlay(visible = state.isBusy)
    }

    if (confirmReceive && t != null) {
        val values = countedValues.map { it ?: 0.0 }
        val short = t.lines.indices.filter { values[it] < t.lines[it].quantity - 0.0001 }
        AlertDialog(
            onDismissRequest = { confirmReceive = false },
            title = { Text("¿Confirmar recepción?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Entra al inventario de ${t.destinationBranchName} lo que contaste.")
                    if (short.isNotEmpty()) {
                        Text("Llegó menos de lo enviado:", fontWeight = FontWeight.SemiBold, color = BrandColors.Danger)
                        short.forEach { i ->
                            val l = t.lines[i]
                            Text("• ${l.itemName}: faltan ${(l.quantity - values[i]).fmtTrim()} ${l.unit}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Queda anotado y se avisa a ${t.originBranchName}.", style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    confirmReceive = false
                    user?.let { viewModel.receive(companyId, t, values, receiveNote, it) }
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { confirmReceive = false }) { Text("Revisar") } }
        )
    }

    if (askCancel && t != null) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askCancel = false },
            title = { Text("¿Cancelar el traslado ${t.folio}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Todo vuelve al inventario de ${t.originBranchName} y se avisa a ${t.destinationBranchName}.")
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it.take(200) },
                        label = { Text("Por qué") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { askCancel = false; user?.let { viewModel.cancel(companyId, t, reason, it) } },
                    enabled = reason.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Danger)
                ) { Text("Cancelar envío") }
            },
            dismissButton = { TextButton(onClick = { askCancel = false }) { Text("Volver") } }
        )
    }
}

@Composable
private fun HeaderCard(t: StockTransfer, canSeeValue: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BrandColors.Sand),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.originBranchName, fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = BrandColors.Primary, modifier = Modifier.padding(horizontal = 6.dp))
                Text(t.destinationBranchName, fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate, modifier = Modifier.weight(1f))
                BrandChip(StockTransferStatus.label(t.status), statusTone(t.status))
            }
            Detail("Envió ${t.sentByName} · ${t.sentAt.fmtDateTime()}")
            if (t.note.isNotBlank()) Detail("Nota: ${t.note}")
            if (t.isReceived) {
                Detail("Recibió ${t.receivedByName} · ${t.receivedAt.fmtDateTime()}")
                if (t.receiveNote.isNotBlank()) Detail("Nota: ${t.receiveNote}")
            }
            if (t.status == StockTransferStatus.CANCELLED) {
                Detail("Canceló ${t.cancelledByName} · ${t.cancelledAt.fmtDateTime()}")
                if (t.cancelReason.isNotBlank()) Detail("Motivo: ${t.cancelReason}")
            }
            if (canSeeValue && t.sentValue > 0) {
                Detail("Valor a costo: Q ${t.sentValue.fmtMoney(2)}" +
                    if (t.shortageValue > 0) " · faltante Q ${t.shortageValue.fmtMoney(2)}" else "")
            }
        }
    }
}

@Composable
private fun Detail(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
}

@Composable
private fun LineRow(line: StockTransferLine, canSeeValue: Boolean, countedText: String?, onCount: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(line.itemName, fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate)
                val sent = line.quantityLabel()
                val base = "${line.quantity.fmtTrim()} ${line.unit}"
                Text(
                    "Enviado: $sent" + if (line.displayQuantity.isNotBlank() && sent != base) " ($base)" else "",
                    style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate
                )
                if (line.kind == StockTransferItemKind.INGREDIENT) Text("Insumo", style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate)
                line.receivedQuantity?.let { r ->
                    Text("Recibido: ${r.fmtTrim()} ${line.unit}", style = MaterialTheme.typography.bodySmall, color = BrandColors.TextSlate)
                    if (line.shortage > 0.0001) BrandChip("Faltó ${line.shortage.fmtTrim()} ${line.unit}", ChipTone.DANGER)
                    if (line.surplus > 0.0001) BrandChip("Llegó ${line.surplus.fmtTrim()} de más", ChipTone.INFO)
                }
                if (canSeeValue && line.unitCost > 0) {
                    Text("Q ${line.unitCost.fmtMoney(2)} c/u a costo", style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate)
                }
                if (line.lots.any { it.expiryDate > 0 }) {
                    Text(
                        "Lotes: " + line.lots.filter { it.expiryDate > 0 }.joinToString { "${it.quantity.fmtTrim()} vence ${it.expiryDate.fmtDateTime().substringBefore(' ')}" },
                        style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate
                    )
                }
            }
            if (countedText != null) {
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = countedText,
                    onValueChange = onCount,
                    label = { Text("Llegó (${line.unit})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.widthIn(max = 130.dp)
                )
            }
        }
    }
}
