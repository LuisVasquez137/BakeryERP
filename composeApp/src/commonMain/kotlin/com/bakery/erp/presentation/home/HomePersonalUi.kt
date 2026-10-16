package com.bakery.erp.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bakery.erp.ui.BrandHeaderBrush
import com.bakery.erp.ui.ProductImage
import com.bakery.erp.ui.theme.BrandColors
import kotlinx.coroutines.delay
import kotlin.time.Clock

// ── Pendientes ───────────────────────────────────────────────────────────────

/**
 * "Pendientes para ti". [canOpen] dice si la persona puede entrar al módulo del botón:
 * sin permiso el pendiente se muestra igual (es información), pero sin botón.
 */
@Composable
fun PendingTasksSection(
    tasks: List<PendingTask>,
    canOpen: (String) -> Boolean,
    onOpen: (String) -> Unit
) {
    if (tasks.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Pendientes para ti",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = BrandColors.TextSlate
            )
            Box(
                Modifier.clip(CircleShape).background(BrandColors.Accent).padding(horizontal = 8.dp, vertical = 1.dp)
            ) {
                Text("${tasks.size}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = BrandColors.OnAccent)
            }
        }
        tasks.forEach { t -> PendingTaskCard(t, t.module?.takeIf(canOpen), onOpen) }
    }
}

private fun pendingLook(kind: PendingKind): Triple<ImageVector, Color, Color> = when (kind) {
    PendingKind.ATTENDANCE -> Triple(Icons.Default.Fingerprint, BrandColors.AccentIcon, BrandColors.AccentSoft)
    PendingKind.DISPATCH_ARRIVED -> Triple(Icons.Default.LocalShipping, BrandColors.AccentIcon, BrandColors.AccentSoft)
    PendingKind.DISPATCH_ON_THE_WAY -> Triple(Icons.Default.LocalShipping, BrandColors.Teal, BrandColors.TealSoft)
    PendingKind.OPEN_CASH -> Triple(Icons.Default.PointOfSale, BrandColors.Danger, BrandColors.DangerSoft)
    PendingKind.CUSTOMER_ORDERS -> Triple(Icons.AutoMirrored.Filled.Assignment, BrandColors.Primary, BrandColors.PrimaryContainer)
}

@Composable
private fun PendingTaskCard(task: PendingTask, module: String?, onOpen: (String) -> Unit) {
    val (icon, tint, bg) = pendingLook(task.kind)
    // Lo que urge (llegó, caja abierta, asistencia) lleva el botón en naranja; lo que solo
    // informa, en azul.
    val urgent = task.kind != PendingKind.DISPATCH_ON_THE_WAY && task.kind != PendingKind.CUSTOMER_ORDERS
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = BrandColors.TextSlate)
                Text(task.detail, style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
            }
            if (module != null && task.actionLabel != null) {
                Button(
                    onClick = { onOpen(module) },
                    colors = if (urgent) ButtonDefaults.buttonColors(containerColor = BrandColors.Accent, contentColor = BrandColors.OnAccent)
                             else ButtonDefaults.buttonColors(containerColor = BrandColors.Primary, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(task.actionLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Actualizado hace… ────────────────────────────────────────────────────────

/** "Actualizado hace 5 min", que se recalcula solo; tocarlo también actualiza. */
@Composable
fun UpdatedAgo(lastUpdatedAt: Long, refreshing: Boolean, onRefresh: () -> Unit) {
    if (lastUpdatedAt <= 0L) return
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(lastUpdatedAt) {
        while (true) {
            now = Clock.System.now().toEpochMilliseconds()
            delay(30_000)
        }
    }
    val minutes = ((now - lastUpdatedAt) / 60_000).coerceAtLeast(0)
    val text = when {
        refreshing -> "Actualizando…"
        minutes < 1 -> "Actualizado hace un momento"
        minutes < 60 -> "Actualizado hace $minutes min"
        else -> "Actualizado hace ${minutes / 60} h · desliza hacia abajo para actualizar"
    }
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !refreshing, onClick = onRefresh)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            Icons.Default.Refresh, "Actualizar",
            tint = if (minutes >= 60) BrandColors.AccentIcon else BrandColors.MutedSlate,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (minutes >= 60) BrandColors.AccentDeep else BrandColors.MutedSlate
        )
    }
}

// ── Cumpleaños ───────────────────────────────────────────────────────────────

@Composable
private fun PersonPhoto(name: String, photoUrl: String, size: Int, ring: Color = Color.White) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(BrandColors.Primary).border(2.dp, ring, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl.isNotBlank()) {
            ProductImage(imageUrl = photoUrl, contentDescription = name, modifier = Modifier.size(size.dp))
        } else {
            Text(
                (name.firstOrNull() ?: '?').uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = if (size >= 44) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge
            )
        }
    }
}

private fun firstName(name: String) = name.trim().split(" ").firstOrNull().orEmpty().ifBlank { name }

/** "María", "María y José", "María, José y Ana", "María, José y 3 más". */
private fun joinNames(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names[0]
    2 -> "${names[0]} y ${names[1]}"
    3 -> "${names[0]}, ${names[1]} y ${names[2]}"
    else -> "${names[0]}, ${names[1]} y ${names.size - 2} más"
}

/**
 * Tarjeta de cumpleaños de la semana. Sin cumpleaños esta semana pero sí en el mes, queda
 * una línea discreta para abrir la lista; sin ninguno, no ocupa espacio.
 */
@Composable
fun BirthdaysCard(birthdays: Birthdays?, onOpenMonth: () -> Unit, onOpenYear: () -> Unit) {
    if (birthdays == null) return
    val week = birthdays.thisWeek
    if (week.isEmpty()) {
        if (birthdays.allYear.isEmpty()) return
        val monthHasSome = birthdays.thisMonth.isNotEmpty()
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .clickable(onClick = if (monthHasSome) onOpenMonth else onOpenYear)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Cake, null, tint = BrandColors.AccentIcon, modifier = Modifier.size(18.dp))
            Text(
                if (monthHasSome) "Cumpleaños de ${birthdays.monthName} · ${birthdays.thisMonth.size}"
                else "Nadie cumple años en ${birthdays.monthName} · ver los del año",
                style = MaterialTheme.typography.labelLarge,
                color = BrandColors.Primary,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = BrandColors.Primary)
        }
        return
    }

    val todays = week.filter { it.daysFromToday == 0 }
    Surface(
        onClick = onOpenMonth,
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, BrandColors.AccentSoft),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier
                .background(Brush.linearGradient(listOf(BrandColors.AccentSoft, Color.White, BrandColors.PrimaryContainer.copy(alpha = 0.5f))))
        ) {
            Confetti(Modifier.matchParentSize(), density = 14)
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Fotos encimadas: se ve de un vistazo cuántos son y quiénes.
                Box(Modifier.width((48 + (week.take(3).size - 1) * 26).dp).height(48.dp)) {
                    week.take(3).forEachIndexed { i, p ->
                        Box(Modifier.offset(x = (i * 26).dp)) {
                            PersonPhoto(p.name, p.photoUrl, 48, ring = if (p.daysFromToday == 0) BrandColors.Accent else Color.White)
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (todays.isNotEmpty()) "¡Hoy cumple años ${joinNames(todays.map { firstName(it.name) })}!"
                        else "Esta semana cumplen años",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = BrandColors.AccentDeep
                    )
                    Text(
                        joinNames(week.map { firstName(it.name) }),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = BrandColors.TextSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        week.take(3).joinToString("  ·  ") { "${firstName(it.name)} ${birthdayWhen(it).lowercase()}" },
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandColors.MutedSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(BrandColors.Accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Cake, "Ver cumpleaños del mes", tint = BrandColors.OnAccent, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Puntitos de confeti con los colores de la marca, siempre en el mismo lugar (no parpadean). */
@Composable
private fun Confetti(modifier: Modifier, density: Int) {
    val colors = listOf(BrandColors.Accent, BrandColors.ChartSky, BrandColors.Teal, BrandColors.AccentIcon, BrandColors.Primary)
    Canvas(modifier) {
        var seed = 7
        fun next(): Float { seed = (seed * 1103515245 + 12345) and 0x7fffffff; return (seed % 1000) / 1000f }
        repeat(density) { i ->
            val c = colors[i % colors.size].copy(alpha = 0.35f)
            val x = size.width * (0.45f + next() * 0.55f)
            val y = size.height * next()
            val r = 2.dp.toPx() + next() * 2.5f.dp.toPx()
            if (i % 3 == 0) drawRect(c, topLeft = Offset(x, y), size = androidx.compose.ui.geometry.Size(r * 1.6f, r * 0.8f))
            else drawCircle(c, r, Offset(x, y))
        }
    }
}

/** Todos los cumpleañeros del mes, día por día. */
@Composable
fun BirthdaysMonthDialog(birthdays: Birthdays, onDismiss: () -> Unit, onOpenYear: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = BrandColors.Ivory,
            modifier = Modifier.padding(horizontal = 20.dp).widthIn(max = 480.dp).fillMaxWidth()
        ) {
            Column {
                Box(Modifier.fillMaxWidth().background(BrandHeaderBrush)) {
                    Confetti(Modifier.matchParentSize(), density = 22)
                    Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(BrandColors.Accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Cake, null, tint = BrandColors.OnAccent, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar", tint = Color.White) }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Cumpleaños de ${birthdays.monthName}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            when (birthdays.thisMonth.size) {
                                0 -> "Nadie cumple años este mes"
                                1 -> "1 persona del equipo"
                                else -> "${birthdays.thisMonth.size} personas del equipo"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
                LazyColumn(
                    Modifier.heightIn(max = 460.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(birthdays.thisMonth, key = { it.employeeId }) { p -> BirthdayRow(p) }
                    item {
                        TextButton(onClick = onOpenYear, modifier = Modifier.fillMaxWidth()) {
                            Text("Ver los cumpleaños de todo el año", fontWeight = FontWeight.SemiBold, color = BrandColors.Primary)
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = BrandColors.Primary)
                        }
                    }
                    if (birthdays.thisMonth.isEmpty()) {
                        item {
                            Text(
                                "Cuando las fichas de los colaboradores tengan fecha de nacimiento, aquí aparecerán.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandColors.MutedSlate,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BirthdayRow(p: BirthdayPerson) {
    val isToday = p.daysFromToday == 0
    val passed = p.daysFromToday < 0
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isToday) BrandColors.AccentSoft else Color.White,
        border = if (isToday) BorderStroke(1.5.dp, BrandColors.Accent) else null,
        shadowElevation = if (isToday) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // La hoja de calendario con el día.
            Column(
                Modifier.width(46.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (passed) BrandColors.ChartTrack else BrandColors.PrimaryContainer),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.fillMaxWidth().background(if (isToday) BrandColors.Accent else if (passed) BrandColors.ChartMuted else BrandColors.Primary)
                        .padding(vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        weekdayShort(p.date.dayOfWeek).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isToday) BrandColors.OnAccent else Color.White
                    )
                }
                Text(
                    "${p.date.dayOfMonth}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (passed) BrandColors.MutedSlate else BrandColors.Primary,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            PersonPhoto(p.name, p.photoUrl, 44, ring = if (isToday) BrandColors.Accent else Color.White)
            Column(Modifier.weight(1f)) {
                Text(
                    p.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (passed) BrandColors.MutedSlate else BrandColors.TextSlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (p.branchName.isNotBlank()) {
                    Text(p.branchName, style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate)
                }
            }
            // Lejos en el año no lleva etiqueta: "En 214 días" no le dice nada a nadie.
            val chip = when {
                isToday -> Triple("¡Hoy!", BrandColors.Accent, BrandColors.OnAccent)
                p.daysFromToday == 1 -> Triple("Mañana", BrandColors.PrimaryContainer, BrandColors.Primary)
                passed -> Triple("Ya pasó", BrandColors.ChartTrack, BrandColors.MutedSlate)
                p.daysFromToday <= 30 -> Triple("En ${p.daysFromToday} días", BrandColors.PrimaryContainer, BrandColors.Primary)
                else -> null
            }
            chip?.let { (label, bg, fg) ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }
    }
}
