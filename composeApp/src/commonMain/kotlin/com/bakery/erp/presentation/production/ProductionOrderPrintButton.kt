package com.bakery.erp.presentation.production

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.domain.BluetoothPrinterManager
import com.bakery.erp.domain.DocumentPrinter
import com.bakery.erp.model.ProductionOrderPrintData
import com.bakery.erp.presentation.pos.BluetoothPrinterPickerDialog
import com.bakery.erp.util.toPrintableDocument
import kotlinx.coroutines.launch
import com.bakery.erp.ui.theme.BrandColors

/**
 * Botón de impresora del TopAppBar de la orden de producción, con todo su flujo.
 *
 * El panadero imprime una vez por turno y con las manos ocupadas, así que el camino
 * feliz es **un solo toque**: si ya hay térmica configurada (o la plataforma no tiene
 * Bluetooth, como la web), el papel sale de inmediato. El diálogo de elección aparece
 * solo la primera vez en Android, que es cuando de verdad hay una decisión que tomar,
 * y desde ahí [BluetoothPrinterPickerDialog] pide el permiso de dispositivos cercanos
 * si todavía no se concedió.
 *
 * Cambiar de impresora se ofrece en el snackbar posterior a imprimir, no en un menú:
 * es justo el momento en que uno descubre que salió por la impresora equivocada.
 */
@Composable
internal fun ProductionOrderPrintButton(
    documentPrinter: DocumentPrinter,
    bluetoothPrinterManager: BluetoothPrinterManager,
    snackbarHostState: SnackbarHostState,
    /** Se evalúa al tocar el botón, para imprimir siempre el estado más reciente. */
    printData: () -> ProductionOrderPrintData?
) {
    val scope = rememberCoroutineScope()
    var isPrinting by remember { mutableStateOf(false) }
    var showChoice by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    fun doPrint() {
        val data = printData()
        if (data == null || data.groups.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("No hay productos que imprimir en esta orden.") }
            return
        }
        isPrinting = true
        scope.launch {
            val result = documentPrinter.print(data.toPrintableDocument())
            isPrinting = false
            result
                .onFailure { e ->
                    snackbarHostState.showSnackbar(e.message ?: "No se pudo imprimir la orden.")
                }
                .onSuccess {
                    // En web el propio diálogo del navegador es el acuse; el snackbar
                    // solo aporta donde hay una térmica que pudo ser la equivocada.
                    val printer = bluetoothPrinterManager.getSavedConfig() ?: return@onSuccess
                    val action = snackbarHostState.showSnackbar(
                        message = "Orden enviada a ${printer.deviceName}",
                        actionLabel = "Cambiar"
                    )
                    if (action == SnackbarResult.ActionPerformed) {
                        bluetoothPrinterManager.clearConfig()
                        showPicker = true
                    }
                }
        }
    }

    IconButton(
        onClick = {
            when {
                !documentPrinter.isAvailable -> scope.launch {
                    snackbarHostState.showSnackbar("La impresión no está disponible en esta plataforma.")
                }
                bluetoothPrinterManager.isPlatformSupported &&
                    bluetoothPrinterManager.getSavedConfig() == null -> showChoice = true
                else -> doPrint()
            }
        },
        enabled = !isPrinting
    ) {
        if (isPrinting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Color.White
            )
        } else {
            Icon(
                Icons.Default.Print,
                contentDescription = "Imprimir orden de producción",
                tint = Color.White
            )
        }
    }

    if (showChoice) {
        PrintTargetChoiceDialog(
            onThermal = { showChoice = false; showPicker = true },
            onSystem = { showChoice = false; doPrint() },
            onDismiss = { showChoice = false }
        )
    }

    if (showPicker) {
        BluetoothPrinterPickerDialog(
            manager = bluetoothPrinterManager,
            onConfigured = {
                showPicker = false
                doPrint()
            },
            onDismiss = { showPicker = false }
        )
    }
}

/**
 * Primera impresión en Android: térmica de la panadería o diálogo del sistema.
 * Se muestra una sola vez — al elegir térmica, la config queda guardada.
 */
@Composable
private fun PrintTargetChoiceDialog(
    onThermal: () -> Unit,
    onSystem: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Print, null, tint = BrandColors.Primary, modifier = Modifier.size(36.dp))
        },
        title = { Text("¿Dónde quieres imprimir?", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Solo se pregunta la primera vez: la impresora que elijas queda guardada " +
                        "en esta tablet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                PrintTargetOption(
                    icon = { Icon(Icons.Default.Bluetooth, null, tint = BrandColors.Primary) },
                    title = "Impresora térmica Bluetooth",
                    subtitle = "Rollo de 58 o 80 mm, la de la panadería",
                    onClick = onThermal
                )
                Spacer(Modifier.height(8.dp))
                PrintTargetOption(
                    icon = { Icon(Icons.Default.Description, null, tint = BrandColors.Primary) },
                    title = "Hoja normal o PDF",
                    subtitle = "Abre el diálogo de impresión del sistema",
                    onClick = onSystem
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun PrintTargetOption(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3F8))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            icon()
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
