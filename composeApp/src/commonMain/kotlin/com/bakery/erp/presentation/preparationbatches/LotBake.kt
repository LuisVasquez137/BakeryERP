package com.bakery.erp.presentation.preparationbatches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.BakeMold
import com.bakery.erp.model.BakeRow
import com.bakery.erp.model.BatterMix
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.ProductionLotLine
import com.bakery.erp.model.batterMixes
import com.bakery.erp.model.convertUnits
import com.bakery.erp.model.countUnitsPer
import com.bakery.erp.model.gramsToUnitOrNull
import com.bakery.erp.model.toGramsOrNull
import com.bakery.erp.presentation.production.ScaledRecipeRow
import com.bakery.erp.presentation.production.ScaledRecipeView
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipTone
import com.bakery.erp.util.fmtTrim
import com.bakery.erp.util.formatBakerWeight

/*
 * Bizcochos dentro del lote de producción. Los pasteles que van sobre bizcocho se agrupan
 * por la masa del bizcocho, como el pan por su receta. Al tocar el grupo, el repostero decide
 * en un diálogo cuántos hornear de cada molde, y del otro lado sale la masa escalada con el
 * mismo panel que el pan. Ver "Bizcochos" en `arquitectura/catalogo.md`.
 */

/** Prefijo de la clave de grupo de un pastel sobre bizcocho (no choca con un recipeId). */
const val BAKE_GROUP_PREFIX = "bake:"

/** Masa del bizcocho que lleva [line] (id, nombre), o null si no va sobre bizcocho. */
fun BakeSheetUiState.batterOfLine(line: ProductionLotLine): Pair<String, String>? {
    for (deco in line.decorationsSnapshot) {
        if (!deco.isCounted) continue
        val base = basesById[deco.preparationId] ?: continue
        if (base.batterPreparationId.isEmpty()) continue
        val name = battersById[base.batterPreparationId]?.name ?: base.batterPreparationName
        return base.batterPreparationId to name
    }
    return null
}

/** Moldes de una masa que el lote pide (o que están bajo la reserva). */
fun BakeSheetUiState.rowsOfBatter(batterId: String): List<BakeRow> =
    rows.filter { it.base.batterPreparationId == batterId }

/** La masa a batir de [batterId] con lo que el repostero puso. Null si no va a hornear nada. */
fun BakeSheetUiState.mixOfBatter(batterId: String): BatterMix? = mixes.firstOrNull { it.batterId == batterId }

/**
 * La masa escalada en el mismo formato que la receta del pan, para que el repostero la lea
 * igual: gramos grandes, el huevo además en piezas.
 */
fun bakeRecipeView(mix: BatterMix, ingredientsById: Map<String, Ingredient>): ScaledRecipeView {
    val grams = mix.scaledIngredients.map { (pi, qty) ->
        val ing = ingredientsById[pi.ingredientId]
        val uw = pi.unitWeightGrams.takeIf { it > 0.0 } ?: ing?.unitWeightGrams ?: 0.0
        Triple(pi, ing, toGramsOrNull(qty, pi.unit, uw) ?: 0.0)
    }
    val total = grams.sumOf { it.third }
    val rows = grams.map { (pi, ing, g) ->
        val countUnit = ing?.allowedUnits?.firstOrNull { countUnitsPer(it) != null }
        ScaledRecipeRow(
            ingredientId = pi.ingredientId,
            ingredientName = pi.ingredientName,
            percentage = if (total > 0.0) g / total * 100.0 else 0.0,
            totalGrams = g,
            gramsFromSponge = 0.0,
            finalGrams = g,
            isBase = ing?.category == "Harinas",
            countEquivalent = if (ing != null && countUnit != null) gramsToUnitOrNull(g, countUnit, ing.unitWeightGrams) else null,
            countUnit = countUnit ?: ""
        )
    }.sortedByDescending { it.finalGrams }
    return ScaledRecipeView(
        productName = mix.batterName,
        producedQuantity = mix.molds.sumOf { it.units },
        productUnit = "bizcochos",
        rawWeightGrams = 0.0,
        totalDoughGrams = mix.totalGrams,
        rows = rows
    )
}

/**
 * Lo que va arriba de la receta: qué moldes va a hornear, con "Cambiar" para volver al
 * diálogo y "Ya salieron del horno" para anotarlos.
 */
@Composable
fun ColumnScope.BakePlanSummary(
    rows: List<BakeRow>,
    mix: BatterMix?,
    canBake: Boolean,
    isSaving: Boolean,
    onEdit: () -> Unit,
    onFinish: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Vas a hornear",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.Primary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Cambiar", fontWeight = FontWeight.Bold)
                }
            }
            if (mix == null) {
                Text(
                    "Nada todavía. Toca \"Cambiar\" y pon cuántos vas a hacer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandColors.MutedSlate
                )
            }
            rows.forEach { row ->
                val units = mix?.molds?.firstOrNull { it.base.id == row.base.id }?.units ?: 0.0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        units.fmtTrim(2),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (units > 0.0) BrandColors.Primary else BrandColors.MutedSlate,
                        modifier = Modifier.width(64.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(row.base.name, style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold, color = BrandColors.Primary)
                        Text(
                            "Piden ${row.demand.units.fmtTrim(2)} · hay ${row.onHand.fmtTrim(2)} fríos",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandColors.MutedSlate
                        )
                    }
                    if (row.short > 0.0 && units + 1e-9 < row.short) {
                        BrandChip("Faltan ${(row.short - units).fmtTrim(2)}", ChipTone.ATTENTION)
                    }
                }
            }
            if (canBake && mix != null) {
                Button(
                    onClick = onFinish,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Ya salieron del horno", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/**
 * Diálogo donde el repostero decide cuántos hornear de cada molde de una masa. Arranca con
 * la sugerencia (lo que piden + lo que hay que tener − lo que hay frío) y debajo va diciendo
 * cuánta masa sale con lo que pone.
 */
@Composable
fun BakePlanDialog(
    batterName: String,
    rows: List<BakeRow>,
    current: Map<String, String>,
    state: BakeSheetUiState,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit
) {
    var values by remember {
        mutableStateOf(rows.associate { r -> r.base.id to (current[r.base.id] ?: r.suggested.takeIf { it > 0 }?.toString().orEmpty()) })
    }
    fun numberOf(id: String) = values[id]?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
    val preview = batterMixes(
        rows.mapNotNull { r -> numberOf(r.base.id).takeIf { it > 0.0 }?.let { BakeMold(r.base, it) } },
        state.battersById
    ).firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Cuántos vas a hornear?", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "$batterName. Sugerido = lo que piden + lo que hay que tener − lo que hay frío. " +
                        "Lo que sobre queda frío para mañana.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.MutedSlate
                )
                rows.forEach { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${row.base.name} · ${convertUnits(row.base.batterGramsPerUnit, "gr", "oz").fmtTrim(1)} oz",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandColors.Primary
                        )
                        Text(
                            (if (row.demand.units > 0.0) "Piden ${row.demand.units.fmtTrim(2)}: " +
                                row.demand.byProduct.entries.joinToString(", ") { "${it.value.fmtTrim(0)} ${it.key}" }
                            else "No lo piden: es para la reserva") +
                                "\nHay ${row.onHand.fmtTrim(2)} fríos" +
                                (if (row.base.stockMinimumUnits > 0.0) " · tener ${row.base.stockMinimumUnits.fmtTrim(1)}" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandColors.TextSlate
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalIconButton(onClick = {
                                val n = (numberOf(row.base.id) - 1).coerceAtLeast(0.0)
                                values = values + (row.base.id to if (n > 0.0) n.fmtTrim(2) else "")
                            }) { Icon(Icons.Default.Remove, contentDescription = "Uno menos") }
                            OutlinedTextField(
                                value = values[row.base.id].orEmpty(),
                                onValueChange = { t -> values = values + (row.base.id to t.filter { it.isDigit() || it == '.' || it == ',' }) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = BrandColors.Primary
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(96.dp)
                            )
                            FilledTonalIconButton(onClick = {
                                values = values + (row.base.id to (numberOf(row.base.id) + 1).fmtTrim(2))
                            }) { Icon(Icons.Default.Add, contentDescription = "Uno más") }
                            Spacer(Modifier.weight(1f))
                            if (row.suggested > 0) {
                                OutlinedButton(onClick = { values = values + (row.base.id to row.suggested.toString()) }) {
                                    Text("Sugerido ${row.suggested}")
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                }
                Surface(color = BrandColors.PrimaryContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Masa a batir", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                            color = BrandColors.Primary, modifier = Modifier.weight(1f))
                        Text(
                            preview?.let { formatBakerWeight(it.totalGrams) } ?: "—",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = BrandColors.Primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(values) }) { Text("Ver receta", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
