package com.bakery.erp.presentation.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.PackagingItem
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmtMoney

/**
 * Botones de empaque cobrado ("+ Bolsa Q0.25") junto al total. Van al pie y no en la cuadrícula
 * de productos porque la bolsa se pide al final, cuando ya se está empacando. Cada toque suma
 * uno; para quitar, el "−" del renglón en el carrito.
 *
 * No pinta nada si la empresa no tiene empaques activos: la panadería que no cobra la bolsa ni
 * se entera de que esto existe.
 */
@Composable
internal fun PackagingQuickBar(
    items: List<PackagingItem>,
    /** Cuántos de cada empaque hay ya en el carrito, por id. */
    inCart: Map<String, Double>,
    onAdd: (PackagingItem) -> Unit,
    modifier: Modifier = Modifier,
    /** Sobre la barra azul del teléfono los botones van en blanco. */
    onPrimary: Boolean = false
) {
    if (items.isEmpty()) return
    // A partes iguales en una fila, no en FlowRow: con el panel del carrito angosto, "Bolsa" y
    // "Caja pastel" a su ancho natural no cabían juntos y quedaban uno debajo del otro.
    val perRow = minOf(items.size, MAX_PER_ROW)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    PackagingButton(
                        item, inCart[item.id] ?: 0.0, onPrimary,
                        onClick = { onAdd(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // La última fila incompleta conserva el mismo ancho de botón que las demás.
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val MAX_PER_ROW = 3

@Composable
private fun PackagingButton(
    item: PackagingItem,
    count: Double,
    onPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val added = count > 0.0
    // En el carrito: relleno azul claro, así de lejos se ve que la bolsa ya va cobrada.
    val container = when {
        onPrimary -> Color.White
        added -> BrandColors.PrimaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = container,
        contentColor = BrandColors.Primary,
        border = if (onPrimary) null else BorderStroke(1.dp, BrandColors.Primary.copy(alpha = if (added) 1f else 0.5f)),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ShoppingBag, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            // Precio debajo del nombre: así dos empaques caben uno a la par del otro.
            Column(Modifier.weight(1f)) {
                Text(
                    "+ ${item.name}",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Q ${item.salePrice.fmtMoney(2)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandColors.MutedSlate,
                    maxLines = 1
                )
            }
            if (added) {
                Spacer(Modifier.width(4.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = BrandColors.Primary, contentColor = Color.White) {
                    Text(
                        "×${count.fmtMoney(0)}",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
