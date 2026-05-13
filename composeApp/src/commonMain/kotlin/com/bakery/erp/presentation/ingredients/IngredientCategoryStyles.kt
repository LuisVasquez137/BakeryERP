package com.bakery.erp.presentation.ingredients

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.convertUnits
import com.bakery.erp.model.largestUnit
// ── Category grouping ─────────────────────────────────────────────────────────

internal enum class CategoryGroup { PRIMARY, SECONDARY, TERTIARY }

internal val CATEGORY_GROUPS: Map<String, CategoryGroup> = mapOf(
    "Harinas"                 to CategoryGroup.PRIMARY,
    "Azúcares"                to CategoryGroup.PRIMARY,
    "Levaduras"               to CategoryGroup.PRIMARY,
    "Huevos"                  to CategoryGroup.PRIMARY,
    "Grasas"                  to CategoryGroup.SECONDARY,
    "Lácteos"                 to CategoryGroup.SECONDARY,
    "Frutas y Rellenos"       to CategoryGroup.SECONDARY,
    "Especias y Saborizantes" to CategoryGroup.SECONDARY,
    "Coberturas"              to CategoryGroup.TERTIARY,
    "Empaques"                to CategoryGroup.TERTIARY,
    "Otros"                   to CategoryGroup.TERTIARY,
)

internal val CATEGORY_ICONS: Map<String, ImageVector> = mapOf(
    "Harinas"                 to Icons.Default.Grain,
    "Grasas"                  to Icons.Default.WaterDrop,
    "Azúcares"                to Icons.Default.Cake,
    "Lácteos"                 to Icons.Default.LocalDrink,
    "Levaduras"               to Icons.Default.Science,
    "Huevos"                  to Icons.Default.Egg,
    "Frutas y Rellenos"       to Icons.Default.LocalFlorist,
    "Coberturas"              to Icons.Default.Palette,
    "Especias y Saborizantes" to Icons.Default.Spa,
    "Empaques"                to Icons.Default.Inventory2,
    "Otros"                   to Icons.Default.Category,
)

@Composable
internal fun groupContainerColor(group: CategoryGroup?) = when (group) {
    CategoryGroup.PRIMARY   -> MaterialTheme.colorScheme.primaryContainer
    CategoryGroup.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer
    CategoryGroup.TERTIARY  -> MaterialTheme.colorScheme.tertiaryContainer
    null                    -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
internal fun groupContentColor(group: CategoryGroup?) = when (group) {
    CategoryGroup.PRIMARY   -> MaterialTheme.colorScheme.onPrimaryContainer
    CategoryGroup.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
    CategoryGroup.TERTIARY  -> MaterialTheme.colorScheme.onTertiaryContainer
    null                    -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun categoryIcon(category: String): ImageVector =
    CATEGORY_ICONS[category] ?: Icons.Default.Kitchen

// ── Stock status ──────────────────────────────────────────────────────────────

internal enum class StockStatus { OK, LOW, OUT, NOT_SET }

internal fun stockStatus(stock: Double?, ingredient: Ingredient): StockStatus {
    val cur = stock ?: return StockStatus.NOT_SET
    if (cur <= 0.0) return StockStatus.OUT
    val alert = ingredient.stockAlert ?: return StockStatus.OK
    val defaultFormat = ingredient.purchaseFormats.firstOrNull { it.isDefault }
        ?: ingredient.purchaseFormats.firstOrNull()
    val thresholdInBase: Double = if (defaultFormat != null) {
        val factor = convertUnits(defaultFormat.unitsPerPackage, defaultFormat.unit, ingredient.effectiveBaseUnit)
        alert * factor
    } else {
        val largest = largestUnit(ingredient.allowedUnits)
        convertUnits(alert, largest, ingredient.effectiveBaseUnit)
    }
    return if (thresholdInBase > 0 && cur <= thresholdInBase) StockStatus.LOW else StockStatus.OK
}

@Composable
internal fun StockBadge(status: StockStatus) {
    val (color, label) = when (status) {
        StockStatus.OK      -> Color(0xFF2E7D32) to "Disponible"
        StockStatus.LOW     -> Color(0xFFE65100) to "Bajo stock"
        StockStatus.OUT     -> Color(0xFFC62828) to "Sin stock"
        StockStatus.NOT_SET -> Color(0xFF9E9E9E) to "Stock no establecido"
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
