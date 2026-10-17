package com.bakery.erp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.domain.ConnectionStatus
import com.bakery.erp.domain.FailedSaleUpload
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmt
import kotlinx.coroutines.delay

private enum class BannerState { HIDDEN, OFFLINE, SYNCING, BACK_ONLINE }

/**
 * Envuelve toda la app con el aviso de conexión: una franja arriba de cualquier pantalla.
 *
 * La franja se lleva el alto de la barra de estado y se lo quita al contenido
 * (`consumeWindowInsets`), así la barra superior de cada pantalla no deja otro hueco igual
 * debajo. Mientras no hay franja, el contenido maneja sus insets como siempre.
 *
 * Espera unos segundos antes de avisar: al pasar de wifi a datos la red cae un instante y
 * no queremos que la franja parpadee. Al volver, si quedaron ventas cobradas sin internet
 * las muestra subiendo (azul) y solo cuando terminan avisa en verde un momento y se va.
 */
@Composable
fun ConnectivityBannerHost(content: @Composable () -> Unit) {
    val online by rememberIsOnline()
    val pendingSales by ConnectionStatus.pendingSales.collectAsState()
    val failedSales by ConnectionStatus.failedSales.collectAsState()
    var state by remember { mutableStateOf(if (online) BannerState.HIDDEN else BannerState.OFFLINE) }

    // Los ViewModels leen la conexión de aquí (ConnectionStatus), no de la UI.
    LaunchedEffect(online) { ConnectionStatus.setOnline(online) }

    LaunchedEffect(online, pendingSales > 0) {
        when {
            !online -> {
                delay(2_000)
                state = BannerState.OFFLINE
            }
            // De vuelta, pero con ventas todavía subiendo: no se dice "listo" hasta que suban.
            pendingSales > 0 && state != BannerState.HIDDEN -> state = BannerState.SYNCING
            state == BannerState.OFFLINE || state == BannerState.SYNCING -> {
                state = BannerState.BACK_ONLINE
                delay(3_000)
                state = BannerState.HIDDEN
            }
        }
    }

    val visible = state != BannerState.HIDDEN
    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            val pending = if (pendingSales > 0) " · $pendingSales venta(s) por subir" else ""
            when (state) {
                BannerState.OFFLINE -> ConnectivityStrip(
                    background = BrandColors.Accent, ink = BrandColors.OnAccent, icon = Icons.Default.CloudOff,
                    text = "Sin conexión a internet$pending"
                )
                BannerState.SYNCING -> ConnectivityStrip(
                    background = BrandColors.Primary, ink = Color.White, icon = Icons.Default.CloudUpload,
                    text = "Conexión restablecida · subiendo $pendingSales venta(s)…"
                )
                else -> ConnectivityStrip(
                    background = BrandColors.Success, ink = Color.White, icon = Icons.Default.CloudDone,
                    text = "Conexión restablecida"
                )
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (visible) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)
        ) {
            content()
        }
    }

    // Una venta que el servidor rechazó al subir ya no existe, pero el dinero sí está en la
    // gaveta. No puede pasar en silencio: se muestra en cualquier pantalla hasta que la vean.
    failedSales.firstOrNull()?.let { failed -> FailedSaleDialog(failed) }
}

@Composable
private fun FailedSaleDialog(failed: FailedSaleUpload) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = BrandColors.Danger) },
        title = { Text("Una venta no se pudo guardar") },
        text = {
            Text(
                "La venta de Q ${failed.total.fmt(2)} (${failed.summary}) se cobró sin internet, " +
                    "pero el servidor no la aceptó al subir y no quedó registrada ni descontó inventario.\n\n" +
                    "Anotala de nuevo o avisale al encargado.\n\nDetalle: ${failed.message}"
            )
        },
        confirmButton = {
            TextButton(onClick = { ConnectionStatus.dismissFailedSale(failed.saleId) }) { Text("Entendido") }
        }
    )
}

@Composable
private fun ConnectivityStrip(background: Color, ink: Color, icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(18.dp)
        )
        Box(Modifier.width(8.dp))
        Text(
            text = text,
            color = ink,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
