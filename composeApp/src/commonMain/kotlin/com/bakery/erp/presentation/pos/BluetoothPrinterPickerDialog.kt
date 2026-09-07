package com.bakery.erp.presentation.pos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.domain.BluetoothPermissionDeniedException
import com.bakery.erp.domain.BluetoothPrinterManager
import com.bakery.erp.domain.PairedBluetoothDevice
import com.bakery.erp.domain.SavedPrinterConfig
import com.bakery.erp.ui.rememberBluetoothPermissionRequester

/**
 * Dialog para emparejar la impresora térmica Bluetooth. Lista los dispositivos vinculados
 * a la tablet y permite elegir uno + el ancho de papel (58/80mm). Persiste la selección.
 *
 * Si el listado falla (permiso denegado, Bluetooth apagado), muestra el error con un botón
 * para reintentar.
 */
@Composable
fun BluetoothPrinterPickerDialog(
    manager: BluetoothPrinterManager,
    onConfigured: () -> Unit,
    onDismiss: () -> Unit
) {
    var devices by remember { mutableStateOf<List<PairedBluetoothDevice>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var needsPermission by remember { mutableStateOf(false) }
    var permissionAutoRequested by remember { mutableStateOf(false) }
    var selectedAddress by remember { mutableStateOf<String?>(null) }
    var paperWidth by remember { mutableStateOf(58) }
    var attempt by remember { mutableStateOf(0) }

    val requestPermission = rememberBluetoothPermissionRequester { granted ->
        if (granted) {
            needsPermission = false
            error = null
            attempt++
        } else {
            needsPermission = true
            loading = false
            error = "Permiso de Bluetooth denegado. Concédelo para listar tus impresoras."
        }
    }

    LaunchedEffect(attempt) {
        loading = true
        error = null
        needsPermission = false
        manager.listPairedDevices()
            .onSuccess { list ->
                devices = list
                loading = false
                if (list.isEmpty()) {
                    error = "No hay dispositivos Bluetooth emparejados. Empareja tu impresora desde Configuración del sistema y vuelve a intentarlo."
                }
            }
            .onFailure { e ->
                if (e is BluetoothPermissionDeniedException) {
                    needsPermission = true
                    // Primera detección de permiso faltante → lanza el dialog del sistema
                    // automáticamente. Si después de eso el usuario lo niega, mostramos el
                    // botón para reintentar manualmente.
                    if (!permissionAutoRequested) {
                        permissionAutoRequested = true
                        requestPermission()
                    } else {
                        loading = false
                        error = "Se requiere permiso de Bluetooth para listar tus impresoras."
                    }
                } else {
                    loading = false
                    error = e.message ?: "No se pudo listar dispositivos"
                }
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Bluetooth, null, tint = MaterialTheme.colorScheme.primary,
                 modifier = Modifier.size(40.dp))
        },
        title = {
            Text("Conectar impresora",
                 fontWeight = FontWeight.SemiBold,
                 modifier = Modifier.fillMaxWidth())
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Selecciona la impresora térmica que usarás. Solo se hace la primera vez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                when {
                    loading -> Box(
                        Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    error != null -> Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        if (needsPermission) {
                            Button(onClick = { requestPermission() }) {
                                Text("Conceder permiso")
                            }
                        } else {
                            TextButton(onClick = { attempt++ }) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Reintentar")
                            }
                        }
                    }

                    else -> {
                        Text(
                            "Dispositivos emparejados",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(devices, key = { it.address }) { device ->
                                DeviceRow(
                                    device = device,
                                    selected = selectedAddress == device.address,
                                    onClick = { selectedAddress = device.address }
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Ancho de papel",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PaperWidthChip(label = "58 mm", selected = paperWidth == 58, onClick = { paperWidth = 58 })
                            PaperWidthChip(label = "80 mm", selected = paperWidth == 80, onClick = { paperWidth = 80 })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val addr = selectedAddress ?: return@Button
                    val name = devices.find { it.address == addr }?.name ?: addr
                    manager.saveConfig(SavedPrinterConfig(
                        deviceName = name,
                        deviceAddress = addr,
                        paperWidthMm = paperWidth
                    ))
                    onConfigured()
                },
                enabled = selectedAddress != null
            ) {
                Text("Guardar y conectar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun DeviceRow(
    device: PairedBluetoothDevice,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Bluetooth, null,
                 tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.SemiBold,
                     style = MaterialTheme.typography.bodyMedium)
                Text(device.address, style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PaperWidthChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}
