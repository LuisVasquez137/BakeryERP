package com.bakery.erp.presentation.production

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bakery.erp.model.ProductionOrderLine
import com.bakery.erp.model.newSpecialProductId
import com.bakery.erp.ui.ImagePickerButton
import com.bakery.erp.ui.ProductImage
import com.bakery.erp.util.formatBakerWeight
import com.bakery.erp.util.fmtTrim
import com.bakery.erp.util.gramsToOz
import com.bakery.erp.util.ozToGrams
import com.bakery.erp.ui.theme.BrandColors

/**
 * Alta/edición de un **pedido especial**: la misma masa que ya se hace, pero en
 * otra figura o tamaño ("un pirujo del doble").
 *
 * Los dos campos que parecen burocracia son los que hacen que el especial no se
 * quede en un papel pegado a la pantalla del panadero:
 *  - **la masa** lo mete en el grupo de su receta, así la amasada de la mañana
 *    ya lo trae contado en vez de aparecer como un pan huérfano;
 *  - **el peso en crudo** es lo único que permite escalar la receta y descontar
 *    los insumos al cerrar el lote. Sin él, el especial se hornea igual pero
 *    sale gratis del inventario.
 *
 * Todo lo demás (descripción, cantidad, precio, foto) es lo que la encargada
 * diría por teléfono, escrito una sola vez.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpecialOrderDialog(
    /** Masas disponibles (las recetas que hoy producen algo). */
    masas: List<RecipeRef>,
    /** Línea a editar, o null para una nueva. */
    existing: ProductionOrderLine?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    /** Devuelve la línea armada y, si eligió una foto nueva, sus bytes. */
    onConfirm: (line: ProductionOrderLine, photoBytes: ByteArray?) -> Unit
) {
    // Todo el estado va llaveado por la línea que se está editando: el diálogo se
    // abre y se cierra sobre el mismo lugar del árbol, y sin la llave el segundo
    // especial que se edita arrancaría con los datos del primero.
    val formKey = existing?.productId.orEmpty()
    var recipeId by rememberSaveable(formKey) { mutableStateOf(existing?.specialRecipeId ?: "") }
    var description by rememberSaveable(formKey) { mutableStateOf(existing?.productName ?: "") }
    // Se teclea en ONZAS —es la unidad en la que se piensa el pan acá— pero lo que
    // se guarda es [ProductionOrderLine.specialRawWeightGrams] en gramos: la receta
    // escalada y el descuento de insumos trabajan en gramos, y guardar onzas
    // obligaría a convertir en cada uno de esos pasos.
    var ozText by rememberSaveable(formKey) {
        mutableStateOf(existing?.specialRawWeightGrams?.takeIf { it > 0.0 }?.gramsToOz()?.fmtTrim(2) ?: "")
    }
    var qtyText by rememberSaveable(formKey) {
        mutableStateOf(existing?.requestedQuantity?.takeIf { it > 0.0 }?.toInt()?.toString() ?: "")
    }
    var priceText by rememberSaveable(formKey) {
        mutableStateOf(existing?.specialUnitPrice?.takeIf { it > 0.0 }?.fmtTrim(2) ?: "")
    }
    var photoBytes by remember(formKey) { mutableStateOf<ByteArray?>(null) }
    var masaExpanded by remember { mutableStateOf(false) }

    // Al editar, la masa que ya tiene el encargo se ofrece aunque hoy no esté entre
    // las de la planta (le cambiaron los productos asignados desde que se anotó):
    // si no, el campo aparecería vacío y el diálogo no dejaría guardar hasta elegir
    // otra masa, perdiendo en silencio la que el panadero ya tenía.
    val masaOptions = remember(masas, existing?.specialRecipeId) {
        val current = existing?.specialRecipeId?.takeIf { it.isNotEmpty() }?.let { id ->
            RecipeRef(id, existing.specialRecipeName.ifEmpty { "Masa del encargo" })
        }
        if (current != null && masas.none { it.id == current.id }) {
            (masas + current).sortedBy { it.name }
        } else masas
    }
    val selectedMasa = masaOptions.find { it.id == recipeId }
    val ounces = ozText.toDoubleOrNull() ?: 0.0
    val grams = ounces.ozToGrams()
    val quantity = qtyText.toDoubleOrNull() ?: 0.0
    val canSave = selectedMasa != null && description.isNotBlank() &&
        grams > 0.0 && quantity > 0.0 && !isSaving
    val savedUrl = existing?.specialPhotoUrl.orEmpty()

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BrandColors.Ivory,
            modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = BrandColors.AccentDeep)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (existing == null) "Pedido especial" else "Editar pedido especial",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BrandColors.Primary
                    )
                }
                Text(
                    "Un pan que no está en la lista: la misma masa de siempre, en otra " +
                        "figura o tamaño.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6E6E6E),
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Masa. Primero porque es la decisión que ordena todo lo demás:
                    // el especial se hornea dentro de esa amasada, no aparte.
                    ExposedDropdownMenuBox(
                        expanded = masaExpanded,
                        onExpandedChange = { masaExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedMasa?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("¿De qué masa sale? *") },
                            placeholder = { Text("Seleccionar masa") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(masaExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = masaExpanded,
                            onDismissRequest = { masaExpanded = false }
                        ) {
                            masaOptions.forEach { masa ->
                                DropdownMenuItem(
                                    text = { Text(masa.name) },
                                    onClick = { recipeId = masa.id; masaExpanded = false }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("¿Cómo lo quieren? *") },
                        placeholder = { Text("Pirujo del doble de tamaño") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = ozText,
                                onValueChange = { text ->
                                    ozText = text.filter { it.isDigit() || it == '.' }
                                },
                                label = { Text("Peso c/u (oz) *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            // Equivalencia en vivo hacia gramos: es lo que va a quedar
                            // guardado y lo que el panadero ve en la receta escalada.
                            Text(
                                if (grams > 0.0) "≈ ${grams.fmtTrim(0)} g en crudo"
                                else "Peso de UNA pieza antes de hornear, en onzas",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (grams > 0.0) BrandColors.Primary else Color(0xFF9E9E9E),
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )
                        }
                        OutlinedTextField(
                            value = qtyText,
                            onValueChange = { text -> qtyText = text.filter { it.isDigit() } },
                            label = { Text("Cantidad *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { text ->
                            priceText = text.filter { it.isDigit() || it == '.' }
                        },
                        label = { Text("Precio unitario (opcional)") },
                        supportingText = {
                            Text(
                                "Si lo dejas vacío, la nota de despacho sale sin valor para " +
                                    "este pan — no bloquea el reparto.",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        "Foto de referencia (opcional)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandColors.Primary
                    )
                    // Foto ya guardada y sin reemplazo elegido: se muestra la real,
                    // no el marcador de "imagen guardada" del picker.
                    if (photoBytes == null && savedUrl.isNotEmpty()) {
                        ProductImage(
                            imageUrl = savedUrl,
                            contentDescription = "Foto de referencia",
                            modifier = Modifier
                                .size(140.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .align(Alignment.CenterHorizontally)
                        )
                    }
                    ImagePickerButton(
                        currentImageUrl = null,
                        selectedBytes = photoBytes,
                        onImageSelected = { photoBytes = it }
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val masa = selectedMasa ?: return@Button
                            onConfirm(
                                ProductionOrderLine(
                                    id = existing?.id.orEmpty(),
                                    // El id sintético se conserva al editar: es la clave
                                    // de la línea en el lote y el nombre de su foto.
                                    productId = existing?.productId?.takeIf { it.isNotEmpty() }
                                        ?: newSpecialProductId(),
                                    productName = description.trim(),
                                    requestedQuantity = quantity,
                                    productUnit = "unidad",
                                    isSpecial = true,
                                    specialRecipeId = masa.id,
                                    specialRecipeName = masa.name,
                                    specialRawWeightGrams = grams,
                                    specialPhotoUrl = savedUrl,
                                    specialUnitPrice = priceText.toDoubleOrNull() ?: 0.0
                                ),
                                photoBytes
                            )
                        },
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                if (existing == null) "Agregar" else "Guardar",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta de un pedido especial ya anotado, dentro del paso de productos.
 *
 * Muestra masa + peso además de la descripción porque son los datos que la
 * planta va a usar: sin ellos la encargada no puede revisar si se equivocó al
 * anotarlo, y el error se descubre hasta la amasada.
 */
@Composable
internal fun SpecialOrderCard(
    line: ProductionOrderLine,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFEBD3A0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (line.specialPhotoUrl.isNotEmpty()) {
                ProductImage(
                    imageUrl = line.specialPhotoUrl,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                )
            } else {
                Icon(Icons.Default.Star, contentDescription = null, tint = BrandColors.AccentDeep)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    line.productName,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandColors.Primary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    listOfNotNull(
                        line.specialRecipeName.takeIf { it.isNotBlank() },
                        formatBakerWeight(line.specialRawWeightGrams).takeIf { it != "—" }
                            ?.let { "$it c/u" },
                        line.specialUnitPrice.takeIf { it > 0.0 }?.let { "Q ${it.fmtTrim(2)}" }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6E6E6E)
                )
            }
            Surface(shape = RoundedCornerShape(20.dp), color = BrandColors.Accent) {
                Text(
                    "${line.requestedQuantity.toInt()} u.",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.OnAccent
                )
            }
            TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Editar", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onRemove, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Quitar", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828))
            }
        }
    }
}
