package com.bakery.erp.presentation.pos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.ExternalCustomer
import com.bakery.erp.model.normalizeForSearch
import com.bakery.erp.ui.theme.BrandColors

/**
 * A qué revendedor se le vende. Busca por nombre o por nombre del negocio, porque en el mostrador
 * se le conoce por cualquiera de los dos ("Don Beto" o "Tienda La Esperanza").
 *
 * El que no tiene lista de precios se muestra igual, marcado: esconderlo haría pensar que no
 * está dado de alta, y lo que falta es asignarle la lista.
 */
@Composable
internal fun ResellerPickerDialog(
    resellers: List<ExternalCustomer>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (ExternalCustomer) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(resellers, query) {
        val q = normalizeForSearch(query.trim())
        if (q.isEmpty()) resellers
        else resellers.filter {
            normalizeForSearch(it.name).contains(q) || normalizeForSearch(it.businessName).contains(q)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Venta a revendedor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Se cobra con la lista de precios del revendedor, de contado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar revendedor") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                when {
                    isLoading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    resellers.isEmpty() -> Text(
                        "No hay revendedores activos. Dalos de alta en Clientes → Revendedores, " +
                            "con su lista de precios.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    shown.isEmpty() -> Text("Nadie con ese nombre.", style = MaterialTheme.typography.bodySmall)
                    else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(shown, key = { it.id }) { r ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(r) }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(r.name, fontWeight = FontWeight.SemiBold)
                                val detail = listOf(
                                    r.businessName,
                                    if (r.priceListId.isBlank()) "Sin lista de precios"
                                    else "Lista: ${r.priceListName.ifBlank { "asignada" }}"
                                ).filter { it.isNotBlank() }.joinToString(" · ")
                                Text(
                                    detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (r.priceListId.isBlank()) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/**
 * Aviso fijo arriba del catálogo mientras se le vende a un revendedor. Tiene que verse todo el
 * tiempo: los precios de las fichas cambiaron, y una cajera que no lo nota le cobra barato al
 * siguiente cliente de la fila.
 */
@Composable
internal fun ResellerSaleBanner(
    resale: ResellerSale,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = BrandColors.AccentSoft, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Storefront, null, tint = BrandColors.AccentIcon, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Revendedor: ${resale.customer.name}",
                    color = BrandColors.Primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Precios de «${resale.priceListName}» · de contado",
                    color = BrandColors.Primary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onEnd) { Text("Volver a mostrador", color = BrandColors.Primary) }
        }
    }
}
