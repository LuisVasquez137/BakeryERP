package com.bakery.erp.presentation.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.CostCenter
import com.bakery.erp.ui.theme.BrandColors

/**
 * "¿De quién es el gasto?": la sucursal, Generales o Reparto. Ver [CostCenter].
 *
 * Chips y no un desplegable: son tres opciones y la que importa —que NO es de la sucursal—
 * tiene que verse sin abrir nada. Cada compartida dice qué entra en ella, porque "Generales"
 * a secas no le dice a quien anota si la luz de la planta va ahí (no va).
 *
 * @param selected `null` = todavía no se eligió. Solo pasa cuando [required]: anotar desde la
 *   caja general es donde se mezclan las facturas de todos, y dejar que caiga sola a la
 *   sucursal de turno es justo lo que cargaría el contador a la planta sin que nadie lo vea.
 * @param branchLabel Nombre de la sucursal para la primera opción. Se nombra en vez de decir
 *   "Sucursal" para que quien anota sepa a cuál va a quedar.
 * @param splitsStore La sucursal produce y vende ([com.bakery.erp.model.splitsStore]): en vez
 *   de una sola opción para ella se ofrecen su planta, su tienda y el local compartido, porque
 *   el panadero es costo del pan y la cajera no.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CostCenterPicker(
    selected: String?,
    branchLabel: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "¿De quién es el gasto?",
    required: Boolean = false,
    splitsStore: Boolean = false,
    /** false = solo las partes de la sucursal (cuando Generales/Reparto se eligen en otro lado). */
    includeShared: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (required) "$label *" else label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val own = if (splitsStore) CostCenter.SPLIT else listOf(CostCenter.BRANCH)
            (own + if (includeShared) CostCenter.SHARED else emptyList()).forEach { option ->
                val isSelected = selected == option
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(option) },
                    label = {
                        Text(optionLabel(option, branchLabel, splitsStore))
                    },
                    leadingIcon = if (isSelected) ({
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    }) else null
                )
            }
        }
        when {
            selected == null && required -> Text(
                "Elegí a quién le corresponde. Si es de una tienda o planta, tocá su nombre.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
            selected != null && splitsStore && selected == CostCenter.BRANCH -> Text(
                "Panaderos, gas del horno, mantenimiento de la planta: lo de producción. Entra al costo del pan.",
                style = MaterialTheme.typography.labelSmall,
                color = BrandColors.Primary
            )
            selected != null && CostCenter.hint(selected).isNotEmpty() -> Text(
                CostCenter.hint(selected),
                style = MaterialTheme.typography.labelSmall,
                color = BrandColors.Primary
            )
        }
    }
}

private fun optionLabel(option: String, branchLabel: String, splitsStore: Boolean): String {
    val name = branchLabel.ifEmpty { "Sucursal" }
    return when {
        option == CostCenter.BRANCH -> if (splitsStore) "Planta $branchLabel".trim() else name
        option == CostCenter.STORE -> "Tienda $branchLabel".trim()
        else -> CostCenter.label(option)
    }
}
