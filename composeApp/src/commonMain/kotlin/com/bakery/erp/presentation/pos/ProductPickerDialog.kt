package com.bakery.erp.presentation.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bakery.erp.model.Product
import com.bakery.erp.util.fmtMoney

/**
 * Dialog que muestra una lista de productos con stock visible para que el cajero los seleccione.
 * Usado tanto para grupos rápidos DETAILED como para búsqueda libre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductPickerDialog(
    title: String,
    products: List<Product>,
    /** null = no mostrar existencias (elegir de catálogo, no del estante). */
    stockOf: ((productId: String) -> Double)?,
    showSearch: Boolean = false,
    onDismiss: () -> Unit,
    onSelect: (Product) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(products, query) {
        if (query.isBlank()) products
        else products.filter { it.name.contains(query, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                     fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                if (showSearch) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Buscar") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(filtered, key = { it.id }) { p ->
                        val stock = stockOf?.invoke(p.id)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            onClick = { onSelect(p) }
                        ) {
                            Row(Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontWeight = FontWeight.SemiBold)
                                    Text("Q ${p.salePrice.fmtMoney(2)}",
                                         style = MaterialTheme.typography.bodySmall,
                                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (stock != null) Column(horizontalAlignment = Alignment.End) {
                                    Text("Stock", style = MaterialTheme.typography.labelSmall,
                                         color = MaterialTheme.colorScheme.outline)
                                    Text(stock.fmtMoney(0),
                                         fontWeight = FontWeight.Bold,
                                         color = if (stock <= 0) MaterialTheme.colorScheme.error
                                                 else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Text("Sin resultados.",
                                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                                 modifier = Modifier.padding(16.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                }
            }
        }
    }
}
