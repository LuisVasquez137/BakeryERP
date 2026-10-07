package com.bakery.erp.presentation.dispatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.OTHER_REASON
import com.bakery.erp.ui.theme.BrandColors

/**
 * Motivo de una diferencia en el despacho: opciones fijas en chips y "Otro" con detalle.
 *
 * El valor se guarda como texto legible ("No alcanzó la producción" u "Otro: se cayó la
 * bandeja") para que las notas viejas, escritas a mano, se sigan leyendo igual. Lo que
 * cambia es que lo nuevo se puede sumar: con texto libre, dos semanas de empaque decían
 * "no se iso", "nose iso" y "f" para lo mismo. Ver `model/UnitChain.kt`.
 *
 * Un valor viejo que no es ninguna opción se muestra como "Otro" con ese texto de detalle,
 * así corregir una nota vieja no borra lo que ya decía.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReasonPicker(
    title: String,
    options: List<String>,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isOption = value in options
    val isOther = !isOption && value.isNotBlank()
    // Sin recortar mientras se escribe: recortar en cada tecla se comería el espacio
    // entre palabras. El valor se recorta al guardar.
    val otherDetail = if (isOther) value.removePrefix("$OTHER_REASON:").removePrefix(" ") else ""
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = BrandColors.PrimaryDark)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = value == option,
                    onClick = { onValueChange(option) },
                    label = { Text(option) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandColors.PrimaryContainer,
                        selectedLabelColor = BrandColors.Primary
                    )
                )
            }
            FilterChip(
                selected = isOther || value == "$OTHER_REASON:",
                onClick = { if (!isOther) onValueChange("$OTHER_REASON:") },
                label = { Text(OTHER_REASON) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BrandColors.PrimaryContainer,
                    selectedLabelColor = BrandColors.Primary
                )
            )
        }
        if (isOther || value == "$OTHER_REASON:") {
            OutlinedTextField(
                value = otherDetail,
                onValueChange = { onValueChange(if (it.isBlank()) "$OTHER_REASON:" else "$OTHER_REASON: $it") },
                label = { Text("¿Qué pasó? *") },
                isError = otherDetail.isBlank(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
