package com.bakery.erp.presentation.production

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Product
import com.bakery.erp.ui.theme.BrandColors

/**
 * Diálogo para definir en qué orden se listan los productos de una planta.
 *
 * Se usa en dos lugares con **listas independientes**:
 *  - El consolidado de pedidos ([com.bakery.erp.model.Branch.productSortOrder]),
 *    que la encargada ordena según cómo revisa los pedidos entrantes.
 *  - La orden de producción del panadero
 *    ([com.bakery.erp.model.Branch.productionSortOrder]), que sigue la secuencia
 *    real del horno.
 *
 * Son campos distintos a propósito: revisar pedidos y hornear no tienen por qué
 * llevar el mismo orden, y forzar uno solo obligaría a que uno de los dos ceda.
 *
 * Se usan botones subir/bajar en vez de arrastrar: estas pantallas se consultan
 * desde tablet en la planta, muchas veces con las manos ocupadas o con harina, y
 * un toque preciso es más confiable que un gesto de arrastre sostenido.
 *
 * La lista base son los productos asignados a la planta (o el catálogo activo
 * completo si no tiene asignación), no solo los del día: así el orden queda
 * definido de una vez y no cambia según lo que se pidió o se hornea hoy.
 *
 * **Orden por receta**: si [recipeIdOf] devuelve una masa para al menos un
 * producto, el diálogo agrupa por receta — mover una masa mueve con ella todos
 * los productos que salen de la misma amasada (la amasada es una sola, no tiene
 * sentido ordenar sus panes por separado). Sin eso (default), sigue siendo la
 * lista plana producto por producto de siempre — así el consolidado de la
 * encargada no cambia de comportamiento sin pedirlo.
 */
@Composable
internal fun ProductSortOrderDialog(
    title: String,
    description: String,
    products: List<Product>,
    assignedProductIds: List<String>,
    currentOrder: List<String>,
    onDismiss: () -> Unit,
    onSave: (productIds: List<String>) -> Unit,
    recipeIdOf: (productId: String) -> String? = { null },
    recipeNameOf: (productId: String) -> String? = { null }
) {
    val initial = remember(products, assignedProductIds, currentOrder) {
        val index = currentOrder.withIndex().associate { (i, id) -> id to i }
        products
            .filter { it.active && (assignedProductIds.isEmpty() || it.id in assignedProductIds) }
            .sortedWith(compareBy({ index[it.id] ?: Int.MAX_VALUE }, { it.name }))
            .map { it.id to it.name }
    }
    val groupedByRecipe = remember(initial) { initial.any { recipeIdOf(it.first) != null } }

    if (groupedByRecipe) {
        RecipeGroupedSortDialog(
            title = title,
            description = description,
            initial = initial,
            currentOrder = currentOrder,
            recipeIdOf = recipeIdOf,
            recipeNameOf = recipeNameOf,
            onDismiss = onDismiss,
            onSave = onSave
        )
    } else {
        FlatProductSortDialog(
            title = title,
            description = description,
            initial = initial,
            onDismiss = onDismiss,
            onSave = onSave
        )
    }
}

@Composable
private fun FlatProductSortDialog(
    title: String,
    description: String,
    initial: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSave: (productIds: List<String>) -> Unit
) {
    var ordered by remember(initial) { mutableStateOf(initial) }

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
                        "No hay productos asignados a esta planta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                        itemsIndexed(ordered, key = { _, item -> item.first }) { index, item ->
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
                                    item.second,
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
                                        "Subir ${item.second}",
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
                                        "Bajar ${item.second}",
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
                    onClick = { ordered = ordered.sortedBy { it.second } },
                    enabled = ordered.isNotEmpty()
                ) {
                    Text("Restablecer orden alfabético", color = BrandColors.AccentDeep)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Si quedó en alfabético puro, guardamos lista vacía: el
                    // default vuelve a ser dinámico y absorbe productos nuevos.
                    val isAlphabetical = ordered == ordered.sortedBy { it.second }
                    onSave(if (isAlphabetical) emptyList() else ordered.map { it.first })
                }
            ) { Text("Guardar", fontWeight = FontWeight.Bold, color = BrandColors.Primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/**
 * Variante agrupada por masa: cada fila es una RECETA, no un producto — mover
 * "Hojaldre" mueve con ella Barcas, Cuadrados y Milhojas. Reutiliza
 * [groupByMasa]/[recipeOrderIndexFrom] (vive en [MasaGrouping.kt]) para que este
 * diálogo, la lista del panadero y el consolidado agrupen exactamente igual.
 */
@Composable
private fun RecipeGroupedSortDialog(
    title: String,
    description: String,
    initial: List<Pair<String, String>>,
    currentOrder: List<String>,
    recipeIdOf: (productId: String) -> String?,
    recipeNameOf: (productId: String) -> String?,
    onDismiss: () -> Unit,
    onSave: (productIds: List<String>) -> Unit
) {
    val groups = remember(initial, currentOrder) {
        val itemIndex = currentOrder.withIndex().associate { (i, id) -> id to i }
        val recipeIndex = recipeOrderIndexFrom(currentOrder) { productId -> recipeIdOf(productId) }
        groupByMasa(
            items = initial,
            recipeIdOf = { pair -> recipeIdOf(pair.first) },
            recipeNameOf = { pair -> recipeNameOf(pair.first) },
            nameOf = { pair -> pair.second },
            groupOrderIndex = { recipeIndex[it] ?: Int.MAX_VALUE },
            itemOrderIndex = { pair -> itemIndex[pair.first] ?: Int.MAX_VALUE }
        )
    }
    val groupsByKey = remember(groups) { groups.associateBy { it.key } }
    var groupOrder by remember(groups) { mutableStateOf(groups.map { it.key }) }

    fun move(from: Int, to: Int) {
        if (to !in groupOrder.indices) return
        groupOrder = groupOrder.toMutableList().apply { add(to, removeAt(from)) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "$description Se ordena por receta: mover una masa mueve con ella " +
                        "todos los productos que salen de la misma amasada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                if (groupOrder.isEmpty()) {
                    Text(
                        "No hay productos asignados a esta planta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        itemsIndexed(groupOrder, key = { _, key -> key }) { index, key ->
                            val group = groupsByKey[key] ?: return@itemsIndexed
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
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
                                        group.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandColors.Primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { move(index, index - 1) },
                                        enabled = index > 0,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            "Subir ${group.title}",
                                            tint = BrandColors.Primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { move(index, index + 1) },
                                        enabled = index < groupOrder.lastIndex,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            "Bajar ${group.title}",
                                            tint = BrandColors.Primary
                                        )
                                    }
                                }
                                if (group.items.isNotEmpty()) {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth().padding(start = 28.dp, top = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        group.items.forEach { (_, name) ->
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = Color(0xFFF6F6F4)
                                            ) {
                                                Text(
                                                    name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
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
                    onClick = { groupOrder = groupsByKey.values.sortedBy { it.title }.map { it.key } },
                    enabled = groupOrder.isNotEmpty()
                ) {
                    Text("Restablecer orden alfabético", color = BrandColors.AccentDeep)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val flattened = groupOrder.flatMap { key -> groupsByKey[key]?.items.orEmpty().map { it.first } }
                    val alphabeticalOrder = groupsByKey.values.sortedBy { it.title }.map { it.key }
                    val isAlphabetical = groupOrder == alphabeticalOrder
                    onSave(if (isAlphabetical) emptyList() else flattened)
                }
            ) { Text("Guardar", fontWeight = FontWeight.Bold, color = BrandColors.Primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/**
 * Aplica el orden configurado: primero los productos listados en [sortOrder] en
 * ese orden, luego los que no estén en la lista ordenados por [nameOf].
 * [sortOrder] vacío ⇒ todo alfabético.
 */
internal fun <T> applyProductSortOrder(
    items: List<T>,
    sortOrder: List<String>,
    productIdOf: (T) -> String,
    nameOf: (T) -> String
): List<T> {
    val index = sortOrder.withIndex().associate { (i, id) -> id to i }
    return items.sortedWith(
        compareBy({ index[productIdOf(it)] ?: Int.MAX_VALUE }, { nameOf(it) })
    )
}
