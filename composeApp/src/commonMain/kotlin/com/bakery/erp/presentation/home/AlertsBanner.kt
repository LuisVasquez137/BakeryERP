package com.bakery.erp.presentation.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.domain.report.AlertSeverity
import com.bakery.erp.domain.report.AlertTarget
import com.bakery.erp.domain.report.HomeAlert
import kotlinx.coroutines.delay
import com.bakery.erp.ui.theme.BrandColors.Primary as InfoColor
import com.bakery.erp.ui.theme.BrandColors

// CRITICAL en rojo y WARNING en naranja: pintar lo critico del color de marca hacia
// que la alerta mas grave se leyera igual que un aviso rutinario.
private val CriticalColor = Color(0xFFC62828)
private val WarningColor  = BrandColors.AccentDeep

@Composable
fun AlertsBanner(
    alerts: List<HomeAlert>,
    onAlertClick: (HomeAlert) -> Unit,
    modifier: Modifier = Modifier
) {
    if (alerts.isEmpty()) return

    var index by remember(alerts.size) { mutableStateOf(0) }
    LaunchedEffect(alerts.size) {
        while (alerts.size > 1) {
            delay(6_000)
            index = (index + 1) % alerts.size
        }
    }
    val current = alerts.getOrNull(index) ?: alerts.first()
    val accent = current.severity.color()

    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAlertClick(current) },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = BorderStroke(1.5.dp, accent.copy(alpha = 0.55f))
        ) {
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { it / 6 }) togetherWith
                        (fadeOut() + slideOutHorizontally { -it / 6 })
                },
                label = "alerts"
            ) { alert ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(accent.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = alert.target.icon(),
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = alert.title,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = BrandColors.TextSlate,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = alert.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF607585),
                            // Alto fijo de 2 líneas: una alerta de 1 línea y otra de 2
                            // cambiaban la altura del banner cada 6 s y toda la lista de
                            // Home se recolocaba (se sentía como un tirón al hacer scroll).
                            minLines = 2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = alert.actionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        if (alerts.size > 1) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                alerts.forEachIndexed { i, _ ->
                    val active = i == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (active) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (active) accent else Color(0xFFCBD5DC))
                    )
                }
            }
        }
    }
}

private fun AlertSeverity.color(): Color = when (this) {
    AlertSeverity.CRITICAL -> CriticalColor
    AlertSeverity.WARNING  -> WarningColor
    AlertSeverity.INFO     -> InfoColor
}

private fun AlertTarget.icon(): ImageVector = when (this) {
    AlertTarget.LOW_STOCK_INGREDIENTS -> Icons.Default.Inventory
    AlertTarget.UPCOMING_MAINTENANCE  -> Icons.Default.Build
    AlertTarget.IN_TRANSIT_DISPATCH   -> Icons.Default.LocalShipping
    AlertTarget.STALE_PRODUCTION_LOT  -> Icons.Default.Factory
    AlertTarget.LATE_PRODUCTION_ORDER -> Icons.Default.Warning
    AlertTarget.OVERDUE_CASH_SESSION  -> Icons.Default.PointOfSale
    AlertTarget.DISPATCH_PRICE_MISSING -> Icons.Default.AttachMoney
    AlertTarget.OVERDUE_PURCHASE      -> Icons.Default.ReceiptLong
    AlertTarget.CREDIT_OVERDUE        -> Icons.Default.ReceiptLong
    AlertTarget.EXPIRING_INGREDIENT   -> Icons.Default.EventBusy
    AlertTarget.STOCK_REPLENISHMENT   -> Icons.Default.Factory
}
