package com.bakery.erp.presentation.production

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.ProductionExtraReason
import com.bakery.erp.model.ProductionLotLine
import com.bakery.erp.model.ProductionWasteReason
import com.bakery.erp.ui.theme.BrandColors

/** Las dos cosas que le pueden pasar a un lote a media producción. */
internal enum class AdjustmentKind { WASTE, EXTRA }

/**
 * Resultado de la hoja: cuánto por línea, con su motivo. El llamador lo manda a
 * `ProductionViewModel.registerProductionAdjustment`.
 */
internal data class ProductionAdjustment(
    val kind: AdjustmentKind,
    val quantityByLineId: Map<String, Double>,
    val reason: String,
    val notes: String
)

/**
 * "¿Qué pasó?" — la hoja con la que el panadero declara pan perdido o pan que hay que
 * hacer de más, a mitad del lote. Ver `model/ProductionAdjustment.kt` para el porqué.
 *
 * **Pregunta la clase de cosa ANTES de mostrar un solo campo**, igual que
 * `CaptureKindStep` en la compra rápida, porque la respuesta cambia todo lo demás: lo
 * perdido no toca los pedidos y pide un motivo de merma; lo que se hace de más sí suma
 * pan y pide un motivo de demanda. Un formulario único con las dos cosas arriba obliga
 * a leer las dos etiquetas para entender cuál aplica, y a las 4am eso se resuelve
 * llenando la equivocada.
 *
 * **Lo que la hace útil no es el registro, es la receta**: apenas se teclea una
 * cantidad aparece abajo la receta escalada exactamente a esas unidades. Ése es el
 * número que el panadero no podía calcular —"¿cuánta harina para reponer 40 panes de
 * capa?"— y sin él la reposición se hacía a ojo o no se hacía.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ProductionAdjustmentSheet(
    masaTitle: String,
    lines: List<ProductionLotLine>,
    sheetState: SheetState,
    /** Receta escalada a las cantidades tecleadas. null mientras no haya ninguna. */
    scaledRecipeFor: (Map<String, Double>) -> ScaledRecipeView?,
    /**
     * Recetas de los RELLENOS de masa cruda de esos productos, escaladas a lo mismo.
     * El Pan de Capa son dos masas —capa por fuera, bolita de suave adentro— y sin
     * esto la hoja daba media respuesta: la otra media masa se hacía a ojo.
     */
    fillingRecipesFor: (Map<String, Double>) -> List<ScaledRecipeView> = { emptyList() },
    /** true si la masa ya tiene esponja confirmada: la tanda extra necesita la suya. */
    hasConfirmedSponge: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ProductionAdjustment) -> Unit
) {
    var kind by remember { mutableStateOf<AdjustmentKind?>(null) }
    var inputs by remember { mutableStateOf(mapOf<String, String>()) }
    var reason by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val quantities = remember(inputs) {
        inputs.mapNotNull { (id, txt) ->
            txt.toDoubleOrNull()?.takeIf { it > 0.0 }?.let { id to it }
        }.toMap()
    }
    val preview = remember(quantities) { scaledRecipeFor(quantities) }
    val fillingPreviews = remember(quantities) { fillingRecipesFor(quantities) }
    val canSave = kind != null && quantities.isNotEmpty() && reason.isNotBlank() && !isSaving

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BrandColors.Ivory
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(
                masaTitle,
                style = MaterialTheme.typography.labelLarge,
                color = BrandColors.MutedSlate
            )
            Text(
                if (kind == null) "¿Qué pasó?" else kindTitle(kind!!),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = BrandColors.Primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (kind == null) {
                // ── Paso 1: la clase de cosa ─────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    KindCard(
                        icon = Icons.Default.LocalFireDepartment,
                        title = "Se perdió pan",
                        subtitle = "Se quemó, se cayó o salió mal. Las sucursales siguen " +
                            "recibiendo lo suyo; se descuenta la masa que se gastó.",
                        onClick = { kind = AdjustmentKind.WASTE; reason = ProductionWasteReason.BURNED }
                    )
                    KindCard(
                        icon = Icons.Default.AddCircleOutline,
                        title = "Hay que hacer más",
                        subtitle = "Un encargo que llegó, o pidieron extra. Ese pan queda " +
                            "en el inventario de la planta.",
                        onClick = { kind = AdjustmentKind.EXTRA; reason = ProductionExtraReason.CUSTOMER_ORDER }
                    )
                }
                Spacer(Modifier.heightIn(min = 8.dp))
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                return@Column
            }

            // ── Paso 2: cuánto, de qué producto ──────────────────────────────
            Text(
                kindHint(kind!!),
                style = MaterialTheme.typography.bodySmall,
                color = BrandColors.MutedSlate,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(lines, key = { it.id }) { line ->
                    AdjustmentRow(
                        line = line,
                        kind = kind!!,
                        value = inputs[line.id].orEmpty(),
                        onValueChange = { txt ->
                            inputs = inputs.toMutableMap().also { it[line.id] = txt.filter { c -> c.isDigit() } }
                        }
                    )
                }

                item(key = "reason") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        HorizontalDivider()
                        Text(
                            if (kind == AdjustmentKind.WASTE) "¿Por qué se perdió?" else "¿Por qué se hace de más?",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandColors.Primary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val options = if (kind == AdjustmentKind.WASTE) ProductionWasteReason.ALL
                                          else ProductionExtraReason.ALL
                            options.forEach { option ->
                                FilterChip(
                                    selected = reason == option,
                                    onClick = { reason = option },
                                    label = {
                                        Text(
                                            if (kind == AdjustmentKind.WASTE) ProductionWasteReason.label(option)
                                            else ProductionExtraReason.label(option)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandColors.PrimaryContainer,
                                        selectedLabelColor = BrandColors.Primary
                                    )
                                )
                            }
                        }
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Detalle (opcional)") },
                            placeholder = {
                                Text(
                                    if (kind == AdjustmentKind.WASTE) "Ej. se pasó la segunda bandeja"
                                    else "Ej. Doña María, pasa a las 11"
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ── La receta de la tanda: el entregable real de esta hoja ───
                if (preview != null) {
                    item(key = "recipe") {
                        Column(modifier = Modifier.padding(top = 14.dp)) {
                            Text(
                                if (kind == AdjustmentKind.WASTE) "Receta para reponer lo perdido"
                                else "Receta para lo que se hace de más",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = BrandColors.Primary
                            )
                            if (hasConfirmedSponge) {
                                // La esponja ya declarada se consumió en la amasada
                                // anterior: esta receta viene SIN su aporte, que es lo
                                // correcto, pero el panadero tiene que saber que si
                                // quiere esponja en esta tanda la declara aparte.
                                SpongeWarning()
                            }
                            if (fillingPreviews.isNotEmpty()) {
                                // Con dos masas hay que decir cuál es cuál antes de la
                                // primera tabla: si no, el panadero lee la de arriba
                                // como "la receta" y no busca la segunda.
                                Text(
                                    "Este pan lleva dos masas — hay que amasar las dos.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrandColors.AccentDeep,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            ScaledRecipePanel(
                                view = preview,
                                // La LazyColumn de la hoja ya scrollea: con el scroll
                                // propio puesto, Compose mide con altura infinita y la
                                // pantalla se cae al dibujar.
                                scrollable = false,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .border(1.dp, BrandColors.Outline, RoundedCornerShape(12.dp))
                            )
                        }
                    }

                    items(fillingPreviews, key = { "filling_${it.productName}" }) { filling ->
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            Text(
                                "Masa del relleno",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = BrandColors.Primary
                            )
                            ScaledRecipePanel(
                                view = filling,
                                scrollable = false,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .border(1.dp, BrandColors.Outline, RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { kind = null; inputs = emptyMap(); reason = "" }) {
                    Text("Atrás")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onConfirm(
                            ProductionAdjustment(
                                kind = kind!!,
                                quantityByLineId = quantities,
                                reason = reason,
                                notes = notes
                            )
                        )
                    },
                    enabled = canSave,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary)
                ) { Text(if (kind == AdjustmentKind.WASTE) "Registrar lo perdido" else "Agregar a la lista") }
            }
        }
    }
}

private fun kindTitle(kind: AdjustmentKind) = when (kind) {
    AdjustmentKind.WASTE -> "Se perdió pan"
    AdjustmentKind.EXTRA -> "Hacer más"
}

private fun kindHint(kind: AdjustmentKind) = when (kind) {
    // Este texto es la guarda contra el doble conteo, que es el modo de fallo obvio:
    // el panadero que resta lo quemado del conteo final Y lo declara acá descontaría
    // la masa dos veces.
    AdjustmentKind.WASTE ->
        "Anotá cuántas se perdieron. Al final, en el conteo, contá solo el pan BUENO: " +
            "lo perdido ya queda contado aquí."
    AdjustmentKind.EXTRA ->
        "Anotá cuántas más hay que hacer. Se suman a la lista y a la receta de esta masa; " +
            "ese pan queda en el inventario de la planta."
}

@Composable
private fun KindCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BrandColors.Outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = BrandColors.Primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.Primary
                )
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
            }
        }
    }
}

@Composable
private fun AdjustmentRow(
    line: ProductionLotLine,
    kind: AdjustmentKind,
    value: String,
    onValueChange: (String) -> Unit
) {
    val already = if (kind == AdjustmentKind.WASTE) line.wasteQuantity else line.extraQuantity
    Surface(color = Color.White, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(line.productName, fontWeight = FontWeight.SemiBold, color = BrandColors.Primary)
                Text(
                    buildString {
                        append("Pedido: ").append(line.plannedQuantity.toInt())
                        // Lo ya declarado se muestra porque las cantidades SE SUMAN: sin
                        // esto, quien declaró una bandeja a las 5am no puede saber si la
                        // de las 7am hay que sumarla o si ya está incluida.
                        if (already > 0.0) {
                            append("  ·  ya anotado: ").append(already.toInt())
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (already > 0.0) BrandColors.AccentDeep else Color.Gray
                )
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(if (kind == AdjustmentKind.WASTE) "Perdidas" else "De más") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.width(120.dp)
            )
        }
    }
}

@Composable
private fun SpongeWarning() {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BrandColors.AccentSoft,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = BrandColors.AccentIcon,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Esta masa lleva esponja y esta receta viene sin ella — la esponja que ya " +
                    "declaraste se usó en la amasada anterior. Si esta tanda también lleva, " +
                    "declarala aparte en el panel de la masa.",
                style = MaterialTheme.typography.labelSmall,
                color = BrandColors.Primary
            )
        }
    }
}
