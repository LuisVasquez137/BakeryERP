package com.bakery.erp.presentation.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.AppNotification
import com.bakery.erp.model.NotificationType
import com.bakery.erp.model.isReadBy
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import com.bakery.erp.ui.theme.BrandColors

/**
 * Bandeja de avisos. Los no leídos llevan punto y fondo tenue; al tocar uno se
 * marca leído y navega a donde apunta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationsSheet(
    notifications: List<AppNotification>,
    currentUserId: String,
    onDismiss: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpen: (AppNotification) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BrandColors.Ivory
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Avisos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.Primary
                )
                if (notifications.any { !it.isReadBy(currentUserId) }) {
                    TextButton(onClick = onMarkAllRead) {
                        Text("Marcar todo leído", color = BrandColors.Primary)
                    }
                }
            }

            if (notifications.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.NotificationsNone,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No tienes avisos",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                return@Column
            }

            LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                items(notifications, key = { it.id }) { notification ->
                    val unread = !notification.isReadBy(currentUserId)
                    Surface(
                        color = if (unread) Color(0xFFEAF3F8) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable { onOpen(notification) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp, end = 10.dp)
                                    .size(8.dp)
                                    .background(
                                        if (unread) notification.type.accentColor() else Color.Transparent,
                                        CircleShape
                                    )
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    notification.title,
                                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                                    color = BrandColors.Primary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    notification.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    relativeTime(notification.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

private fun String.accentColor(): Color = when (this) {
    NotificationType.PRODUCT_CANCELLED_MIN_BATCH -> Color(0xFFC62828)
    NotificationType.ORDER_IN_PRODUCTION -> Color(0xFF6A1B9A)
    NotificationType.ORDER_IN_LOT -> Color(0xFF1565C0)
    else -> BrandColors.AccentDeep
}

/** "hace 5 min" / "hace 2 h" / "12/08 06:40". */
private fun relativeTime(millis: Long): String {
    if (millis <= 0L) return ""
    return runCatching {
        val now = Clock.System.now().toEpochMilliseconds()
        val diff = now - millis
        when {
            diff < 60_000 -> "hace un momento"
            diff < 3_600_000 -> "hace ${diff / 60_000} min"
            diff < 86_400_000 -> "hace ${diff / 3_600_000} h"
            else -> {
                val ldt = Instant.fromEpochMilliseconds(millis)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                "${ldt.dayOfMonth.toString().padStart(2, '0')}/" +
                    "${ldt.monthNumber.toString().padStart(2, '0')} " +
                    "${ldt.hour.toString().padStart(2, '0')}:" +
                    ldt.minute.toString().padStart(2, '0')
            }
        }
    }.getOrElse { "" }
}
