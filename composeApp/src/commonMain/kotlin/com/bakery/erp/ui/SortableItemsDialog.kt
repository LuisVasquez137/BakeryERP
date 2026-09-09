package com.bakery.erp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.ui.theme.BrandColors

/** Un elemento ordenable: el `id` es lo que se persiste, el `label` lo que se ve. */
data class SortableItem(val id: String, val label: String)

/**
 * Diálogo genérico para fijar el orden de una lista con botones subir/bajar.
 *
 * Se usan botones y no arrastrar por la misma razón que en
 * `ProductSortOrderDialog`: estas pantallas se usan en tablet dentro de la
 * planta, muchas veces con las manos ocupadas o con harina, y un toque preciso
 * es más confiable que un gesto de arrastre sostenido.
 *
 * **Guarda lista vacía cuando el orden quedó alfabético**, en vez de guardar la
 * lista completa: así el default vuelve a ser dinámico y absorbe solo los
 * elementos nuevos. Una lista fija con todos los ids dejaría cualquier sucursal
 * o producto nuevo colgando al final para siempre.
 */
@Composable
fun SortableItemsDialog(
    title: String,
    description: String,
    items: List<SortableItem>,
    onDismiss: () -> Unit,
    onSave: (orderedIds: List<String>) -> Unit,
    emptyText: String = "No hay nada que ordenar.",
    resetLabel: String = "Restablecer orden alfabético"
) {
    var ordered by remember(items) { mutableStateOf(items) }

    fun move(from: Int, to: Int) {
        if (to !in ordered.indices) return
        ordered = ordered.toMutableList().apply { add(to, removeAt(from)) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                if (ordered.isEmpty()) {
                    Text(
                        emptyText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                        itemsIndexed(ordered, key = { _, item -> item.id }) { index, item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${index + 1}.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(28.dp)
                                )
                                Text(
                                    item.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { move(index, index - 1) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        "Subir ${item.label}",
                                        tint = BrandColors.Primary
                                    )
                                }
                                IconButton(
                                    onClick = { move(index, index + 1) },
                                    enabled = index < ordered.lastIndex,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        "Bajar ${item.label}",
                                        tint = BrandColors.Primary
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { ordered = ordered.sortedBy { it.label } },
                    enabled = ordered.isNotEmpty()
                ) {
                    Text(resetLabel, color = BrandColors.AccentDeep)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val isAlphabetical = ordered == ordered.sortedBy { it.label }
                    onSave(if (isAlphabetical) emptyList() else ordered.map { it.id })
                }
            ) { Text("Guardar", fontWeight = FontWeight.Bold, color = BrandColors.Primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
