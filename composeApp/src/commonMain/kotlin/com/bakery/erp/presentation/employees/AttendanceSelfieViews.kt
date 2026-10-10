package com.bakery.erp.presentation.employees

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.ATTENDANCE_SELFIE_RETENTION_DAYS
import com.bakery.erp.model.SelfieCheck
import com.bakery.erp.ui.ProductImage
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.ChipTone
import kotlin.time.Clock

private const val DAY_MS = 86_400_000L

/**
 * La selfie de una marca en el historial: miniatura que se abre en grande, o la razón por
 * la que no hay foto. Las marcas sin selfie (viejas o hechas a mano) no muestran nada.
 */
@Composable
internal fun AttendanceSelfieThumb(
    label: String,
    markedAt: Long,
    check: SelfieCheck,
    photoUrl: String,
    size: Dp = 32.dp
) {
    if (check == SelfieCheck.NONE || markedAt == 0L) return
    val expired = Clock.System.now().toEpochMilliseconds() - markedAt >
        ATTENDANCE_SELFIE_RETENTION_DAYS * DAY_MS
    when {
        check == SelfieCheck.SKIPPED -> BrandChip("Sin foto", ChipTone.ATTENTION)
        expired -> Text(
            "Foto vencida",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        photoUrl.isEmpty() -> BrandChip("Foto no subida", ChipTone.ATTENTION)
        else -> {
            var open by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { open = true }
            ) {
                ProductImage(imageUrl = photoUrl, contentDescription = "Selfie de $label", modifier = Modifier.size(size))
            }
            if (open) SelfieDialog(label, check, photoUrl, onDismiss = { open = false })
        }
    }
}

@Composable
private fun SelfieDialog(label: String, check: SelfieCheck, photoUrl: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        title = { Text("Selfie de $label") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProductImage(
                    imageUrl = photoUrl,
                    contentDescription = "Selfie de $label",
                    modifier = Modifier.size(240.dp).clip(CircleShape)
                )
                when (check) {
                    SelfieCheck.LIVE -> BrandChip("Parpadeó frente a la cámara", ChipTone.SUCCESS)
                    SelfieCheck.FACE -> BrandChip("Cara detectada, sin parpadeo", ChipTone.NEUTRAL)
                    SelfieCheck.PHOTO -> BrandChip("Cara sin verificar", ChipTone.ATTENTION)
                    else -> Unit
                }
                Text(
                    "La foto se borra sola a los $ATTENDANCE_SELFIE_RETENTION_DAYS días.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    )
}
