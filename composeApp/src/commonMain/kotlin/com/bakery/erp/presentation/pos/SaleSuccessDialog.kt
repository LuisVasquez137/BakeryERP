package com.bakery.erp.presentation.pos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipEmphasis
import com.bakery.erp.ui.theme.ChipTone
import com.bakery.erp.ui.theme.chipColors
import com.bakery.erp.util.fmtMoney
import kotlinx.coroutines.delay

/**
 * Acuse del cobro. Tiene que confirmar de un vistazo que la venta quedó: el ✓ entra con un
 * rebote y el vuelto cuenta hasta su valor —lo que la cajera va a entregar—. Imprimir muestra
 * que está imprimiendo y después que salió; antes el botón solo giraba un segundo y parecía que
 * no había pasado nada.
 */
@Composable
internal fun SaleSuccessDialog(
    total: Double,
    change: Double,
    paymentLabel: String,
    isPrinting: Boolean,
    isPrintingAvailable: Boolean,
    savedPrinterName: String?,
    onPrint: () -> Unit,
    onChangePrinter: () -> Unit,
    invoice: InvoiceProgress? = null,
    /** Cuándo terminó la última impresión (0 = nunca): muestra "Ticket impreso" un momento. */
    printedAt: Long = 0L,
    onDismiss: () -> Unit
) {
    val (successBg, successInk) = chipColors(ChipTone.SUCCESS, ChipEmphasis.SOFT)
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val checkScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "check"
    )
    val shownChange by animateFloatAsState(
        targetValue = if (appeared) change.toFloat() else 0f,
        animationSpec = tween(durationMillis = 700, delayMillis = 200),
        label = "change"
    )
    // "Ticket impreso" solo un momento después de cada impresión; el acuse se abrió antes.
    var openedAt by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) { openedAt = kotlin.time.Clock.System.now().toEpochMilliseconds() }
    var showPrinted by remember { mutableStateOf(false) }
    LaunchedEffect(printedAt) {
        if (printedAt > 0L && printedAt >= openedAt) {
            showPrinted = true
            delay(2_500)
            showPrinted = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = CircleShape,
                color = successInk,
                modifier = Modifier.size(64.dp).scale(checkScale)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
        },
        title = {
            Text("Venta cobrada", fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Q ${total.fmtMoney(2)}", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold, color = BrandColors.Primary)
                Spacer(Modifier.height(2.dp))
                Text(paymentLabel, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (change > 0.0) {
                    Spacer(Modifier.height(14.dp))
                    Surface(color = successBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Vuelto", style = MaterialTheme.typography.labelLarge, color = successInk)
                            Text("Q ${shownChange.toDouble().fmtMoney(2)}", style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.ExtraBold, color = successInk)
                        }
                    }
                }

                if (invoice != null) {
                    Spacer(Modifier.height(12.dp))
                    InvoiceStatusBanner(invoice)
                }

                // ── Impresión ──
                AnimatedVisibility(visible = isPrinting, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    PrintingBanner()
                }
                AnimatedVisibility(visible = showPrinted && !isPrinting, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = successInk, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ticket impreso", color = successInk, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (isPrintingAvailable && savedPrinterName != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Impresora: $savedPrinterName", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = onChangePrinter, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                            Text("Cambiar", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary)) {
                Text("Listo", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            if (isPrintingAvailable) {
                OutlinedButton(onClick = onPrint, enabled = !isPrinting) {
                    Icon(Icons.Default.Print, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            isPrinting -> "Imprimiendo…"
                            invoice?.status == com.bakery.erp.model.FelInvoiceStatus.CERTIFICADO -> "Imprimir factura"
                            else -> "Imprimir ticket"
                        }
                    )
                }
            }
        }
    )
}

/** La impresora "trabajando": el ícono late y la barra corre mientras sale el papel. */
@Composable
private fun PrintingBanner() {
    val pulse = rememberInfiniteTransition(label = "print")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "printAlpha"
    )
    Surface(
        color = BrandColors.PrimaryContainer, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Print, null, tint = BrandColors.Primary, modifier = Modifier.size(20.dp).alpha(alpha))
                Spacer(Modifier.width(8.dp))
                Text("Imprimiendo ticket…", color = BrandColors.Primary, fontWeight = FontWeight.SemiBold)
            }
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = BrandColors.Primary)
        }
    }
}
