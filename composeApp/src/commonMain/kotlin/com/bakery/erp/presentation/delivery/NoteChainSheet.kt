package com.bakery.erp.presentation.delivery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.ChainLine
import com.bakery.erp.model.ChainStage
import com.bakery.erp.model.DiscrepancyType
import com.bakery.erp.model.ProductionWasteReason
import com.bakery.erp.model.label
import com.bakery.erp.model.BasketCheck
import com.bakery.erp.model.basketCheck
import com.bakery.erp.model.transitStageLabel
import com.bakery.erp.ui.theme.BrandColors

/**
 * La cadena de una entrega, para el dueño: por cada pan, pedido → empacado → contado, con
 * el eslabón donde empezó a faltar marcado y quién estaba en cada paso. Contesta "si faltó
 * francés en Democracia, ¿dónde empezó a faltar?" sin abrir tres pantallas.
 *
 * Solo la ven el dueño y quien maneja la planta: que la encargada de una sucursal vea
 * quién perdió su pan genera roces que no ayudan a encontrarlo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteChainSheet(
    chain: NoteChain,
    onDismiss: () -> Unit,
    onOpenDetail: () -> Unit
) {
    val note = chain.note
    var showAll by remember(note.id) { mutableStateOf(false) }
    val baskets = basketCheck(note.basketsSent, note.basketsReceived)
    val transitLabel = transitStageLabel(baskets)
    val withDiff = chain.lines.filter { it.hasDifference }
    val shown = if (showAll || withDiff.isEmpty()) chain.lines else withDiff

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BrandColors.Ivory) {
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Cadena de la entrega", style = MaterialTheme.typography.labelMedium, color = BrandColors.MutedSlate)
                Text(note.destinationBranchName, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = BrandColors.PrimaryDark)
                Text("${note.shiftName.ifBlank { "Sin turno" }} · ${formatDay(note.productionDate)}",
                    style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
            }
            item { People(chain) }
            if (note.basketsSent > 0) item { Baskets(note.basketsSent, note.basketsReceived, baskets) }
            item { Summary(chain, transitLabel) }
            items(shown, key = { it.productId }) { ChainRow(it, transitLabel) }
            if (withDiff.isNotEmpty() && withDiff.size < chain.lines.size) {
                item {
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(if (showAll) "Ver solo lo que no cuadró" else "Ver también los ${chain.lines.size - withDiff.size} que cuadraron")
                    }
                }
            }
            item {
                OutlinedButton(onClick = onOpenDetail, modifier = Modifier.fillMaxWidth()) {
                    Text("Ver la nota completa")
                }
            }
        }
    }
}

@Composable
private fun People(chain: NoteChain) {
    val n = chain.note
    val rows = listOf(
        "Empacó" to n.packedByUserName,
        "Llevó" to listOf(chain.driverName, chain.vehicleInfo.substringBefore(" · ")).filter { it.isNotBlank() }.joinToString(" · "),
        "Firmó la llegada" to n.arrivedByUserName,
        "Contó" to n.receivedByUserName
    )
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEach { (label, who) ->
                Row {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = BrandColors.MutedSlate,
                        modifier = Modifier.width(110.dp))
                    Text(who.ifBlank { "—" }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
                        color = BrandColors.TextSlate)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(chain: NoteChain, transitLabel: String) {
    val s = chain.summary
    val parts = listOf(
        ChainStage.PRODUCTION to s.production,
        ChainStage.PACKING to s.packing,
        ChainStage.TRANSIT_OR_BRANCH to s.transitOrBranch
    ).filter { it.second != 0.0 }
    if (parts.isEmpty()) {
        Surface(color = BrandColors.SuccessSoft, shape = RoundedCornerShape(12.dp)) {
            Text(
                if (s.transferred != 0.0 || chain.lines.any { it.isTransfer() }) "Todo cuadró. Hubo traspasos entre sucursales en el empaque."
                else "Todo cuadró: lo que se pidió es lo que se contó.",
                color = BrandColors.Success, fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
        }
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEach { (stage, units) ->
            val (bg, fg) = stageColors(stage)
            Surface(color = bg, shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(if (stage == ChainStage.TRANSIT_OR_BRANCH) transitLabel else stage.label(),
                        style = MaterialTheme.typography.labelSmall, color = fg)
                    Text(signed(units), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = fg)
                }
            }
        }
    }
}

@Composable
private fun ChainRow(line: ChainLine, transitLabel: String) {
    val breakAt = line.firstBreak()
    Surface(
        color = Color.White, shape = RoundedCornerShape(12.dp),
        border = if (breakAt != null) BorderStroke(1.5.dp, stageColors(breakAt).first) else null
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(line.productName, fontWeight = FontWeight.SemiBold, color = BrandColors.PrimaryDark, modifier = Modifier.weight(1f))
                breakAt?.let {
                    val (bg, fg) = stageColors(it)
                    Surface(color = bg, shape = RoundedCornerShape(50)) {
                        val where = if (it == ChainStage.TRANSIT_OR_BRANCH) transitLabel else it.label()
                        Text("Empezó en: ${where.lowercase()}", style = MaterialTheme.typography.labelSmall,
                            color = fg, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Step("Pedido", line.requested, highlight = false, modifier = Modifier.weight(1f))
                Arrow()
                Step("Empacado", line.packed, highlight = breakAt == ChainStage.PACKING || breakAt == ChainStage.PRODUCTION,
                    diff = line.packingDiff, modifier = Modifier.weight(1f))
                Arrow()
                Step("Contado", line.received, highlight = breakAt == ChainStage.TRANSIT_OR_BRANCH,
                    diff = line.receptionDiff, modifier = Modifier.weight(1f))
            }
            // Contexto del horno: no reparte el faltante, pero explica un empaque corto.
            val planned = line.plannedInShift
            val produced = line.producedInShift
            if (planned != null && produced != null && produced < planned) {
                Note("Horno: el turno sacó ${produced.toInt()} de ${planned.toInt()}" +
                    if (line.wasteInShift > 0.0) " · ${line.wasteInShift.toInt()} ${ProductionWasteReason.label(line.wasteReasonInShift).lowercase()}" else "")
            }
            line.packedReason?.takeIf { it.isNotBlank() && line.packingDiff != 0.0 }?.let { Note("Empaque: $it") }
            if ((line.receptionDiff ?: 0.0) != 0.0) {
                val type = line.discrepancyType?.let { DiscrepancyType.label(it) }
                Note("Recepción: " + listOfNotNull(type, line.receivedReason?.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { "sin motivo" })
            }
        }
    }
}

/** Lo que dicen las canastas del tramo camino/recepción (ver `basketCheck`). */
@Composable
private fun Baskets(sent: Int, received: Int?, check: BasketCheck) {
    val (bg, fg, text) = when (check) {
        BasketCheck.MISSING_BASKETS -> Triple(BrandColors.DangerSoft, BrandColors.Danger,
            "Salieron $sent canastas y llegaron $received: faltó ${sent - (received ?: 0)} en el camión.")
        BasketCheck.ALL_ARRIVED -> Triple(BrandColors.TealSoft, BrandColors.Teal,
            "Salieron $sent canastas y llegaron todas: lo que falte se perdió dentro de la canasta (empaque o conteo), no en el camino.")
        BasketCheck.UNKNOWN -> Triple(BrandColors.Sand, BrandColors.MutedSlate,
            "Salieron $sent canastas. Todavía no se firmó cuántas llegaron.")
    }
    Surface(color = bg, shape = RoundedCornerShape(12.dp)) {
        Text(text, color = fg, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(12.dp))
    }
}

@Composable
private fun Step(label: String, value: Double?, highlight: Boolean, diff: Double? = null, modifier: Modifier = Modifier) {
    val color = if (highlight) BrandColors.Danger else BrandColors.TextSlate
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate)
        Text(value?.toInt()?.toString() ?: "sin contar", fontWeight = FontWeight.Bold, color = color,
            style = if (value == null) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium)
        if (diff != null && diff != 0.0) {
            Text(signed(diff), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                color = if (diff < 0) BrandColors.Danger else BrandColors.Success, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Arrow() {
    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = BrandColors.Outline, modifier = Modifier.size(16.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = BrandColors.MutedSlate)
}

/** Producción en naranja oscuro, empaque en azul, camino en teal: el mismo color del eslabón en el tablero. */
private fun stageColors(stage: ChainStage): Pair<Color, Color> = when (stage) {
    ChainStage.PRODUCTION -> BrandColors.AccentDeep to Color.White
    ChainStage.PACKING -> BrandColors.Primary to Color.White
    ChainStage.TRANSIT_OR_BRANCH -> BrandColors.Teal to Color.White
}

private fun signed(v: Double): String = if (v > 0) "+${v.toInt()}" else "${v.toInt()}"
