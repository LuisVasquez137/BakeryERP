package com.bakery.erp.presentation.ingredients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bakery.erp.model.Ingredient

/**
 * Selector de insumo con buscador. Lo usan la receta, la preparación y el producto de
 * reventa que se respalda en un insumo.
 *
 * Vivía duplicado como `private` en dos formularios y estaba a punto de triplicarse. Es
 * `internal` —de módulo, no de paquete— así que se ve desde cualquier `presentation/`,
 * el mismo mecanismo con el que despacho reutiliza `groupByMasa` de producción.
 *
 * **Filtrar es del llamador, no de acá**: la receta pasa solo materia prima
 * (`usableInRecipes`), y decidirlo adentro obligaría a este diálogo a conocer para qué se
 * le está pidiendo el insumo.
 */
@Composable
internal fun IngredientPickerDialog(
    ingredients: List<Ingredient>,
    onPick: (Ingredient) -> Unit,
    onDismiss: () -> Unit,
    alreadyAddedIds: Set<String> = emptySet(),
    title: String = "Seleccionar insumo",
    /**
     * Muestra la nota de "sigue agregando". Modo alta múltiple: el diálogo no se cierra al
     * seleccionar — eso lo decide el llamador.
     */
    keepAddingNote: Boolean = false
) {
    var searchQuery by remember { mutableStateOf("") }
    val sorted = remember(ingredients) { ingredients.sortedBy { it.name.lowercase() } }
    val filtered = remember(searchQuery, sorted) {
        if (searchQuery.isBlank()) sorted
        else sorted.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (keepAddingNote && ingredients.isNotEmpty()) {
                    Text(
                        text = "Sigue agregando los insumos que necesites; las cantidades las " +
                            "ingresarás después en el formulario. Cierra este cuadro cuando termines.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (ingredients.isEmpty()) {
                    Text(
                        text = "No hay insumos disponibles. Agrégalos primero en el módulo de Insumos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Buscar insumo") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        if (filtered.isEmpty()) {
                            item {
                                Text(
                                    text = "Sin resultados para \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        } else {
                            items(filtered, key = { it.id }) { ing ->
                                val isAdded = ing.id in alreadyAddedIds
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isAdded) { onPick(ing) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = ing.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isAdded) MaterialTheme.colorScheme.outline
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = when {
                                            isAdded && ing.allowedUnits.isNotEmpty() ->
                                                "${ing.allowedUnits.joinToString(" / ")} • ya agregado"
                                            isAdded -> "ya agregado"
                                            ing.allowedUnits.isNotEmpty() -> ing.allowedUnits.joinToString(" / ")
                                            else -> ""
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                }
            }
        }
    }
}
