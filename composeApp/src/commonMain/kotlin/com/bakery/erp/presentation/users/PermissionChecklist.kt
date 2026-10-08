package com.bakery.erp.presentation.users

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Permission
import com.bakery.erp.ui.theme.BrandColors

/**
 * Lista de permisos agrupada por módulo, con "Habilitar todos" por grupo. La misma para la
 * cuenta de un usuario y para el PIN de un colaborador: si fueran dos listas, tarde o
 * temprano una tendría un permiso que la otra no.
 *
 * [enabled] = false la deja de solo lectura (quien no es OWNER/ADMIN ve qué tiene alguien
 * pero no lo cambia).
 */
@Composable
fun PermissionChecklist(
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    enabled: Boolean = true
) {
    val groups = Permission.entries.groupBy { it.group }
    groups.forEach { (groupName, perms) ->
        // El maestro del grupo NO toca los permisos sensibles (ver dinero): se marcan uno
        // por uno, con intención. Antes "Habilitar todos" en Distribución le daba a
        // cualquiera que empaca el valor de cada nota de despacho, de regalo.
        val groupPermNames = perms.filterNot { it.isSensitive }.map { it.name }
        val selectedInGroup = groupPermNames.count { it in selected }
        val groupTriState = when (selectedInGroup) {
            0 -> ToggleableState.Off
            groupPermNames.size -> ToggleableState.On
            else -> ToggleableState.Indeterminate
        }
        val toggleGroup = {
            onChange(
                if (selectedInGroup == groupPermNames.size) selected - groupPermNames.toSet()
                else selected + groupPermNames.toSet()
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { toggleGroup() }
                .padding(top = 8.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = groupName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            if (enabled) {
                Text(
                    text = "Habilitar todos",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TriStateCheckbox(state = groupTriState, onClick = { toggleGroup() })
            }
        }
        perms.forEach { perm ->
            val toggle = { onChange(if (perm.name in selected) selected - perm.name else selected + perm.name) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { toggle() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = perm.name in selected,
                    onCheckedChange = { toggle() },
                    enabled = enabled
                )
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(perm.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        perm.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Se avisa en la fila, no solo en la lógica: quien reparte permisos
                    // tiene que ver que éste muestra dinero antes de marcarlo.
                    if (perm.isSensitive) {
                        Text(
                            "Muestra dinero · queda fuera de \"Habilitar todos\"",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandColors.AccentDeep
                        )
                    }
                }
            }
        }
    }
}
