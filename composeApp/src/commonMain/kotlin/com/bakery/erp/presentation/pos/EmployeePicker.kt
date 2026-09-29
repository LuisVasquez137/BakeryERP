package com.bakery.erp.presentation.pos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.bakery.erp.model.Employee
import com.bakery.erp.ui.theme.BrandColors

/**
 * Elegir un colaborador (de Colaboradores, no de usuarios de la app): la tablet de la caja
 * tiene un solo usuario, así que es lo que dice quién estaba.
 *
 * [noneLabel] agrega una opción "nadie" (al cerrar el último turno del día no hay quien reciba);
 * elegirla también cuenta como respuesta, por eso [chosen] va aparte de [selected].
 */
@Composable
fun EmployeePickerField(
    label: String,
    employees: List<Employee>,
    selected: Employee?,
    chosen: Boolean,
    onSelect: (Employee?) -> Unit,
    noneLabel: String? = null
) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Person, null)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    selected != null -> selected.name
                    chosen && noneLabel != null -> noneLabel
                    else -> "Elegir colaborador"
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
    if (open) {
        var query by remember { mutableStateOf("") }
        val q = query.trim().lowercase()
        val found = employees.filter { q.isEmpty() || it.name.lowercase().contains(q) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { query = it }, label = { Text("Buscar") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        if (noneLabel != null) {
                            item {
                                Row(Modifier.fillMaxWidth().clickable { onSelect(null); open = false }.padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(noneLabel, color = BrandColors.MutedSlate)
                                }
                                HorizontalDivider()
                            }
                        }
                        items(found, key = { it.id }) { e ->
                            Row(Modifier.fillMaxWidth().clickable { onSelect(e); open = false }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.name, color = BrandColors.TextSlate)
                                    val sub = listOf(e.positionTitle, e.branchName).filter { it.isNotBlank() }.joinToString(" · ")
                                    if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
                                }
                            }
                        }
                    }
                    if (found.isEmpty()) {
                        Text("No hay colaboradores activos con ese nombre. Se registran en Administración → Colaboradores.",
                            style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } }
        )
    }
}
