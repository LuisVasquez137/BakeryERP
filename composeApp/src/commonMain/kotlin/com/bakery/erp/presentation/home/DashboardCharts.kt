package com.bakery.erp.presentation.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtMoney
import kotlin.math.abs
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Las gráficas del inicio.
 *
 * Espacio: en teléfono van en un carrusel de tarjetas del mismo alto (se ve una y se asoma
 * la siguiente), así el tablero ocupa lo de una tarjeta y no empuja los módulos hacia
 * abajo. En tablet caben todas a la vista, en cuadrícula.
 *
 * Animación: el Home se pinta sin esperarlas. Mientras llegan los datos hay una tarjeta
 * vacía del mismo alto (nada salta al llegar), y al llegar cada tarjeta se dibuja una vez:
 * las líneas avanzan, las donas giran, las barras crecen. Al regresar de otra pantalla no
 * se repite (ver [rememberEntryProgress]).
 */
private val CARD_HEIGHT = 248.dp

private val SLICE_COLORS = listOf(
    BrandColors.Primary, BrandColors.Accent, BrandColors.Teal, BrandColors.ChartSky, BrandColors.AccentDeep
)

private fun sliceColor(index: Int, label: String): Color =
    if (label == "Otras" || label == "Otros") BrandColors.ChartMuted else SLICE_COLORS[index % SLICE_COLORS.size]

@Composable
fun HomeDashboardSection(
    dashboard: HomeDashboard?,
    loading: Boolean,
    onSelectPeriod: (DashboardPeriod) -> Unit,
    /** Null si no tiene el reporte de ventas: el enlace no lleva a una pantalla vedada. */
    onOpenSalesReport: (() -> Unit)?,
    /** True mientras la persona desplaza el Home: el carrusel no avanza bajo su dedo. */
    isHomeScrolling: () -> Boolean = { false }
) {
    if (dashboard == null && !loading) return
    val played = rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(save = { it.toList() }, restore = { it.toMutableStateList() })
    ) { mutableStateListOf<String>() }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 600.dp
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (dashboard) {
                null -> DashboardLayout(
                    isHomeScrolling = isHomeScrolling,
                    wide = wide,
                    heroFullWidth = false,
                    cards = List(if (wide) 2 else 1) { { m: Modifier -> PlaceholderCard(m) } }
                )

                is HomeDashboard.Owner -> {
                    PeriodSelector(selected = dashboard.selected, onSelect = onSelectPeriod)
                    val data = dashboard.periods[dashboard.selected]
                    if (data == null) {
                        DashboardLayout(isHomeScrolling = isHomeScrolling, wide = wide, heroFullWidth = true, cards = List(if (wide) 3 else 1) { { m: Modifier -> PlaceholderCard(m) } })
                    } else {
                        val key = "owner_${data.period}"
                        DashboardLayout(
                            isHomeScrolling = isHomeScrolling,
                            wide = wide,
                            heroFullWidth = true,
                            cards = listOf(
                                { m -> OwnerTrendCard(data, dashboard.showMoney, onOpenSalesReport, rememberEntryProgress("${key}_trend", played), m) },
                                { m -> BranchShareCard(data, dashboard.showMoney, rememberEntryProgress("${key}_branches", played), m) },
                                { m -> PaymentsCard(data, rememberEntryProgress("${key}_payments", played), m) }
                            )
                        )
                    }
                }

                is HomeDashboard.Branch -> {
                    val d = dashboard.data
                    DashboardLayout(
                        isHomeScrolling = isHomeScrolling,
                        wide = wide,
                        heroFullWidth = false,
                        cards = listOf(
                            { m -> PaceRingCard(d, rememberEntryProgress("branch_pace", played), m) },
                            { m -> PeakHoursCard(d, rememberEntryProgress("branch_hours", played), m) },
                            { m -> TopProductsCard(d, rememberEntryProgress("branch_top", played), m) },
                            { m -> PlantOrderCard(d, rememberEntryProgress("branch_order", played), m) }
                        )
                    )
                }
            }
        }
    }
}

/**
 * 0→1 la primera vez que se pinta la tarjeta con [key]; 1 de una vez si ya se animó en
 * este Home (la lista sobrevive a navegar y volver). Cambiar de período cambia la llave,
 * así que el dueño ve dibujarse cada período nuevo.
 */
@Composable
private fun rememberEntryProgress(key: String, played: SnapshotStateList<String>): Float {
    val already = key in played
    val anim = remember(key) { Animatable(if (already) 1f else 0f) }
    LaunchedEffect(key) {
        if (key !in played) played.add(key)
        if (anim.value < 1f) anim.animateTo(1f, tween(durationMillis = 950, easing = FastOutSlowInEasing))
    }
    return anim.value
}

@Composable
private fun DashboardLayout(
    isHomeScrolling: () -> Boolean,
    wide: Boolean,
    heroFullWidth: Boolean,
    cards: List<@Composable (Modifier) -> Unit>
) {
    if (!wide) {
        DashboardPager(cards, isHomeScrolling)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val rest = if (heroFullWidth) {
            cards.first()(Modifier.fillMaxWidth().height(CARD_HEIGHT))
            cards.drop(1)
        } else cards
        rest.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { card -> card(Modifier.weight(1f).height(CARD_HEIGHT)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Cada cuánto avanza solo. Más lento que el banner de alertas (6 s) para no ir al mismo compás. */
private const val AUTOPLAY_MS = 8_000L
/** Después de tocarlo a mano, se queda quieto este rato para leer con calma. */
private const val TOUCH_PAUSE_MS = 20_000L

/**
 * Carrusel que avanza solo hacia la izquierda y se puede mover a mano en los dos sentidos.
 *
 * Es "infinito" (muchas vueltas de las mismas tarjetas, arrancando a la mitad) para que al
 * pasar de la última a la primera siga deslizándose a la izquierda en vez de regresar de
 * golpe hacia atrás. Al salir de la vista (scroll o cambio de pantalla) el efecto se cancela
 * solo porque la tarjeta deja de estar compuesta.
 */
@Composable
private fun DashboardPager(cards: List<@Composable (Modifier) -> Unit>, isHomeScrolling: () -> Boolean) {
    val count = cards.size
    val laps = if (count > 1) 1000 else 1
    val state = rememberPagerState(initialPage = if (count > 1) count * (laps / 2) else 0) { count * laps }
    var pausedUntil by remember { mutableLongStateOf(0L) }
    fun now() = Clock.System.now().toEpochMilliseconds()
    val dragged by state.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) { if (dragged) pausedUntil = now() + TOUCH_PAUSE_MS }
    LaunchedEffect(count) {
        if (count < 2) return@LaunchedEffect
        while (true) {
            delay(AUTOPLAY_MS)
            if (now() < pausedUntil || state.isScrollInProgress || isHomeScrolling()) continue
            state.animateScrollToPage(state.currentPage + 1, animationSpec = tween(700, easing = FastOutSlowInEasing))
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalPager(
            state = state,
            // La siguiente tarjeta se asoma: dice sin palabras que hay más deslizando.
            contentPadding = PaddingValues(end = if (count > 1) 28.dp else 0.dp),
            pageSpacing = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(CARD_HEIGHT)
                // Un toque sin arrastrar (tocar una dona, por ejemplo) también lo detiene.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        pausedUntil = now() + TOUCH_PAUSE_MS
                    }
                }
        ) { page -> cards[page % count](Modifier.fillMaxSize()) }
        if (count > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(count) { i ->
                    val selected = i == state.currentPage % count
                    val width by animateDpAsState(if (selected) 18.dp else 6.dp)
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(if (selected) BrandColors.Primary else BrandColors.ChartMuted)
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodSelector(selected: DashboardPeriod, onSelect: (DashboardPeriod) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrandColors.ChartTrack)
            .padding(3.dp)
    ) {
        DashboardPeriod.entries.forEach { p ->
            val isSel = p == selected
            Surface(
                onClick = { onSelect(p) },
                shape = RoundedCornerShape(9.dp),
                color = if (isSel) Color.White else Color.Transparent,
                shadowElevation = if (isSel) 1.dp else 0.dp,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    p.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSel) BrandColors.Primary else BrandColors.MutedSlate,
                    modifier = Modifier.padding(vertical = 7.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

// ── Contenedores ─────────────────────────────────────────────────────────────

/** Entrada de la tarjeta: aparece y sube unos dp mientras su gráfica se dibuja. */
private fun Modifier.entry(progress: Float): Modifier = graphicsLayer {
    val p = (progress * 2.5f).coerceIn(0f, 1f)
    alpha = p
    translationY = (1f - p) * 18.dp.toPx()
}

@Composable
private fun ChartCard(
    modifier: Modifier,
    progress: Float,
    background: Brush? = null,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.entry(progress),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = border,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (background != null) Modifier.background(background) else Modifier)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun CardTitle(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = BrandColors.TextSlate,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        trailing?.invoke()
    }
}

@Composable
private fun SmallChip(text: String, bg: Color, fg: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = fg,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun PlaceholderCard(modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 1.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(140.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(BrandColors.ChartTrack))
            Box(Modifier.width(90.dp).height(26.dp).clip(RoundedCornerShape(8.dp)).background(BrandColors.ChartTrack))
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(BrandColors.ChartTrack.copy(alpha = 0.6f)))
        }
    }
}

// ── Dueño ────────────────────────────────────────────────────────────────────

private fun periodTitle(p: DashboardPeriod) = when (p) {
    DashboardPeriod.TODAY -> "Ventas de hoy"
    DashboardPeriod.WEEK -> "Ventas · últimos 7 días"
    DashboardPeriod.MONTH -> "Ventas · últimos 30 días"
}

private fun periodCompare(p: DashboardPeriod) = when (p) {
    DashboardPeriod.TODAY -> "vs. el mismo día de la semana pasada"
    DashboardPeriod.WEEK -> "vs. semana pasada"
    DashboardPeriod.MONTH -> "vs. 30 días anteriores"
}

private fun deltaText(delta: Double): String =
    "${if (delta >= 0) "▲" else "▼"} ${abs(delta).fmt(1)} %"

@Composable
private fun OwnerTrendCard(
    data: OwnerPeriodData,
    showMoney: Boolean,
    onOpenSalesReport: (() -> Unit)?,
    progress: Float,
    modifier: Modifier
) {
    val hero = Brush.linearGradient(listOf(BrandColors.Primary, BrandColors.PrimaryDark, BrandColors.OnAccent))
    Surface(modifier = modifier.entry(progress), shape = RoundedCornerShape(20.dp), color = BrandColors.Primary, shadowElevation = 2.dp) {
        Column(
            Modifier
                .fillMaxSize()
                .background(hero)
                .drawBehind {
                    // Brillo naranja en la esquina: el croissant del logo, no decoración suelta.
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(BrandColors.Accent.copy(alpha = 0.32f), Color.Transparent),
                            center = Offset(size.width, 0f),
                            radius = size.minDimension * 0.75f
                        ),
                        radius = size.minDimension * 0.75f,
                        center = Offset(size.width, 0f)
                    )
                }
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    periodTitle(data.period),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f)
                )
                if (onOpenSalesReport != null) {
                    Text(
                        "Ver reporte",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenSalesReport)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            val delta = data.deltaPct
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (showMoney) "Q ${(data.total * progress.coerceIn(0f, 1f)).fmtMoney(0)}"
                           else delta?.let { deltaText(it) } ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                if (showMoney || delta == null) {
                    SmallChip(
                        text = delta?.let { "${deltaText(it)} ${periodCompare(data.period)}" } ?: "Sin datos para comparar",
                        bg = BrandColors.Accent,
                        fg = BrandColors.OnAccent
                    )
                } else {
                    Text(periodCompare(data.period), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                }
            }
            LineCompareChart(
                current = data.current,
                previous = data.previous,
                progress = progress,
                lineColor = Color.White,
                previousColor = Color.White.copy(alpha = 0.55f),
                fillColor = Color.White.copy(alpha = 0.30f),
                gridColor = Color.White.copy(alpha = 0.14f),
                labelColor = Color.White.copy(alpha = 0.8f),
                dotColor = BrandColors.Accent,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendLine("Actual", Color.White, dashed = false)
                LegendLine(if (data.period == DashboardPeriod.TODAY) "Semana pasada" else "Período anterior", Color.White.copy(alpha = 0.7f), dashed = true)
            }
        }
    }
}

@Composable
private fun LegendLine(label: String, color: Color, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(Modifier.width(14.dp).height(3.dp)) {
            drawLine(
                color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                strokeWidth = size.height, cap = StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4f, 4f)) else null
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun BranchShareCard(data: OwnerPeriodData, showMoney: Boolean, progress: Float, modifier: Modifier) {
    ChartCard(modifier, progress) {
        CardTitle("Ventas por sucursal") {
            Text(data.period.label, style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate)
        }
        if (data.branches.isEmpty()) {
            EmptyNote("Todavía no hay ventas en este período.")
            return@ChartCard
        }
        val total = data.branches.sumOf { it.value }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Donut(
                slices = data.branches,
                progress = progress,
                centerBig = "${data.branches.count { it.label != "Otras" }}",
                centerSmall = if (data.branches.size == 1) "sucursal" else "sucursales",
                modifier = Modifier.size(118.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                data.branches.forEachIndexed { i, s ->
                    LegendRow(
                        color = sliceColor(i, s.label),
                        name = s.label,
                        pct = if (total > 0) s.value / total * 100 else 0.0,
                        sub = if (showMoney) "Q ${s.value.fmtMoney(0)}" else null,
                        delta = if (data.period == DashboardPeriod.TODAY) null else s.deltaPct
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentsCard(data: OwnerPeriodData, progress: Float, modifier: Modifier) {
    ChartCard(modifier, progress) {
        CardTitle("Cómo te pagan") {
            Text(data.period.label, style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate)
        }
        if (data.payments.isEmpty()) {
            EmptyNote("Todavía no hay cobros en este período.")
            return@ChartCard
        }
        val total = data.payments.sumOf { it.value }
        val first = data.payments.first()
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Donut(
                slices = data.payments,
                progress = progress,
                centerBig = "${(first.value / total * 100).roundToInt()} %",
                centerSmall = first.label.lowercase(),
                modifier = Modifier.size(108.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                data.payments.forEachIndexed { i, s ->
                    LegendRow(sliceColor(i, s.label), s.label, s.value / total * 100, sub = null, delta = null)
                }
            }
        }
        // Pan contra restaurante: solo si de verdad hay los dos.
        if (data.channels.size >= 2) {
            val chTotal = data.channels.sumOf { it.value }
            Row(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(7.dp))) {
                data.channels.take(2).forEachIndexed { i, c ->
                    val frac = (c.value / chTotal).toFloat().coerceAtLeast(0.12f)
                    Box(
                        Modifier.weight(frac).fillMaxHeight()
                            .background(if (i == 0) BrandColors.Primary else BrandColors.TealSoft),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            "${c.label} ${(c.value / chTotal * 100).roundToInt()} %",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (i == 0) Color.White else BrandColors.Teal,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, name: String, pct: Double, sub: String?, delta: Double?) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = BrandColors.TextSlate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text("${pct.roundToInt()} %", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = BrandColors.TextSlate)
        }
        if (sub != null || delta != null) {
            Row(Modifier.padding(start = 16.dp)) {
                Text(sub.orEmpty(), style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate, modifier = Modifier.weight(1f))
                if (delta != null) {
                    Text(
                        deltaText(delta),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (delta >= 0) BrandColors.Success else BrandColors.Danger
                    )
                }
            }
        }
    }
}

// ── Sucursal ─────────────────────────────────────────────────────────────────

@Composable
private fun PaceRingCard(d: BranchToday, progress: Float, modifier: Modifier) {
    val ratio = d.paceRatio
    val ahead = ratio != null && ratio >= 1.0
    ChartCard(
        modifier, progress,
        background = Brush.linearGradient(listOf(BrandColors.AccentSoft, Color.White)),
        border = BorderStroke(1.dp, BrandColors.AccentSoft)
    ) {
        CardTitle("¿Cómo vamos hoy?") { SmallChip(d.weekdayName, BrandColors.AccentSoft, BrandColors.AccentDeep) }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 15.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(BrandColors.ChartTrack, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    val sweep = ((ratio ?: 0.0).coerceIn(0.0, 1.0) * 360f * progress).toFloat()
                    if (sweep > 0f) {
                        drawArc(
                            brush = if (ahead) Brush.sweepGradient(listOf(BrandColors.Success, BrandColors.Teal, BrandColors.Success))
                                    else Brush.sweepGradient(listOf(BrandColors.Accent, BrandColors.AccentIcon, BrandColors.Accent)),
                            startAngle = -90f, sweepAngle = sweep, useCenter = false,
                            topLeft = Offset(inset, inset), size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (ratio == null) "—" else "${(ratio * 100 * progress).roundToInt()} %",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = BrandColors.TextSlate
                    )
                    Text("vs. ${d.weekdayName} pasado", style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when {
                        ratio == null -> "Todavía no hay ventas del ${d.weekdayName} pasado para comparar."
                        ahead -> "¡Ya pasaron lo que llevaban el ${d.weekdayName} pasado a esta hora!"
                        else -> "Van al ${(ratio * 100).roundToInt()} % de lo que llevaban el ${d.weekdayName} pasado a esta hora."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandColors.TextSlate
                )
                d.remainingNormalShare?.takeIf { it > 0.005 }?.let { rest ->
                    Text(
                        "Lo normal es que de aquí al cierre se venda otro ${(rest * 100).roundToInt()} % del día.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandColors.MutedSlate
                    )
                }
            }
        }
    }
}

@Composable
private fun PeakHoursCard(d: BranchToday, progress: Float, modifier: Modifier) {
    ChartCard(modifier, progress) {
        CardTitle("Horas fuertes") {
            d.peakLabel?.let { SmallChip("Pico $it", BrandColors.AccentSoft, BrandColors.AccentDeep) }
        }
        if (d.normalShare.all { it <= 0.0 } && d.todayShare.all { it <= 0.0 }) {
            EmptyNote("Cuando haya unas semanas de ventas aquí se verán las horas en que más se vende.")
            return@ChartCard
        }
        val measurer = rememberTextMeasurer()
        val labelStyle = TextStyle(fontSize = 10.sp, color = BrandColors.MutedSlate)
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val bottom = size.height - 16.dp.toPx()
            val top = 4.dp.toPx()
            val n = d.hours.size
            if (n < 2) return@Canvas
            val maxV = (d.normalShare + d.todayShare).maxOrNull()?.takeIf { it > 0 } ?: 1.0
            fun x(i: Int) = size.width * i / (n - 1)
            fun y(v: Double) = (bottom - (v / maxV) * (bottom - top) * 0.92).toFloat()

            // Franja del pico
            if (d.peakLabel != null) {
                val best = (0 until n - 1).maxByOrNull { d.normalShare[it] + d.normalShare[it + 1] } ?: 0
                drawRoundRect(
                    BrandColors.AccentSoft,
                    topLeft = Offset(x(best) - 6.dp.toPx(), top),
                    size = Size(x(best + 1) - x(best) + 12.dp.toPx(), bottom - top),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                )
            }
            // Un día normal: área azul pálida
            val normalPts = d.normalShare.mapIndexed { i, v -> Offset(x(i), y(v)) }
            val area = smoothPath(normalPts).apply {
                lineTo(normalPts.last().x, bottom); lineTo(normalPts.first().x, bottom); close()
            }
            clipRect(right = size.width * progress) {
                drawPath(area, BrandColors.PrimaryContainer)
                drawPath(smoothPath(normalPts), BrandColors.ChartSky, style = Stroke(1.5.dp.toPx()))
            }
            // Hoy: línea naranja hasta ahora
            if (d.todayShare.size >= 2) {
                val todayPts = d.todayShare.mapIndexed { i, v -> Offset(x(i), y(v)) }
                clipRect(right = size.width * progress) {
                    drawPath(smoothPath(todayPts), BrandColors.Accent, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round))
                }
                val now = todayPts.last()
                drawLine(
                    BrandColors.TextSlate.copy(alpha = 0.4f), Offset(now.x, top), Offset(now.x, bottom),
                    strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
                if (progress > 0.95f) {
                    drawCircle(Color.White, 5.5.dp.toPx(), now)
                    drawCircle(BrandColors.Accent, 3.8.dp.toPx(), now)
                }
            }
            // Horas abajo: unas cuantas, no todas.
            val step = ((n - 1) / 4).coerceAtLeast(1)
            for (i in 0 until n step step) {
                val t = measurer.measure(hourLabel(d.hours[i]), labelStyle)
                val lx = (x(i) - t.size.width / 2f).coerceIn(0f, size.width - t.size.width)
                drawText(t, topLeft = Offset(lx, size.height - t.size.height))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendSwatch("Hoy", BrandColors.Accent, line = true)
            LegendSwatch("Un ${d.weekdayName} normal", BrandColors.PrimaryContainer, line = false)
        }
    }
}

@Composable
private fun LegendSwatch(label: String, color: Color, line: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.width(14.dp).height(if (line) 3.dp else 9.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate)
    }
}

@Composable
private fun TopProductsCard(d: BranchToday, progress: Float, modifier: Modifier) {
    ChartCard(modifier, progress) {
        CardTitle("Lo que más se vende hoy") {
            Text("unidades", style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate)
        }
        if (d.topProducts.isEmpty()) {
            EmptyNote("Todavía no hay ventas hoy.")
            return@ChartCard
        }
        val max = d.topProducts.maxOf { it.value }.takeIf { it > 0 } ?: 1.0
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            d.topProducts.forEachIndexed { i, s ->
                // Cada barra arranca un poco después de la anterior.
                val local = ((progress - i * 0.08f) / 0.68f).coerceIn(0f, 1f)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(22.dp).clip(RoundedCornerShape(7.dp))
                            .background(if (i == 0) BrandColors.Accent else BrandColors.PrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (i == 0) BrandColors.OnAccent else BrandColors.Primary
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row {
                            Text(
                                s.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = BrandColors.TextSlate,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(units(s.value), style = MaterialTheme.typography.labelMedium, color = BrandColors.MutedSlate)
                        }
                        Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(BrandColors.ChartTrack)) {
                            Box(
                                Modifier
                                    .fillMaxWidth((s.value / max).toFloat() * local)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (i == 0) Brush.horizontalGradient(listOf(BrandColors.AccentDeep, BrandColors.Accent))
                                        else Brush.horizontalGradient(listOf(BrandColors.Primary, BrandColors.ChartSky))
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlantOrderCard(d: BranchToday, progress: Float, modifier: Modifier) {
    ChartCard(modifier, progress) {
        CardTitle("Pedido a planta de hoy")
        val order = d.plantOrder
        if (order == null || order.requested <= 0.0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.LocalShipping, null, tint = BrandColors.ChartMuted, modifier = Modifier.size(22.dp))
                Text("Hoy no hay pedido a planta para esta sucursal.", style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
            }
        } else {
            val frac = (order.arrived / order.requested).coerceIn(0.0, 1.0)
            val color = when (order.stage) {
                PlantOrderStage.RECEIVED -> if (frac >= 0.999) BrandColors.Success else BrandColors.AccentDeep
                PlantOrderStage.ON_THE_WAY -> BrandColors.Teal
                PlantOrderStage.PACKING -> BrandColors.Primary
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (order.stage == PlantOrderStage.PACKING) "—" else "${(frac * 100 * progress).roundToInt()} %",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = color
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        when (order.stage) {
                            PlantOrderStage.RECEIVED -> "Recibido ${units(order.arrived)} de ${units(order.requested)} pedidos"
                            PlantOrderStage.ON_THE_WAY -> "En camino: ${units(order.arrived)} de ${units(order.requested)} empacados"
                            PlantOrderStage.PACKING -> "Pidieron ${units(order.requested)} · la planta está empacando"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = BrandColors.MutedSlate,
                        maxLines = 2
                    )
                    Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(5.dp)).background(BrandColors.ChartTrack)) {
                        Box(
                            Modifier.fillMaxWidth((frac * progress).toFloat()).fillMaxHeight()
                                .clip(RoundedCornerShape(5.dp)).background(color)
                        )
                    }
                }
            }
            order.shortages.take(2).forEach { s ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(BrandColors.DangerSoft)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(s.productName, style = MaterialTheme.typography.labelMedium, color = BrandColors.TextSlate,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${units(s.got)} de ${units(s.expected)}", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold, color = BrandColors.Danger)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        val co = d.customerOrders
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BrandColors.PrimaryContainer).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val next = co.next
            if (next == null) {
                Text("Sin pedidos de clientes para hoy ni mañana.", style = MaterialTheme.typography.labelMedium, color = BrandColors.Primary)
            } else {
                Text(
                    next.time,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(BrandColors.Primary).padding(horizontal = 8.dp, vertical = 5.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        "${next.title}${if (next.customer.isNotBlank()) " · ${next.customer}" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = BrandColors.TextSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Por entregar: ${co.today} hoy · ${co.tomorrow} mañana",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandColors.MutedSlate
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
    }
}

private fun units(v: Double): String = if (abs(v - v.roundToInt()) < 0.01) "${v.roundToInt()}" else v.fmt(1)

// ── Dibujo ───────────────────────────────────────────────────────────────────

/** Curva suave por los puntos (Catmull-Rom pasada a Bézier). */
private fun smoothPath(pts: List<Offset>): Path = Path().apply {
    if (pts.isEmpty()) return@apply
    moveTo(pts[0].x, pts[0].y)
    for (i in 0 until pts.size - 1) {
        val p0 = pts.getOrElse(i - 1) { pts[i] }
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val p3 = pts.getOrElse(i + 2) { p2 }
        cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y
        )
    }
}

@Composable
private fun Donut(slices: List<Slice>, progress: Float, centerBig: String, centerSmall: String, modifier: Modifier) {
    val total = slices.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(BrandColors.ChartTrack, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            // Gira hasta su lugar: todo el anillo arranca arriba y se abre.
            var start = -90f
            val gap = if (slices.size > 1) 2.5f else 0f
            slices.forEachIndexed { i, s ->
                val sweep = (s.value / total * 360f).toFloat()
                val drawn = ((sweep - gap) * progress).coerceAtLeast(0f)
                if (drawn > 0f) {
                    drawArc(sliceColor(i, s.label), start * progress + (-90f) * (1 - progress), drawn, false,
                        Offset(inset, inset), arcSize, style = Stroke(stroke))
                }
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerBig, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = BrandColors.TextSlate)
            Text(centerSmall, style = MaterialTheme.typography.labelSmall, color = BrandColors.MutedSlate, maxLines = 1)
        }
    }
}

@Composable
private fun LineCompareChart(
    current: List<ChartPoint>,
    previous: List<ChartPoint>,
    progress: Float,
    lineColor: Color,
    previousColor: Color,
    fillColor: Color,
    gridColor: Color,
    labelColor: Color,
    dotColor: Color,
    modifier: Modifier
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    Canvas(modifier) {
        val n = maxOf(current.size, previous.size)
        if (n < 2) return@Canvas
        val labelH = 14.dp.toPx()
        val top = 6.dp.toPx()
        val bottom = size.height - labelH - 2.dp.toPx()
        val all = (current + previous).map { it.value }
        val maxV = all.maxOrNull() ?: 0.0
        val minV = all.minOrNull() ?: 0.0
        // El piso no es cero: con cero las semanas parecidas se ven planas, que era justo
        // el problema de las barras. Se deja un margen bajo el mínimo para que la línea respire.
        val span = (maxV - minV).takeIf { it > 0 } ?: (maxV.takeIf { it > 0 } ?: 1.0)
        val lo = (minV - span * 0.35).coerceAtLeast(0.0)
        val hi = maxV + span * 0.12
        fun x(i: Int) = size.width * i / (n - 1)
        fun y(v: Double) = (bottom - ((v - lo) / (hi - lo)) * (bottom - top)).toFloat()

        for (k in 0..2) {
            val gy = top + (bottom - top) * k / 2f
            drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
        }
        val prevPts = previous.mapIndexed { i, p -> Offset(x(i), y(p.value)) }
        if (prevPts.size >= 2) {
            drawPath(smoothPath(prevPts), previousColor, style = Stroke(1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
        }
        val curPts = current.mapIndexed { i, p -> Offset(x(i), y(p.value)) }
        if (curPts.size >= 2) {
            val line = smoothPath(curPts)
            val area = smoothPath(curPts).apply { lineTo(curPts.last().x, bottom); lineTo(curPts.first().x, bottom); close() }
            clipRect(right = size.width * progress) {
                drawPath(area, Brush.verticalGradient(listOf(fillColor, Color.Transparent), startY = top, endY = bottom))
                drawPath(line, lineColor, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        if (curPts.isNotEmpty() && progress > 0.95f) {
            val last = curPts.last()
            drawCircle(Color.White, 5.5.dp.toPx(), last)
            drawCircle(dotColor, 3.8.dp.toPx(), last)
        }
        // Etiquetas: todas si son pocas, si no unas cinco repartidas (siempre la última).
        val idx = if (n <= 8) (0 until n).toList()
                  else (0..4).map { it * (n - 1) / 4 }.distinct()
        // Las etiquetas son las del período actual ("Hoy" al final); hoy por horas, la línea
        // actual llega solo hasta ahora y las horas que faltan salen de la otra.
        val labels = if (current.size == n) current else previous
        idx.forEach { i ->
            val text = labels.getOrNull(i)?.label ?: return@forEach
            val t = measurer.measure(text, labelStyle)
            val lx = (x(i) - t.size.width / 2f).coerceIn(0f, size.width - t.size.width)
            drawText(t, topLeft = Offset(lx, size.height - t.size.height))
        }
    }
}
